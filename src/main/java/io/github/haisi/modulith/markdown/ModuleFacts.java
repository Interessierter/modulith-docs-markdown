package io.github.haisi.modulith.markdown;

import static java.util.stream.Collectors.toCollection;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.EventType;
import org.springframework.modulith.core.SpringBean;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * The single source of truth for "the facts" a module canvas documents, extracted exclusively from the public
 * {@code spring-modulith-core} model. Both {@link ModulithDocusaurusRenderer} and the semantic parity test consume
 * this same definition, so a mis-modelled row breaks in exactly one place.
 */
public record ModuleFacts(
		String identifier,
		String displayName,
		String basePackage,
		SortedSet<String> springBeans,
		SortedSet<String> beanReferences,
		SortedSet<String> aggregateRoots,
		SortedSet<String> valueTypes,
		SortedSet<String> publishedEvents,
		SortedSet<String> eventsListenedTo,
		SortedSet<String> configProperties) {

	public static ModuleFacts from(ApplicationModule module, ApplicationModules modules) {

		return new ModuleFacts(
				module.getIdentifier().toString(),
				module.getDisplayName(),
				module.getBasePackage().getName(),
				// Matches Documenter's default CanvasOptions (hideInternals = true): only API/exposed beans are listed.
				simpleNames(module.getSpringBeans().stream()
						.filter(SpringBean::isApiBean)
						.map(bean -> bean.toArchitecturallyEvidentType().getType())),
				module.getDirectDependencies(modules, DependencyType.USES_COMPONENT)
						.uniqueStream(ApplicationModuleDependency::getTargetType)
						.map(dependency -> dependency.getTargetType().getSimpleName())
						.collect(toCollection(TreeSet::new)),
				simpleNames(module.getAggregateRoots().stream()),
				simpleNames(module.getValueTypes().stream()),
				simpleNames(module.getPublishedEvents().stream()
						.map(EventType::getType)
						.filter(module::isExposed)),
				simpleNames(module.getEventsListenedTo(modules).stream()),
				ConfigMetadata.propertyNamesFor(module));
	}

	/**
	 * Tokens that MUST appear (as inline code) in any faithful rendering. Description is intentionally excluded — its
	 * source (package-info Javadoc) is docs-internal in Spring Modulith, see the README's known-gaps section.
	 */
	SortedSet<String> allTokens() {

		var tokens = new TreeSet<String>();

		tokens.add(basePackage);
		tokens.addAll(springBeans);
		tokens.addAll(beanReferences);
		tokens.addAll(aggregateRoots);
		tokens.addAll(valueTypes);
		tokens.addAll(publishedEvents);
		tokens.addAll(eventsListenedTo);
		tokens.addAll(configProperties);

		return tokens;
	}

	private static SortedSet<String> simpleNames(Stream<JavaClass> types) {
		return types.map(JavaClass::getSimpleName).collect(toCollection(TreeSet::new));
	}
}
