package li.selman.modulithdocsmarkdown;

import com.structurizr.Workspace;
import com.structurizr.export.mermaid.MermaidDiagramExporter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Tags;
import com.structurizr.view.ComponentView;
import com.structurizr.view.Shape;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Renders Mermaid component diagrams for an {@link ApplicationModules} instance, built from the exact same
 * Structurizr view model {@code Documenter} builds (software system -&gt; container -&gt; one {@link Component} per
 * module, {@code uses} edges from direct module dependencies), then exported with Structurizr's own
 * {@link MermaidDiagramExporter}. Diagram parity with Spring Modulith's PlantUML output holds by construction because
 * both are derived from the identical model, never from a PlantUML-to-Mermaid text conversion.
 */
public final class ModulithMermaid {

    private final ApplicationModules modules;
    private final Workspace workspace;
    private final Container container;
    private final Map<ApplicationModule, Component> components;

    public ModulithMermaid(ApplicationModules modules) {

        this.modules = modules;
        this.workspace = new Workspace("Modulith", "");

        workspace
                .getViews()
                .getConfiguration()
                .getStyles()
                .addElementStyle(Tags.COMPONENT)
                .shape(Shape.Component);

        var systemName = modules.getSystemName().orElse("Modules");
        var system = workspace.getModel().addSoftwareSystem(systemName, "");

        this.container = system.addContainer(systemName, "", "");
        this.components = modules.stream()
                .collect(Collectors.toMap(
                        Function.identity(), module -> container.addComponent(module.getDisplayName(), "", "Module")));

        modules.forEach(this::wireDependencies);
    }

    /**
     * A diagram of all modules and every direct inter-module dependency.
     */
    public String overviewDiagram() {

        var componentView = workspace.getViews().createComponentView(container, "overview", "");
        componentView.setTitle(modules.getSystemName().orElse("Modules"));

        modules.forEach(module -> componentView.add(componentFor(module)));

        return export(componentView);
    }

    /**
     * A diagram of the given module, its direct dependencies and its bootstrap (upstream) dependencies.
     */
    public String moduleDiagram(ApplicationModule module) {

        var componentView =
                workspace.getViews().createComponentView(container, module.getIdentifier() + "-diagram", "");
        componentView.setTitle(module.getDisplayName());

        Stream.concat(
                        Stream.of(module),
                        Stream.concat(
                                module.getDirectDependencies(modules).uniqueModules(),
                                module.getBootstrapDependencies(modules)))
                .distinct()
                .forEach(m -> componentView.add(componentFor(m)));

        return export(componentView);
    }

    private void wireDependencies(ApplicationModule module) {

        var source = componentFor(module);

        module.getDirectDependencies(modules)
                .uniqueModules()
                .map(this::componentFor)
                .forEach(target -> source.uses(target, "uses"));
    }

    @SuppressWarnings("NullAway") // components is populated for every module in the constructor
    private Component componentFor(ApplicationModule module) {
        return components.get(module);
    }

    private static String export(ComponentView view) {
        return new MermaidDiagramExporter().export(view).getDefinition();
    }
}
