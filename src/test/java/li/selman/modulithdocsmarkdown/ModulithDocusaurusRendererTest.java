package li.selman.modulithdocsmarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.modulith.core.ApplicationModules;

class ModulithDocusaurusRendererTest {

    private final ApplicationModules modules = SampleModel.get().verify();

    @Test
    void defaultCanvasStyleRendersHeadedSections() {

        var order = modules.getModuleByName("order").orElseThrow();
        ModulithDocusaurusRenderer renderer = new ModulithDocusaurusRenderer(modules);
        var page = renderer.renderModulePage(order, 1);
        var indexPage = renderer.renderIndexPage();

        assertThat(page).contains("## Spring components").doesNotContain("<table>");
        assertThat(indexPage).contains("./" + renderer.generateModuleFileName(order));
    }

    @Test
    void htmlTableCanvasStyleRendersASingleTable() {

        var order = modules.getModuleByName("order").orElseThrow();
        var page = new ModulithDocusaurusRenderer(modules)
                .withCanvasStyle(ModulithDocusaurusRenderer.CanvasStyle.HTML_TABLE)
                .renderModulePage(order, 1);

        assertThat(page)
                .contains("<table>")
                .contains("</table>")
                .contains("<tr><th>Spring components</th><td>")
                .doesNotContain("## Spring components");
    }

    @Test
    void customMarkupEmitterControlsExtensionAndPreamble(@TempDir Path tmp) throws IOException {

        var mdx = new MarkupEmitter() {
            @Override
            public String fileExtension() {
                return "mdx";
            }

            @Override
            public String mermaid(String definition) {
                return MarkupEmitter.markdown().mermaid(definition);
            }

            @Override
            public String preamble() {
                return "import Diagram from '@site/src/components/Diagram';\n\n";
            }
        };

        new ModulithDocusaurusRenderer(modules).withMarkupEmitter(mdx).writeTo(tmp);

        var order = modules.getModuleByName("order").orElseThrow();
        var page = Files.readString(tmp.resolve(ModulithDocusaurusRenderer.slug(order) + ".mdx"));

        assertThat(page).contains("import Diagram from '@site/src/components/Diagram';");
        assertThat(Files.exists(tmp.resolve("index.mdx"))).isTrue();
    }

    @Test
    void configPropertyLineOmitsAbsentOrBlankOptionalParts() {

        var everythingPresent =
                new ConfigMetadata.ConfigProperty("app.x", "java.lang.String", "A description.", "com.Src", "v");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(everythingPresent))
                .isEqualTo("- `app.x` -- `java.lang.String`, default `v`. A description.");

        var nullType = new ConfigMetadata.ConfigProperty("app.x", null, "A description.", "com.Src", "v");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(nullType)).doesNotContain("--");

        var blankType = new ConfigMetadata.ConfigProperty("app.x", " ", "A description.", "com.Src", "v");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(blankType)).doesNotContain("--");

        var nullDefault =
                new ConfigMetadata.ConfigProperty("app.x", "java.lang.String", "A description.", "com.Src", null);
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(nullDefault)).doesNotContain("default");

        var blankDefault =
                new ConfigMetadata.ConfigProperty("app.x", "java.lang.String", "A description.", "com.Src", " ");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(blankDefault)).doesNotContain("default");

        var nullDescription = new ConfigMetadata.ConfigProperty("app.x", "java.lang.String", null, "com.Src", "v");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(nullDescription))
                .isEqualTo("- `app.x` -- `java.lang.String`, default `v`");

        var blankDescription = new ConfigMetadata.ConfigProperty("app.x", "java.lang.String", " ", "com.Src", "v");
        assertThat(ModulithDocusaurusRenderer.configPropertyLine(blankDescription))
                .isEqualTo("- `app.x` -- `java.lang.String`, default `v`");
    }
}
