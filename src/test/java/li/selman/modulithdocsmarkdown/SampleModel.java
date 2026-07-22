package li.selman.modulithdocsmarkdown;

import com.tngtech.archunit.core.importer.ImportOption;
import li.selman.modulithdocsmarkdown.sample.SampleApplication;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The {@link SampleApplication} fixture lives under {@code src/test/java}, so it compiles to
 * {@code target/test-classes}, which ArchUnit's default {@code DoNotIncludeTests} import option excludes. Every test
 * needs {@link ImportOption.Predefined#ONLY_INCLUDE_TESTS} instead of the (verified, but silently empty) zero-arg
 * {@code ApplicationModules.of(Class)}. {@code ApplicationModules.of(...)} caches by its argument tuple, so repeated
 * calls across test classes are cheap.
 */
final class SampleModel {

    private SampleModel() {}

    static ApplicationModules get() {
        return ApplicationModules.of(SampleApplication.class, ImportOption.Predefined.ONLY_INCLUDE_TESTS);
    }
}
