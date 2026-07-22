package li.selman.modulithdocsmarkdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;

/**
 * Renders an {@link ApplicationModules} instance as Docusaurus-flavoured Markdown with Mermaid diagrams, carrying the
 * same semantic information as Spring Modulith's built-in {@code Documenter} (AsciiDoc + PlantUML). See
 * {@link ModuleFacts} for the canonical fact set every module page emits, and {@link ModulithMermaid} for how
 * diagrams are derived from the same model {@code Documenter} builds.
 * <p>
 * Two configuration seams exist by design, both defaulted to the simplest choice:
 * <ul>
 * <li>{@link MarkupEmitter} — plain {@code .md} by default; swap in an MDX-flavoured emitter without touching this
 * class.</li>
 * <li>{@link CanvasStyle} — headed sections with bullet lists by default; {@link CanvasStyle#HTML_TABLE} renders the
 * canvas as a single HTML {@code <table>}, a closer 1:1 mirror of the AsciiDoc canvas, at the cost of Markdown tables
 * not supporting block content natively.</li>
 * </ul>
 */
public final class ModulithDocusaurusRenderer {

    public enum CanvasStyle {
        SECTIONS,
        HTML_TABLE
    }

    private record CanvasRow(String heading, String content) {}

    private final ApplicationModules modules;
    private final ModulithMermaid mermaid;
    private final MarkupEmitter emitter;
    private final CanvasStyle canvasStyle;

    public ModulithDocusaurusRenderer(ApplicationModules modules) {
        this(modules, MarkupEmitter.markdown(), CanvasStyle.SECTIONS);
    }

    private ModulithDocusaurusRenderer(ApplicationModules modules, MarkupEmitter emitter, CanvasStyle canvasStyle) {

        this.modules = modules;
        this.mermaid = new ModulithMermaid(modules);
        this.emitter = emitter;
        this.canvasStyle = canvasStyle;
    }

    public ModulithDocusaurusRenderer withMarkupEmitter(MarkupEmitter emitter) {
        return new ModulithDocusaurusRenderer(modules, emitter, canvasStyle);
    }

    public ModulithDocusaurusRenderer withCanvasStyle(CanvasStyle canvasStyle) {
        return new ModulithDocusaurusRenderer(modules, emitter, canvasStyle);
    }

    /**
     * The Docusaurus route slug for a module: its identifier, lowercased, with every run of non-alphanumeric
     * characters collapsed to a single hyphen.
     */
    public static String slug(ApplicationModule module) {

        return module.getIdentifier()
                .toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /**
     * Writes one page per module plus an {@code index} page with the overview diagram, into the given directory.
     */
    public ModulithDocusaurusRenderer writeTo(Path directory) throws IOException {

        Files.createDirectories(directory);

        var sortedModules = modules.stream()
                .sorted(Comparator.comparing(ApplicationModule::getDisplayName))
                .toList();

        for (int i = 0; i < sortedModules.size(); i++) {

            var module = sortedModules.get(i);
            var page = renderModulePage(module, i + 1);

            Files.writeString(directory.resolve(slug(module) + "." + emitter.fileExtension()), page);
        }

        Files.writeString(directory.resolve("index." + emitter.fileExtension()), renderIndexPage());

        return this;
    }

    String renderModulePage(ApplicationModule module, int sidebarPosition) {

        var facts = ModuleFacts.from(module, modules);
        var displayName = module.getDisplayName();

        return frontMatter(slug(module), displayName, sidebarPosition)
                + emitter.preamble()
                + "# " + displayName + System.lineSeparator() + System.lineSeparator()
                + emitter.mermaid(mermaid.moduleDiagram(module)) + System.lineSeparator()
                + renderCanvas(buildRows(module, facts));
    }

    String renderIndexPage() {

        var systemName = modules.getSystemName().orElse("Modules");

        var links = modules.stream()
                .sorted(Comparator.comparing(ApplicationModule::getDisplayName))
                .map(module -> "- [%s](./%s)".formatted(module.getDisplayName(), slug(module)))
                .collect(Collectors.joining(System.lineSeparator()));

        return frontMatter("index", systemName, 0)
                + emitter.preamble()
                + "# " + systemName + System.lineSeparator() + System.lineSeparator()
                + emitter.mermaid(mermaid.overviewDiagram()) + System.lineSeparator()
                + "## Modules" + System.lineSeparator() + System.lineSeparator()
                + links + System.lineSeparator();
    }

    private static String frontMatter(String id, String title, int sidebarPosition) {

        return """
				---
				id: %s
				title: %s
				sidebar_label: %s
				sidebar_position: %d
				---
				""".formatted(id, title, title, sidebarPosition);
    }

    private List<CanvasRow> buildRows(ApplicationModule module, ModuleFacts facts) {

        return List.of(
                new CanvasRow(
                        "Description",
                        PackageDescriptions.forPackage(facts.basePackage()).orElse("_None_")),
                new CanvasRow("Base package", "`" + facts.basePackage() + "`"),
                new CanvasRow("Spring components", bulletList(facts.springBeans())),
                new CanvasRow("Bean references", beanReferences(module)),
                new CanvasRow("Aggregate roots", bulletList(facts.aggregateRoots())),
                new CanvasRow("Value types", bulletList(facts.valueTypes())),
                new CanvasRow("Published events", bulletList(facts.publishedEvents())),
                new CanvasRow("Events listened to", bulletList(facts.eventsListenedTo())),
                new CanvasRow("Configuration properties", configProperties(module)));
    }

    /**
     * The single swap point between rendering the canvas as headed sections (default) and as one HTML
     * {@code <table>} — a one-line {@link #withCanvasStyle(CanvasStyle)} configuration choice, not a rewrite.
     */
    private String renderCanvas(List<CanvasRow> rows) {

        return switch (canvasStyle) {
            case SECTIONS -> rows.stream().map(this::section).collect(Collectors.joining());
            case HTML_TABLE -> htmlTable(rows);
        };
    }

    private String section(CanvasRow row) {

        return "## " + row.heading() + System.lineSeparator() + System.lineSeparator() + row.content()
                + System.lineSeparator() + System.lineSeparator();
    }

    private String htmlTable(List<CanvasRow> rows) {

        var builder = new StringBuilder("<table>").append(System.lineSeparator());

        for (var row : rows) {

            builder.append("  <tr><th>")
                    .append(row.heading())
                    .append("</th><td>")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(row.content())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("</td></tr>")
                    .append(System.lineSeparator());
        }

        return builder.append("</table>").append(System.lineSeparator()).toString();
    }

    private String beanReferences(ApplicationModule module) {

        var lines = module.getDirectDependencies(modules, DependencyType.USES_COMPONENT)
                .uniqueStream(ApplicationModuleDependency::getTargetType)
                .map(dependency -> "- `%s` (in [%s](./%s))"
                        .formatted(
                                dependency.getTargetType().getSimpleName(),
                                dependency.getTargetModule().getDisplayName(),
                                slug(dependency.getTargetModule())))
                .collect(Collectors.joining(System.lineSeparator()));

        return lines.isBlank() ? "_None_" : lines;
    }

    private String configProperties(ApplicationModule module) {

        var lines = ConfigMetadata.propertiesFor(module).stream()
                .map(ModulithDocusaurusRenderer::configPropertyLine)
                .collect(Collectors.joining(System.lineSeparator()));

        return lines.isBlank() ? "_None_" : lines;
    }

    // Package-private (not private) so ModulithDocusaurusRendererTest can exercise the null/blank branches for
    // type, defaultValue and description directly, without needing five differently-shaped @ConfigurationProperties
    // classes in the sample fixture just to produce every combination.
    static String configPropertyLine(ConfigMetadata.ConfigProperty property) {

        var line = new StringBuilder("- `").append(property.name()).append('`');

        if (property.type() != null && !property.type().isBlank()) {
            line.append(" -- `").append(property.type()).append('`');
        }

        if (property.defaultValue() != null && !property.defaultValue().isBlank()) {
            line.append(", default `").append(property.defaultValue()).append('`');
        }

        if (property.description() != null && !property.description().isBlank()) {
            line.append(". ").append(property.description());
        }

        return line.toString();
    }

    private static String bulletList(Collection<String> items) {

        return items.isEmpty()
                ? "_None_"
                : items.stream().map(item -> "- `" + item + "`").collect(Collectors.joining(System.lineSeparator()));
    }
}
