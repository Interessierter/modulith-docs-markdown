package li.selman.modulithdocsmarkdown;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The deliverable that matters: runs both the real Spring Modulith {@link Documenter} and
 * {@link ModulithDocusaurusRenderer} over the identical {@link ApplicationModules} instance and asserts semantic
 * parity — same documented facts, same diagram topology — never a byte/structural diff, since the two tools emit
 * different markup (AsciiDoc/PlantUML vs Markdown/Mermaid) by design.
 * <p>
 * Token matching intentionally does not look for the bare {@code `SimpleName`} form. Spring Modulith's
 * {@code Asciidoctor} renders type names via {@code FormattableType#getAbbreviatedFullName(module)}, which — except
 * for types in the JDK's default package — <em>always</em> prefixes the simple name with an abbreviated package path
 * (e.g. {@code `i.g.h.m.m.s.o.OrderPlaced`}), so a literal {@code `` + token + ``} search against the oracle would
 * never match. {@link #containsInlineCode(String, String)} instead matches an inline-code span whose content *ends*
 * with the token, optionally preceded by a dotted prefix — still a real code-span match (no bare-substring false
 * positives), just tolerant of Spring Modulith's package abbreviation.
 */
class SemanticParityTest {

    private static final Set<String> KNOWN_GAPS = Set.of("Description");

    private static ApplicationModules modules;
    private static Path smDir;
    private static Path mdDir;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws IOException {

        modules = SampleModel.get().verify();

        smDir = tmp.resolve("sm");
        new Documenter(modules, Documenter.Options.defaults().withOutputFolder(smDir.toString()))
                .writeModuleCanvases()
                .writeModulesAsPlantUml();

        mdDir = tmp.resolve("md");
        new ModulithDocusaurusRenderer(modules).writeTo(mdDir);
    }

    @TestFactory
    Stream<DynamicTest> everyModuleHasSemanticParity() {

        return modules.stream()
                .map(module -> dynamicTest(module.getIdentifier().toString(), () -> {
                    var expected = ModuleFacts.from(module, modules);
                    var adoc = readCanvas(module);
                    var md = Files.readString(mdDir.resolve(ModulithDocusaurusRenderer.slug(module) + ".md"));

                    for (String token : expected.allTokens()) {

                        assertTrue(
                                containsInlineCode(adoc, token),
                                () -> "Oracle drift: Spring Modulith no longer documents " + token);
                        assertTrue(containsInlineCode(md, token), () -> "Renderer gap: new renderer omitted " + token);
                    }

                    var inAdoc = expected.allTokens().stream()
                            .filter(token -> containsInlineCode(adoc, token))
                            .collect(toSet());
                    var inMd = expected.allTokens().stream()
                            .filter(token -> containsInlineCode(md, token))
                            .collect(toSet());

                    assertEquals(inAdoc, inMd, "Renderers disagree on documented facts for " + module.getIdentifier());
                }));
    }

    @Test
    void coverageGuard_noSpringModulithRowIsSilentlyDropped() throws IOException {

        for (ApplicationModule module : modules) {

            var facts = ModuleFacts.from(module, modules);
            var md = Files.readString(mdDir.resolve(ModulithDocusaurusRenderer.slug(module) + ".md"));

            record Row(String heading, boolean nonEmptyInSpringModulith) {}

            var rows = java.util.List.of(
                    new Row("Base package", true),
                    new Row("Spring components", !facts.springBeans().isEmpty()),
                    new Row("Bean references", !facts.beanReferences().isEmpty()),
                    new Row("Aggregate roots", !facts.aggregateRoots().isEmpty()),
                    new Row("Value types", !facts.valueTypes().isEmpty()),
                    new Row("Published events", !facts.publishedEvents().isEmpty()),
                    new Row("Events listened to", !facts.eventsListenedTo().isEmpty()),
                    new Row(
                            "Configuration properties",
                            !facts.configProperties().isEmpty()));

            for (Row row : rows) {

                if (KNOWN_GAPS.contains(row.heading())) {
                    continue;
                }

                if (row.nonEmptyInSpringModulith()) {
                    assertThat(isNonEmptySection(md, row.heading()))
                            .as(
                                    "Module '%s': renderer dropped non-empty Spring Modulith row '%s'",
                                    module.getIdentifier(), row.heading())
                            .isTrue();
                }
            }
        }
    }

    @Test
    void overviewDiagramReflectsFullModelTopology() {

        var mermaid = new ModulithMermaid(modules).overviewDiagram();
        var graph = MermaidGraph.parse(mermaid);

        var modelEdges = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules)
                        .uniqueModules()
                        .map(target -> module.getDisplayName() + "->" + target.getDisplayName()))
                .collect(toSet());

        assertEquals(modelEdges, graph.edgesByLabel());

        for (ApplicationModule module : modules) {
            assertTrue(
                    mermaid.contains(module.getDisplayName()),
                    () -> "Overview diagram is missing node for " + module.getDisplayName());
        }
    }

    private static String readCanvas(ApplicationModule module) {

        try (var files = Files.list(smDir)) {

            var identifier = module.getIdentifier().toString();

            var canvas = files.filter(path -> path.getFileName().toString().endsWith(".adoc"))
                    .filter(path -> path.getFileName().toString().contains(identifier))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No canvas file found for module " + identifier));

            return Files.readString(canvas);

        } catch (IOException o_O) {
            throw new UncheckedIOException(o_O);
        }
    }

    private static boolean containsInlineCode(String text, String token) {
        return Pattern.compile("`(?:[\\w$]+\\.)*" + Pattern.quote(token) + "`")
                .matcher(text)
                .find();
    }

    private static boolean isNonEmptySection(String markdown, String heading) {

        var content = extractSection(markdown, heading);

        return content != null && !content.isBlank() && !content.equalsIgnoreCase("_none_");
    }

    private static @Nullable String extractSection(String markdown, String heading) {

        var marker = "## " + heading;

        @Var var start = markdown.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();

        var next = markdown.indexOf("## ", start);
        var section = next < 0 ? markdown.substring(start) : markdown.substring(start, next);

        return section.strip();
    }
}
