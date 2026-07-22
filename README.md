# modulith-docs-markdown

Documents a [Spring Modulith](https://spring.io/projects/spring-modulith) application as
Docusaurus-flavoured Markdown with [Mermaid](https://mermaid.js.org/) diagrams, carrying the same semantic
information as Spring Modulith's built-in `Documenter` (which emits AsciiDoc + PlantUML).

The output format differs — Markdown vs AsciiDoc, Mermaid vs PlantUML — but the documented **facts** and
**diagram topology** are asserted to be identical by `SemanticParityTest`, which runs both tools over the
same `ApplicationModules` instance. See [How parity is verified](#how-parity-is-verified).

## Usage

```java
var modules = ApplicationModules.of(MyApplication.class).verify();

new ModulithDocusaurusRenderer(modules).writeTo(Path.of("docs/modules"));
```

This writes one page per module (`<slug>.md`) plus an `index.md` with the overview diagram and links to
every module page. Drop the output directory into your Docusaurus `docs/` tree.

### Docusaurus setup

Diagrams are Mermaid, not PlantUML, so enable `@docusaurus/theme-mermaid` in `docusaurus.config.js`:

```js
export default {
  markdown: {
    mermaid: true,
  },
  themes: ['@docusaurus/theme-mermaid'],
};
```

Structurizr's Mermaid export embeds raw HTML (`<div>`, inline `style=`) inside node/edge labels for
formatting. Mermaid only renders that HTML when `securityLevel` is `"loose"` — the default `"strict"` level
escapes it into visible tag soup. Configure it on the `mermaid` theme config:

```js
export default {
  themeConfig: {
    mermaid: {
      theme: { light: 'neutral', dark: 'dark' },
      options: { securityLevel: 'loose' },
    },
  },
};
```

### Configuration seams

Two seams exist, both defaulted to the simplest choice — swapping either is a one-line change, not a
rewrite:

```java
new ModulithDocusaurusRenderer(modules)
    .withMarkupEmitter(MarkupEmitter.markdown())      // default: plain .md
    .withCanvasStyle(ModulithDocusaurusRenderer.CanvasStyle.SECTIONS) // default
    .writeTo(outputDir);
```

- **`MarkupEmitter`** — there is one renderer, not a parallel `Md`/`Mdx` class hierarchy, because MDX is a
  strict superset of Markdown (Mermaid fences and `:::` admonitions work in both). An MDX-flavoured emitter
  only needs to override `MarkupEmitter.preamble()` (e.g. to add a component `import`); nothing else in the
  renderer changes.
- **`CanvasStyle`** — `SECTIONS` (default) renders each canvas row as a heading + bullet list, which reads
  well in Docusaurus. `HTML_TABLE` renders the whole canvas as a single HTML `<table>`, a closer 1:1 mirror
  of the AsciiDoc canvas — Markdown tables can't hold block content (lists, multi-paragraph cells), which is
  exactly why the Spring Modulith maintainers resisted a Markdown canvas in the first place.

## How diagrams are derived

`ModulithMermaid` builds the *exact same* Structurizr `Workspace` that `Documenter` builds — software system
→ container → one `Component` per module → `uses` edges from `ApplicationModule#getDirectDependencies` —
and exports it with Structurizr's own `com.structurizr.export.mermaid.MermaidDiagramExporter`. Diagram parity
holds by construction: both tools export the identical model, so there is no lossy PlantUML-to-Mermaid text
conversion anywhere in this codebase.

## How parity is verified

`SemanticParityTest` is the deliverable that matters. For the same `ApplicationModules` instance it:

1. Runs the real `Documenter` (`spring-modulith-docs`, test-scope only) to produce AsciiDoc canvases.
2. Runs `ModulithDocusaurusRenderer` to produce Markdown pages.
3. Computes `ModuleFacts` — the single, public-model-only definition of "the facts" a canvas documents
   (see `ModuleFacts.java`) — and asserts every fact appears as inline code in **both** outputs.
4. Asserts the two outputs mention the **same subset** of expected facts (renderer-to-renderer parity).
5. **Coverage guard**: for every canvas row Spring Modulith renders non-empty, asserts the Markdown has a
   corresponding non-empty section — unless the row is in `KNOWN_GAPS` (see below). This turns a future
   Spring Modulith row we haven't modelled into a test failure instead of a silent gap.
6. Asserts the overview Mermaid diagram's node/edge set matches the model's module set and direct-dependency
   pairs exactly.

Token matching does **not** look for the bare `` `SimpleName` `` form. Spring Modulith's `Asciidoctor`
renders type names via `FormattableType#getAbbreviatedFullName(module)`, which (outside the default package)
always prefixes the simple name with an abbreviated package path, e.g. `` `i.g.h.m.m.s.o.OrderPlaced` ``.
`SemanticParityTest.containsInlineCode` instead matches an inline-code span whose content *ends* with the
token, optionally preceded by a dotted prefix — still a genuine code-span match (no bare-substring
false positives), just tolerant of the abbreviation. This is documented here because it is a deliberate
correction to the naive `` "`" + token + "`" `` check one might reach for first, not a weakening of the
assertion.

### The sample fixture

`src/test/java/.../sample` is a two-module Spring Modulith app exercising every canvas row: a `@Service`,
a Spring Data repository, a jMolecules `@AggregateRoot` and `@ValueObject`, an exposed `@DomainEvent`, an
`@ApplicationModuleListener`, a cross-module bean reference via an exposed interface, a `package-info.java`
with Javadoc, and a `@ConfigurationProperties` class (with the config-metadata annotation processor wired
in, so that row is actually exercised).

It lives under `src/test/java`, so it compiles to `target/test-classes` — which ArchUnit's default
`DoNotIncludeTests` import option excludes. Every test therefore uses
`ApplicationModules.of(SampleApplication.class, ImportOption.Predefined.ONLY_INCLUDE_TESTS)` rather than the
zero-argument factory (which would silently see zero classes). See `SampleModel.java`.

The fixture's two modules are wired as a DAG (`order` depends on `inventory` for both a bean reference and
an event subscription), not bidirectionally — Spring Modulith's `verify()` runs a slice-cycle check that
treats any inter-module edge as a graph edge regardless of `DependencyType`, so a bidirectional dependency
(even split across "sync bean call" one way and "async event" the other) is a real cycle violation, not just
a lint nit.

## Known gaps

Recorded here and enforced via `SemanticParityTest.KNOWN_GAPS`, so that anything *not* listed here that
Spring Modulith documents will fail the coverage guard instead of silently regressing.

- **Description (package-info Javadoc).** Spring Modulith's `Documenter` reads a Javadoc `DocumentationSource`
  that is docs-internal and backed by the `spring-modulith-apt` annotation processor's generated metadata —
  there is no public API for it, and depending on it would mean reaching into docs-internal classes, which
  the brief explicitly rules out. This renderer instead reads `package-info.java` **source** directly
  (`PackageDescriptions.java`), relative to the conventional `src/main/java`/`src/test/java` roots under the
  working directory. This works whenever the renderer runs from a project checkout — the normal case for a
  build-time doc generator, and how the parity test itself exercises it — but is a known limitation for
  packaged/jarred consumers where source is not on disk. Description is therefore excluded from
  `ModuleFacts.allTokens()` (per the brief) and from the coverage guard's hard-fail path.
- **Bean grouping / value formatting.** Spring Modulith groups beans (services, repositories, event
  listeners, ...) and formats type names as abbreviated FQNs via `CanvasOptions`/`FormattableType`. Both are
  presentational; parity is asserted on the underlying type *set*, not on grouping or formatting.
- **Config metadata availability.** If a consumer's build doesn't run the Spring Boot
  configuration-metadata annotation processor, `spring-configuration-metadata.json` is absent and the
  Configuration properties row is empty on *both* sides — acceptable, but the sample fixture enables the
  processor explicitly (see `pom.xml`'s `annotationProcessorPaths`) so this row is actually exercised by the
  parity test. This was also needed because the JDK used to build this project no longer auto-discovers
  annotation processors from the classpath by default.

## Dependencies

Main scope: `spring-modulith-core`, `structurizr-core`, `structurizr-export` — pinned to the exact
Structurizr version (`5.0.3`) that `spring-modulith-docs:2.1.0` itself resolves, to avoid exporter API drift.
Test scope only: `spring-modulith-docs` (the parity oracle) and the sample fixture's dependencies
(`spring-modulith-events-api`, `spring-boot-autoconfigure`, `spring-data-commons`, `jmolecules-ddd`,
`jmolecules-events`, `spring-boot-configuration-processor`). `spring-modulith-docs` never appears in
`src/main`.
