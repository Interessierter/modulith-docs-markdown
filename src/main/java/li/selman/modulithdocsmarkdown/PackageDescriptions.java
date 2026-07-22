package li.selman.modulithdocsmarkdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Best-effort {@code package-info.java} Javadoc reader.
 * <p>
 * Spring Modulith's own module description comes from a {@code DocumentationSource} that reads Javadoc captured at
 * compile time by the docs-internal {@code spring-modulith-apt} annotation processor — there is no public API for
 * it. Rather than depend on that internal wiring (or fake a description), this reads the {@code package-info.java}
 * source file directly, relative to the conventional Maven/Gradle source roots under the current working directory.
 * <p>
 * This works whenever the renderer runs from a project checkout (the normal case for a build-time doc generator) but
 * is a known limitation for packaged/jarred consumers where source is not on disk — see the README.
 */
final class PackageDescriptions {

    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");
    private static final Pattern JAVADOC_COMMENT = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);

    private PackageDescriptions() {}

    static Optional<String> forPackage(String packageName) {
        return forPackage(packageName, SOURCE_ROOTS);
    }

    /**
     * Overload taking the source roots explicitly, so {@code PackageDescriptionsTest} can point it at a
     * {@code @TempDir} instead of this process's actual working directory.
     */
    static Optional<String> forPackage(String packageName, List<String> sourceRoots) {

        var relativePath = packageName.replace('.', '/') + "/package-info.java";

        return sourceRoots.stream()
                .map(root -> Path.of(root, relativePath))
                .filter(Files::exists)
                .findFirst()
                .flatMap(PackageDescriptions::readJavadoc);
    }

    private static Optional<String> readJavadoc(Path packageInfo) {

        String source;

        try {
            source = Files.readString(packageInfo);
        } catch (IOException o_O) {
            return Optional.empty();
        }

        var matcher = JAVADOC_COMMENT.matcher(source);

        if (!matcher.find()) {
            return Optional.empty();
        }

        var description = matcher.group(1)
                .lines()
                .map(String::strip)
                .map(line -> line.startsWith("*") ? line.substring(1).strip() : line)
                .filter(line -> !line.startsWith("@"))
                .collect(java.util.stream.Collectors.joining(" "))
                .strip();

        return description.isBlank() ? Optional.empty() : Optional.of(description);
    }
}
