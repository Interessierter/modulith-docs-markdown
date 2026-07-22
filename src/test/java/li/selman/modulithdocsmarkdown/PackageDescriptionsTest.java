package li.selman.modulithdocsmarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageDescriptionsTest {

    @Test
    void extractsASingleLineJavadocDescription(@TempDir Path tmp) throws IOException {

        writePackageInfo(tmp, "com.example", "/** Handles widgets. */\npackage com.example;\n");

        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp))).contains("Handles widgets.");
    }

    @Test
    void joinsAMultiLineJavadocDescriptionAndDropsTags(@TempDir Path tmp) throws IOException {

        writePackageInfo(tmp, "com.example", """
				/**
				 * Handles widgets.
				 * Also gadgets.
				 *
				 * @author Someone
				 * @since 1.0
				 */
				package com.example;
				""");

        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp)))
                .contains("Handles widgets. Also gadgets.");
    }

    @Test
    void returnsEmptyWhenNoPackageInfoExistsInAnyRoot(@TempDir Path tmp) {
        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp))).isEmpty();
    }

    @Test
    void returnsEmptyWhenPackageInfoHasNoJavadocComment(@TempDir Path tmp) throws IOException {

        writePackageInfo(tmp, "com.example", "package com.example;\n");

        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp))).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheJavadocCommentIsOnlyTags(@TempDir Path tmp) throws IOException {

        writePackageInfo(tmp, "com.example", "/**\n * @author Someone\n */\npackage com.example;\n");

        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp))).isEmpty();
    }

    @Test
    void returnsEmptyWhenPackageInfoCannotBeRead(@TempDir Path tmp) throws IOException {

        // A directory named package-info.java exists (so Files::exists matches) but reading it as text fails.
        var packageInfo = tmp.resolve("src/main/java/com/example/package-info.java");
        Files.createDirectories(packageInfo);

        assertThat(PackageDescriptions.forPackage("com.example", roots(tmp))).isEmpty();
    }

    private static List<String> roots(Path tmp) {
        return List.of(
                tmp.resolve("src/main/java").toString(),
                tmp.resolve("src/test/java").toString());
    }

    private static void writePackageInfo(Path tmp, String packageName, String content) throws IOException {

        var path = tmp.resolve("src/main/java")
                .resolve(packageName.replace('.', '/'))
                .resolve("package-info.java");

        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
