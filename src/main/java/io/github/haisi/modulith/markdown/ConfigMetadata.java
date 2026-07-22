package io.github.haisi.modulith.markdown;

import static java.util.stream.Collectors.toCollection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.modulith.core.ApplicationModule;

/**
 * Reproduces Spring Modulith's {@code ConfigurationProperties} logic: reads every
 * {@code META-INF/spring-configuration-metadata.json} on the classpath (as produced by the Spring Boot
 * configuration-metadata annotation processor) and keeps the properties whose declaring type belongs to a given
 * module. Depends only on the JDK — no JSON library is part of the main artifact's dependency set.
 */
final class ConfigMetadata {

	record ConfigProperty(String name, String type, String description, String sourceType, String defaultValue) {}

	private static final String METADATA_RESOURCE = "META-INF/spring-configuration-metadata.json";
	private static final List<ConfigProperty> PROPERTIES = load();

	private ConfigMetadata() {}

	static SortedSet<String> propertyNamesFor(ApplicationModule module) {

		return PROPERTIES.stream()
				.filter(property -> module.getType(property.sourceType()).isPresent())
				.map(ConfigProperty::name)
				.collect(toCollection(TreeSet::new));
	}

	static List<ConfigProperty> propertiesFor(ApplicationModule module) {

		return PROPERTIES.stream()
				.filter(property -> module.getType(property.sourceType()).isPresent())
				.toList();
	}

	private static List<ConfigProperty> load() {

		try {

			var resources = Collections.list(
					Thread.currentThread().getContextClassLoader().getResources(METADATA_RESOURCE));

			var result = new ArrayList<ConfigProperty>();

			for (URL resource : resources) {
				result.addAll(parse(resource));
			}

			return result;

		} catch (IOException o_O) {
			throw new UncheckedIOException(o_O);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<ConfigProperty> parse(URL resource) throws IOException {

		String content;

		try (var in = resource.openStream()) {
			content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		var root = (Map<String, Object>) Json.parse(content);
		var properties = (List<Object>) root.getOrDefault("properties", List.of());

		var result = new ArrayList<ConfigProperty>();

		for (Object raw : properties) {

			var property = (Map<String, Object>) raw;
			var sourceType = asString(property.get("sourceType"));

			if (sourceType == null || sourceType.isBlank()) {
				continue;
			}

			result.add(new ConfigProperty(
					Objects.requireNonNull(asString(property.get("name")), "name"),
					asString(property.get("type")),
					asString(property.get("description")),
					sourceType,
					asString(property.get("defaultValue"))));
		}

		return result;
	}

	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}

	/**
	 * Minimal recursive-descent JSON reader. Only supports what {@code spring-configuration-metadata.json} actually
	 * contains: objects, arrays, strings (with standard escapes), numbers, booleans and {@code null}.
	 */
	private static final class Json {

		private final String source;
		private int index;

		private Json(String source) {
			this.source = source;
		}

		static Object parse(String source) {

			var parser = new Json(source);
			parser.skipWhitespace();

			var value = parser.readValue();

			parser.skipWhitespace();

			return value;
		}

		private Object readValue() {

			return switch (peek()) {
				case '{' -> readObject();
				case '[' -> readArray();
				case '"' -> readString();
				case 't', 'f' -> readBoolean();
				case 'n' -> readNull();
				default -> readNumber();
			};
		}

		private Map<String, Object> readObject() {

			var result = new java.util.LinkedHashMap<String, Object>();

			expect('{');
			skipWhitespace();

			if (peek() == '}') {
				index++;
				return result;
			}

			while (true) {

				skipWhitespace();
				var key = readString();
				skipWhitespace();
				expect(':');
				skipWhitespace();
				result.put(key, readValue());
				skipWhitespace();

				if (peek() == ',') {
					index++;
					continue;
				}

				expect('}');
				break;
			}

			return result;
		}

		private List<Object> readArray() {

			var result = new ArrayList<Object>();

			expect('[');
			skipWhitespace();

			if (peek() == ']') {
				index++;
				return result;
			}

			while (true) {

				skipWhitespace();
				result.add(readValue());
				skipWhitespace();

				if (peek() == ',') {
					index++;
					continue;
				}

				expect(']');
				break;
			}

			return result;
		}

		private String readString() {

			expect('"');

			var builder = new StringBuilder();

			while (peek() != '"') {

				char c = source.charAt(index++);

				if (c == '\\') {

					char escaped = source.charAt(index++);

					builder.append(switch (escaped) {
						case '"' -> '"';
						case '\\' -> '\\';
						case '/' -> '/';
						case 'b' -> '\b';
						case 'f' -> '\f';
						case 'n' -> '\n';
						case 'r' -> '\r';
						case 't' -> '\t';
						case 'u' -> (char) Integer.parseInt(source.substring(index, index += 4), 16);
						default -> throw new IllegalStateException("Unsupported escape sequence \\" + escaped);
					});

				} else {
					builder.append(c);
				}
			}

			index++;

			return builder.toString();
		}

		private Boolean readBoolean() {

			if (source.startsWith("true", index)) {
				index += 4;
				return Boolean.TRUE;
			}

			if (source.startsWith("false", index)) {
				index += 5;
				return Boolean.FALSE;
			}

			throw new IllegalStateException("Invalid literal at " + index);
		}

		private Object readNull() {

			if (source.startsWith("null", index)) {
				index += 4;
				return null;
			}

			throw new IllegalStateException("Invalid literal at " + index);
		}

		private Number readNumber() {

			int start = index;

			while (index < source.length() && "-+.eE0123456789".indexOf(source.charAt(index)) >= 0) {
				index++;
			}

			var literal = source.substring(start, index);

			// Not a ternary: `cond ? Double.parseDouble(x) : Long.parseLong(x)` would binary-numeric-promote the
			// long branch to double (JLS 15.25), silently turning e.g. 10L into 10.0d.
			if (literal.contains(".") || literal.contains("e") || literal.contains("E")) {
				return Double.parseDouble(literal);
			}

			return Long.parseLong(literal);
		}

		private void expect(char expected) {

			if (source.charAt(index) != expected) {
				throw new IllegalStateException("Expected '%s' at %d but found '%s'".formatted(expected, index, peek()));
			}

			index++;
		}

		private char peek() {
			return source.charAt(index);
		}

		private void skipWhitespace() {

			while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
				index++;
			}
		}
	}
}
