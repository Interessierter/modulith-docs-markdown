package li.selman.modulithdocsmarkdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code ConfigMetadata}'s hand-rolled JSON reader directly via the package-private
 * {@code parseProperties} seam. Real {@code spring-configuration-metadata.json} content (as generated for
 * {@code SampleApplication} in {@code ModuleFactsTest}) only ever exercises a handful of these branches — booleans,
 * decimals, escaped characters, explicit {@code null}s and malformed input are all deliberately crafted here rather
 * than depended on to occur naturally in generated metadata.
 */
class ConfigMetadataTest {

    @Test
    void parsesEveryJsonValueTypeAndSkipsPropertiesWithoutASourceType() {

        var content = """
				{
				  "decorative": [true, false, null, 42, 3.14, {}, [], "esc: \\" \\\\ \\/ \\b \\f \\n \\r \\t \\u0041"],
				  "properties": [
				    {
				      "name": "app.complete",
				      "type": "java.lang.String",
				      "description": "A slash / and a \\"quoted\\" word.",
				      "sourceType": "com.example.Props",
				      "defaultValue": "value"
				    },
				    {
				      "name": "app.missing-source-type",
				      "type": "java.lang.String"
				    },
				    {
				      "name": "app.blank-source-type",
				      "type": "java.lang.String",
				      "sourceType": ""
				    }
				  ]
				}
				""";

        var properties = ConfigMetadata.parseProperties(content);

        assertThat(properties).hasSize(1);
        var property = properties.get(0);
        assertThat(property.name()).isEqualTo("app.complete");
        assertThat(property.sourceType()).isEqualTo("com.example.Props");
        assertThat(property.description()).isEqualTo("A slash / and a \"quoted\" word.");
        assertThat(property.defaultValue()).isEqualTo("value");
    }

    @Test
    void parsesALoneIntegerRunningToEndOfInput() {
        assertThat(ConfigMetadata.parseJson("42")).isEqualTo(42L);
    }

    @Test
    void parsesLowercaseScientificNotationAsADouble() {
        assertThat(ConfigMetadata.parseJson("1e10")).isEqualTo(1e10);
    }

    @Test
    void parsesUppercaseScientificNotationAsADouble() {
        assertThat(ConfigMetadata.parseJson("1E10")).isEqualTo(1E10);
    }

    @Test
    void tolerantOfAnEmptyPropertiesArray() {
        assertThat(ConfigMetadata.parseProperties("{\"properties\": []}")).isEmpty();
    }

    @Test
    void tolerantOfAMissingPropertiesKey() {
        assertThat(ConfigMetadata.parseProperties("{}")).isEmpty();
    }

    @Test
    void rejectsAnArrayMissingItsCommaSeparator() {
        assertThatThrownBy(() -> ConfigMetadata.parseProperties("[1 2]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected");
    }

    @Test
    void rejectsAnObjectMissingItsCommaSeparator() {
        assertThatThrownBy(() -> ConfigMetadata.parseProperties("{\"a\":1 \"b\":2}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected");
    }

    @Test
    void rejectsAnUnsupportedEscapeSequence() {
        assertThatThrownBy(() -> ConfigMetadata.parseProperties("[\"bad \\q escape\"]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported escape sequence");
    }

    @Test
    void rejectsAMalformedBooleanLiteral() {
        assertThatThrownBy(() -> ConfigMetadata.parseProperties("[truthy]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid literal");
    }

    @Test
    void rejectsAMalformedNullLiteral() {
        assertThatThrownBy(() -> ConfigMetadata.parseProperties("[nil]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid literal");
    }

    @Test
    void loadWrapsAnIOExceptionFromTheClassLoaderInAnUncheckedIOException() {

        var original = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(new ClassLoader(original) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                throw new IOException("boom");
            }
        });

        try {
            assertThatThrownBy(ConfigMetadata::load).isInstanceOf(UncheckedIOException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void loadReturnsAnEmptyListWhenNoMetadataResourceIsOnTheClasspath() {

        var original = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(new ClassLoader(original) {
            @Override
            public Enumeration<URL> getResources(String name) {
                return Collections.emptyEnumeration();
            }
        });

        try {
            assertThat(ConfigMetadata.load()).isEmpty();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
