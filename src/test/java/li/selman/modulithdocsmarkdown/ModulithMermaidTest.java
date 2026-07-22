package li.selman.modulithdocsmarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModulithMermaidTest {

    private final ApplicationModules modules = SampleModel.get().verify();

    @Test
    void overviewDiagramNodeSetMatchesAllModuleDisplayNames() {

        var mermaid = new ModulithMermaid(modules).overviewDiagram();
        var displayNames =
                modules.stream().map(ApplicationModule::getDisplayName).toList();

        assertThat(displayNames).allSatisfy(name -> assertThat(mermaid).contains(name));
    }

    @Test
    void overviewDiagramEdgeSetMatchesDirectDependencyPairs() {

        var mermaid = new ModulithMermaid(modules).overviewDiagram();

        var expectedEdges = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules)
                        .uniqueModules()
                        .map(target -> module.getDisplayName() + "->" + target.getDisplayName()))
                .collect(Collectors.toSet());

        var actualEdges = MermaidGraph.parse(mermaid).edgesByLabel();

        assertThat(actualEdges).isEqualTo(expectedEdges);
    }

    @Test
    void moduleDiagramContainsModuleAndItsDependencies() {

        var order = modules.getModuleByName("order").orElseThrow();
        var mermaid = new ModulithMermaid(modules).moduleDiagram(order);

        assertThat(mermaid).contains("Order").contains("Inventory");
    }

    @Test
    void moduleDiagramOfDependencyFreeModuleHasNoEdges() {

        var inventory = modules.getModuleByName("inventory").orElseThrow();
        var mermaid = new ModulithMermaid(modules).moduleDiagram(inventory);

        assertThat(MermaidGraph.parse(mermaid).edgesByLabel()).isEmpty();
    }

    @Test
    void overviewDiagramHasExactlyOneEdgeForTheSampleApp() {

        var mermaid = new ModulithMermaid(modules).overviewDiagram();

        assertThat(MermaidGraph.parse(mermaid).edgesByLabel()).isEqualTo(Set.of("Order->Inventory"));
    }
}
