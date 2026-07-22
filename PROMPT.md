# Build prompt: `modulith-docs-markdown` — a Docusaurus (Markdown + Mermaid) renderer for Spring Modulith, with a semantic parity test

You are an agentic coding assistant working in my repository. Build a small library that documents a Spring Modulith application as **Docusaurus-flavoured Markdown with Mermaid diagrams**, carrying the **same semantic information** as Spring Modulith's built-in `Documenter` (which emits AsciiDoc + PlantUML).

The single most important acceptance criterion: **a test that runs both my new renderer and the real Spring Modulith `Documenter` over the same `ApplicationModules` instance and asserts semantic parity.** Everything else serves that goal. Read this whole brief before writing code, then work in the ordered phases below, keeping each phase green before moving on.

---

## 1. Mission and hard constraints

- **Target:** Docusaurus, which renders Markdown/MDX. Diagrams must be Mermaid (via `@docusaurus/theme-mermaid`), not PlantUML.
- **Parity, not fidelity of format:** the output format differs (Markdown vs AsciiDoc, Mermaid vs PlantUML). Parity is measured on the *set of documented facts and diagram nodes/edges*, never on bytes.
- **Render from the model, do not fork `Documenter`.** `Documenter` is a concrete class with a private Structurizr `Workspace`, a private `render(...)` that hard-codes PlantUML exporters, and a package-private `Asciidoctor` helper. There is no extension seam. Build a parallel renderer over the **public** `spring-modulith-core` model and Structurizr's Mermaid exporter.
- **Java 25.** Use records, sealed types where natural, `var`, text blocks, and streams.
- **Starting point:** two skeleton files are provided in the repo — `ModulithMermaid.java` (Structurizr workspace → Mermaid) and `ModulithDocusaurusRenderer.java` (model → Markdown). Extend them; do not start from scratch. Rename the placeholder package `com.example.modulith.docs` to our project's package.

### Reference material (read, don't depend on)

Use these as the authoritative definition of "what Spring Modulith documents." Read them to keep the parity contract in sync, but do **not** add `spring-modulith-docs` as a *main* dependency.

- `Documenter.java` and `Asciidoctor.java` in `spring-modulith-docs` (module: `org.springframework.modulith.docs`) — the canvas rows and the model calls behind each.
- `ConfigurationProperties.java` (same package) — how config properties are sourced.

---

## 2. Dependencies and module layout

- **Main dependencies:** `org.springframework.modulith:spring-modulith-core`, `com.structurizr:structurizr-core`, `com.structurizr:structurizr-export`.
  - Pin the Structurizr version to whatever `spring-modulith-docs` resolves to (`mvn dependency:tree` on a scratch module that pulls `spring-modulith-docs`). This avoids exporter API drift.
- **Test-only dependency:** `org.springframework.modulith:spring-modulith-docs` — the parity test needs the real `Documenter` on the test classpath. It must **not** leak into the main artifact.
- Standard Maven library layout. Deliverable is a reusable library plus tests; no Spring Boot app packaging.

---

## 3. Design decisions (and why) — implement these as written

1. **One renderer, Docusaurus-flavoured Markdown; no separate `Md`/`Mdx` classes.** MDX is a superset of Markdown; Mermaid fences and `:::` admonitions work in both `.md` and `.mdx`. MDX only adds React components. So keep a *single* renderer and put any MDX-only affordance behind one `MarkupEmitter` seam, rather than duplicating a whole class hierarchy. Default emitter targets plain `.md`.

2. **Diagrams via Structurizr's `com.structurizr.export.mermaid.MermaidDiagramExporter`, reusing the same view model `Documenter` builds.** Do **not** convert PlantUML text to Mermaid — that path is lossy and brittle. Build the Structurizr `Workspace` exactly as `Documenter` does (software system → container → one `Component` per module → `uses` edges from `getDirectDependencies(...).uniqueModules()`), create a `ComponentView`, and call `exporter.export(view).getDefinition()`. This guarantees the diagram is derived from the identical model, which is what makes diagram parity hold by construction.

3. **Canvas rendered as headed sections + bullet lists by default, with an HTML-`<table>` strategy behind one method seam.** Markdown tables cannot hold block content (lists, multi-line cells), which is precisely why the Spring Modulith maintainer resisted a Markdown canvas. Sections read better in Docusaurus; an HTML table is the only faithful 1:1 of the AsciiDoc canvas. Make `section(...)` the single swap point so the strategy is a one-line configuration choice, not a rewrite.

4. **Emit Docusaurus front matter, slugs, and sidebar metadata.** Each page gets `id`, `title`, `sidebar_label`, `sidebar_position`. Cross-module links resolve to Docusaurus route slugs (`./<slug>`), not filesystem paths or AsciiDoc `xref`s. Slug = module identifier lowercased with non-alphanumerics collapsed to `-`.

5. **Depend only on the public core model.** Every canvas fact below is reachable from `spring-modulith-core`, with two caveats called out (Description, Config properties). Never reach into docs-internal classes from main code.

---

## 4. The parity contract — the canonical feature set

This table is the semantic spec. Each row is a feature Spring Modulith's canvas/diagram documents, and the **public model call** that produces it. The renderer must emit every row; the parity test asserts every row.

| Canvas element | Model call (from `ApplicationModule module`, `ApplicationModules modules`) | Notes |
|---|---|---|
| Identifier | `module.getIdentifier()` | Page id / slug source |
| Display name | `module.getDisplayName()` | Page title, diagram node label |
| Description | package-info Javadoc via a `DocumentationSource` | **Caveat:** the Javadoc source is docs-internal. Replicate the package-info extraction, or document as a known gap (see §7). |
| Base package | `module.getBasePackage().getName()` | |
| Spring components | `module.getSpringBeans()` → `SpringBean::toArchitecturallyEvidentType` → `.getType()` | Spring Modulith groups these (services/repos/etc.) via `CanvasOptions`; grouping is presentational. Parity is on the *set of bean types*. |
| Bean references | `module.getDirectDependencies(modules, DependencyType.USES_COMPONENT).uniqueStream(ApplicationModuleDependency::getTargetType)` → each `.getTargetType()` + `.getTargetModule().getDisplayName()` | Fully public. |
| Aggregate roots | `module.getAggregateRoots()` → `List<JavaClass>` | |
| Value types | `module.getValueTypes()` → `List<JavaClass>` | |
| Published events | `module.getPublishedEvents()` → `EventType::getType`, filtered by `module.isExposed(type)` | Match Spring Modulith: only *exposed* events. |
| Events listened to | `module.getEventsListenedTo(modules)` → `List<JavaClass>` | |
| Configuration properties | parse `classpath:META-INF/spring-configuration-metadata.json`, keep properties whose source type is in `module.getBasePackage()` | Reproduce `ConfigurationProperties`'s logic (Jackson-parse the metadata JSON; filter by package). Requires the config-metadata annotation processor to have run. |

**Diagrams:**

| Diagram | Nodes | Edges |
|---|---|---|
| Overview | all modules (`modules.stream()`) | every `module.getDirectDependencies(modules).uniqueModules()` pair |
| Per module | the module + its direct dependencies (Spring Modulith also folds in bootstrap/upstream deps via `getBootstrapDependencies`) | the induced sub-edges |

Keep all fact accessors in **one** place — a `ModuleFacts` record (§6) — so the renderer and the parity test consume the exact same definition of "the facts."

---

## 5. Build phases (keep each green before proceeding)

**Phase 0 — Scaffold.** Create the Maven module, dependencies (§2), package rename, and drop in the two skeleton files. Add a `SampleApplication` test fixture (§below). Compile.

**Phase 1 — `ModuleFacts` extractor.** Implement the record in §6 covering every row of §4 that comes from the public model (all except Description; handle Config properties via the metadata-JSON helper). Unit-test it against the sample app: assert non-empty sets for the modules that have beans/events/aggregates.

**Phase 2 — Diagrams.** Finish `ModulithMermaid`: `overviewDiagram()` and `moduleDiagram(module)`. Acceptance: printing the overview yields a Mermaid graph whose node set == all module display names and whose edge set == the model's direct-dependency pairs.

**Phase 3 — Markdown renderer.** Finish `ModulithDocusaurusRenderer` so each module page emits: front matter, H1, module Mermaid fence, and one section per §4 row driven by `ModuleFacts` (empty rows render an explicit `_None_`). Plus an `index.md` with the overview diagram and links. Wire the `section(...)` seam and a `MarkupEmitter` interface with a single default (plain-MD) implementation.

**Phase 4 — Configuration & Description.** Implement the config-metadata reader. Attempt the Description via package-info Javadoc; if the extractor is not reachable publicly, wire it as a documented, test-acknowledged gap (§7) rather than faking it.

**Phase 5 — The parity test (the deliverable that matters).** Implement §6 in full.

**Phase 6 — Docs & polish.** README with usage, the table-strategy switch, the Mermaid `securityLevel: "loose"` note for Docusaurus, and the list of known gaps.

### Sample application fixture (required for a meaningful parity test)

Under `src/test`, build a minimal but *complete* Spring Modulith app that exercises **every** canvas row, otherwise the test proves nothing. It must contain at least two modules with a dependency between them, and collectively include: a `@Service` bean, a repository, a jMolecules/JPA **aggregate root**, a **value type**, an **exposed published event**, an **event listener** (`@ApplicationModuleListener`), a **cross-module bean reference** (module A autowires an exposed bean/interface of module B), a `package-info.java` with a Javadoc description, and a `@ConfigurationProperties` class (with the config-metadata annotation processor enabled so the JSON is generated). `ApplicationModules.of(SampleApplication.class).verify()` must pass.

---

## 6. The parity test — detailed design

### Why not a byte/structural diff
The two tools emit different markup. A diff compares formatting, not meaning, and would fail on every run for reasons we don't care about. Parity must be asserted on *facts* and *diagram topology*.

### The three-way oracle
Use the model as the source of truth and the real `Documenter` output as a cross-oracle:

1. Compute `expected = ModuleFacts.from(module, modules)` — the authoritative fact set.
2. Assert the **Spring Modulith** `.adoc` mentions every expected token. *This validates that our notion of "the facts" matches what Spring Modulith actually documents* — it catches us mis-modelling a row, and it will start failing if a Spring Modulith upgrade changes what a row contains.
3. Assert the **new renderer** `.md` mentions every expected token. *This validates our renderer.*
4. Assert the two outputs mention the **same subset** of the expected tokens (renderer-to-renderer parity, directly answering the requirement).
5. **Coverage guard:** for each canvas row Spring Modulith renders as non-empty (not `None`/`none`), assert the new renderer emits a corresponding non-empty section. This catches "a whole feature was forgotten," and — combined with an explicit allow-list of *known gaps* — it turns any newly appearing Spring Modulith row into a test failure rather than a silent regression.

To reduce false-positive substring matches, match on the **inline-code form** (both formats wrap type names in backticks: `` `OrderService` ``), not the bare name.

### Wiring
- Run the real documenter over the same instance:
  `new Documenter(modules, /* options with output = tmpSm */ ...).writeModuleCanvases().writeModulesAsPlantUml();`
  Configure the output folder via `Documenter.Options` (confirm the exact setter by reading `Documenter.java`; there is also a legacy `Documenter(modules, String outputFolder)` constructor). Locate each module's `.adoc` by listing the folder and matching the identifier (canvas filenames come from `CanvasOptions.getTargetFileName`, so don't hard-code the pattern).
- Run the new renderer: `new ModulithDocusaurusRenderer(modules).writeTo(tmpMd);`

### Test skeleton (fill in, keep API-accurate)

```java
class SemanticParityTest {

    static ApplicationModules modules;
    static Path smDir;   // Spring Modulith AsciiDoc/PlantUML output
    static Path mdDir;   // new renderer Markdown output

    @BeforeAll
    static void generate(@TempDir Path tmp) throws IOException {
        modules = ApplicationModules.of(SampleApplication.class).verify();

        smDir = tmp.resolve("sm");
        // Configure Documenter to write into smDir (see Documenter.Options / legacy ctor).
        new Documenter(modules /*, options -> smDir */)
                .writeModuleCanvases()
                .writeModulesAsPlantUml();

        mdDir = tmp.resolve("md");
        new ModulithDocusaurusRenderer(modules).writeTo(mdDir);
    }

    @TestFactory
    Stream<DynamicTest> everyModuleHasSemanticParity() {
        return modules.stream().map(module -> dynamicTest(module.getIdentifier().toString(), () -> {
            var expected = ModuleFacts.from(module, modules);
            var adoc = readCanvas(smDir, module);   // locate the module's .adoc
            var md   = Files.readString(mdDir.resolve(slug(module) + ".md"));

            for (String token : expected.allTokens()) {
                var code = "`" + token + "`";
                assertTrue(adoc.contains(code),
                    () -> "Oracle drift: Spring Modulith no longer documents " + token);
                assertTrue(md.contains(code),
                    () -> "Renderer gap: new renderer omitted " + token);
            }

            // renderer-to-renderer: same subset of expected tokens present on both sides
            var inAdoc = expected.allTokens().stream().filter(t -> adoc.contains("`"+t+"`")).collect(toSet());
            var inMd   = expected.allTokens().stream().filter(t -> md.contains("`"+t+"`")).collect(toSet());
            assertEquals(inAdoc, inMd, "Renderers disagree on documented facts for " + module.getIdentifier());
        }));
    }

    @Test
    void coverageGuard_noSpringModulithRowIsSilentlyDropped() {
        // For each canonical canvas row, if Spring Modulith rendered it non-empty,
        // assert the Markdown has a matching non-empty section — unless it is in KNOWN_GAPS.
    }

    @Test
    void overviewDiagramReflectsFullModelTopology() {
        var mermaid = new ModulithMermaid(modules).overviewDiagram();
        var modelEdges = /* build "A->B" set from getDirectDependencies(...).uniqueModules() */;
        var mermaidEdges = /* parse `-->` edges out of the Mermaid definition, by display name */;
        assertEquals(modelEdges, mermaidEdges);
        // Nodes: assert every module display name appears in the definition.
    }
}
```

### `ModuleFacts` (single source of "the facts")

```java
record ModuleFacts(
        String identifier, String displayName, String basePackage,
        SortedSet<String> springBeans, SortedSet<String> beanReferences,
        SortedSet<String> aggregateRoots, SortedSet<String> valueTypes,
        SortedSet<String> publishedEvents, SortedSet<String> eventsListenedTo,
        SortedSet<String> configProperties) {

    static ModuleFacts from(ApplicationModule m, ApplicationModules all) {
        return new ModuleFacts(
            m.getIdentifier().toString(),
            m.getDisplayName(),
            m.getBasePackage().getName(),
            simpleNames(m.getSpringBeans().stream()
                .map(b -> b.toArchitecturallyEvidentType().getType())),
            m.getDirectDependencies(all, DependencyType.USES_COMPONENT)
                .uniqueStream(ApplicationModuleDependency::getTargetType)
                .map(d -> d.getTargetType().getSimpleName())
                .collect(toCollection(TreeSet::new)),
            simpleNames(m.getAggregateRoots().stream()),
            simpleNames(m.getValueTypes().stream()),
            simpleNames(m.getPublishedEvents().stream()
                .map(EventType::getType).filter(m::isExposed)),
            simpleNames(m.getEventsListenedTo(all).stream()),
            ConfigMetadata.propertyNamesFor(m));   // reads spring-configuration-metadata.json
    }

    /** Tokens that MUST appear (as inline code) in any faithful rendering. */
    SortedSet<String> allTokens() {
        var s = new TreeSet<String>();
        s.add(basePackage);
        s.addAll(springBeans); s.addAll(beanReferences);
        s.addAll(aggregateRoots); s.addAll(valueTypes);
        s.addAll(publishedEvents); s.addAll(eventsListenedTo);
        s.addAll(configProperties);
        return s;   // description intentionally excluded — asserted softly, see §7
    }

    private static SortedSet<String> simpleNames(Stream<JavaClass> types) {
        return types.map(JavaClass::getSimpleName).collect(toCollection(TreeSet::new));
    }
}
```

---

## 7. Known gaps and how to handle them (be honest in the test, not silent)

- **Description (package-info Javadoc):** the Javadoc `DocumentationSource` Spring Modulith uses is docs-internal. Either replicate the package-info Javadoc extraction, or exclude Description from `allTokens()` and assert it *softly* (if Spring Modulith rendered a non-empty description, warn/xfail rather than hard-fail). Record the decision in the README and in a `KNOWN_GAPS` constant referenced by the coverage guard.
- **Bean grouping / value formatting:** Spring Modulith groups beans and formats types with abbreviated FQNs (`FormattableType`). This is presentational; parity is asserted on the underlying type set, so grouping differences are fine.
- **Config metadata availability:** if the annotation processor isn't wired in the test module, the metadata JSON is absent and this row is empty on *both* sides — acceptable, but ensure the fixture enables it so the row is actually exercised.

Any gap must appear in `KNOWN_GAPS` so the coverage guard treats it as intentional; anything *not* in that list that Spring Modulith documents must make the test fail.

---

## 8. Deliverables checklist

- [ ] Maven library module; `structurizr-*` on main scope, `spring-modulith-docs` on test scope only.
- [ ] `ModulithMermaid` (overview + per-module Mermaid).
- [ ] `ModulithDocusaurusRenderer` (front matter, sections, index, `MarkupEmitter` seam, `section(...)` table-strategy seam).
- [ ] `ModuleFacts` + `ConfigMetadata` helper.
- [ ] `SampleApplication` fixture exercising every canvas row; `verify()` passes.
- [ ] `SemanticParityTest`: per-module three-way token parity, renderer-to-renderer subset equality, coverage guard with `KNOWN_GAPS`, and overview-diagram topology check.
- [ ] README: usage, table-strategy switch, Docusaurus `securityLevel: "loose"` note, known gaps.
- [ ] All tests green; no `spring-modulith-docs` reference in main sources.

**Work incrementally and show me the parity test output first when it goes green — that is the proof the renderer is faithful.** If any parity assertion can only pass by weakening it, stop and flag it to me rather than loosening the check.