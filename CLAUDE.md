# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`modulith-docs-markdown` (`li.selman:modulith-docs-markdown`) documents a Spring Modulith application as
Docusaurus-flavoured Markdown with Mermaid diagrams, carrying the same semantic information as Spring
Modulith's built-in `Documenter` (AsciiDoc + PlantUML). It is a parallel renderer built over the *public*
`spring-modulith-core` model — it does not fork or reach into `Documenter`'s internals.

The single most important file is `src/test/java/.../SemanticParityTest.java`: it runs both the real
`Documenter` and this library's renderer over the same `ApplicationModules` instance (built from the
two-module sample fixture under `src/test/java/.../sample`) and asserts they document the same facts and the
same diagram topology. `ModuleFacts.java` is the single, public-model-only definition of "the facts" both the
renderer and the test consume — a mis-modelled row breaks in exactly one place. See the README's "How parity
is verified" section for the full design rationale, and its "Known gaps" section for the one deliberately
scoped exception (package-info Javadoc descriptions).

## Commands

```shell
./mvnw verify                 # full build: compile, test, 100% coverage gate, format/style/nullness checks
./mvnw verify -Dquick          # fast loop: compile + test only, skips every quality gate below
./mvnw spotless:apply          # auto-format Java (palantir-java-format) and sort pom.xml - run before committing
./mvnw test -Dtest=SemanticParityTest   # just the parity test
```

`verify` runs, in order: compile (Error Prone + NullAway), tests, JaCoCo report, package/javadoc/sources jars,
Spotless check, Checkstyle check, JaCoCo coverage check. All of these gate the build - a green `verify` means
all of them passed, not just tests.

All plugins other than the compiler and Surefire (tests) live in the `qa` profile, which is active by
default and only deactivates when the `quick` system property is set (`-Dquick`, any value). With `qa`
inactive, Maven falls back to a bare `javac` compile via the default lifecycle bindings (still respecting
`maven.compiler.release`) - no Error Prone/NullAway, no `-Werror`, no Spotless/Checkstyle, no JaCoCo, no
javadoc/sources jars. Useful while hacking locally; don't rely on a `-Dquick` build for anything you intend
to commit or release - always run a plain `./mvnw verify` before that.

### Releasing

```shell
./bumpPomVersion.sh   # interactive: pick major/minor/patch/free-text, sets version, offers to commit
./release.sh          # pulls, checks current version > latest GitHub release, tags and pushes vX.Y.Z
```

Pushing a `vX.Y.Z` tag triggers `.github/workflows/release.yml`, which sets the version from the tag, builds,
stages artifacts to a local repo, and runs JReleaser (`jreleaser.yml`) to sign and deploy to Maven Central via
the Central Portal, and to create the GitHub Release with a generated changelog.

`dryrun-release.sh` exercises the JReleaser signing/deploy path locally against real GPG keys (`public.asc`/
`private.asc`, gitignored) and Central Portal credentials (`.env`, gitignored) - use it to debug release
config changes without waiting on CI.

## Commit conventions

Commit messages must follow [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/#specification)
(`<type>[optional scope]: <description>`, e.g. `fix:`, `feat:`, `build:`, `docs:`, `chore:`; append `!` or a
`BREAKING CHANGE:` footer for breaking changes).

## Build enforcement to know about

- **JaCoCo coverage is enforced at 100% (line and branch) for `src/main`**, not just measured. `verify` fails
  below that. `**/sample/**` (the ArchUnit test fixture) is excluded from the coverage rule — it's a Spring
  Modulith app verified structurally by `ApplicationModules.verify()` and the parity test, not meant to be
  unit-tested line-by-line, and deliberately contains code paths (e.g. a repository interface method,
  `InventoryLookup.isAvailable` always returning `true`) that are never exercised.
- **Error Prone's `-Xep:Var:ERROR`** requires every reassigned local variable to carry
  `@com.google.errorprone.annotations.Var`. This is enabled project-wide via `maven-compiler-plugin`.
- **NullAway** is configured with `AnnotatedPackages=li.selman.modulithdocsmarkdown` - it statically checks
  null-safety for this package specifically.
- **`.mvn/jvm.config`** carries `--add-exports`/`--add-opens` flags that Error Prone needs to hook into javac
  internals on JDK 16+. Without this file the build fails with an unhelpful error - don't remove it.
- **Checkstyle's `IllegalImport` rule** blocks non-jspecify `@Nullable` imports (Spring's, JetBrains', etc.),
  steering toward `org.jspecify.annotations.Nullable`.
- **The JDK used to build this project doesn't auto-discover annotation processors from the classpath** —
  `spring-boot-configuration-processor` (needed to generate the sample fixture's
  `spring-configuration-metadata.json`) must be listed explicitly in `annotationProcessorPaths`, alongside
  Error Prone and NullAway.
- Java baseline is 25 (`maven.compiler.release`). Since this is a compile-scope dependency (unlike a
  test-scope-only library), bumping it raises the minimum JDK for every consumer's build, not just this one.

## Domain notes specific to this project

- **`ApplicationModules.of(SampleApplication.class)` (zero/one-arg form) sees zero classes.** The fixture
  lives under `src/test/java`, compiling to `target/test-classes`, which ArchUnit's default
  `DoNotIncludeTests` import option excludes by path pattern. Every test uses
  `ApplicationModules.of(SampleApplication.class, ImportOption.Predefined.ONLY_INCLUDE_TESTS)` instead — see
  `SampleModel.java`.
- **The sample fixture's two modules form a DAG, not a cycle.** `order` depends on `inventory` for both a
  bean reference and an event subscription — same direction both times. Spring Modulith's `verify()` runs a
  slice-cycle check that treats any inter-module edge as a graph edge regardless of `DependencyType`, so a
  bidirectional dependency (even split sync-call one way, async-event the other) is a real cycle violation.
- **Token matching in `SemanticParityTest` is not a bare `` `SimpleName` `` substring search.** Spring
  Modulith's `Asciidoctor` renders type names via `FormattableType#getAbbreviatedFullName(module)`, which
  prefixes an abbreviated package path outside the default package. `containsInlineCode` matches an
  inline-code span whose content *ends* with the token instead — read the Javadoc on that method before
  "simplifying" it back to a literal substring check.
