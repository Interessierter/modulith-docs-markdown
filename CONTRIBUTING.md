# Contributing

Thanks for considering a contribution to `modulith-docs-markdown`.

## Reporting bugs / requesting features

Open an [issue](https://github.com/haisi/modulith-docs-markdown/issues) with a minimal repro (a
`ApplicationModules` setup, or a link to a sample repo) for bugs, or a short description of the use case for
feature requests.

## Making changes

1. Fork the repo and create a branch off `main`.
2. Build and test:
   ```shell
   ./mvnw verify
   ```
3. Keep the change focused. If you're proposing something larger (a new public API, a new dependency), open
   an issue first to discuss the approach before writing code.
4. Formatting, Checkstyle, Error Prone/NullAway, and 100% test coverage (excluding the `sample` test fixture,
   which is exercised structurally rather than line-by-line) are all enforced by `verify` — run
   `./mvnw spotless:apply` to auto-format code and `pom.xml` before committing.
5. If your change affects what a canvas row documents, `SemanticParityTest` should catch it — a change that
   only passes by weakening a parity assertion needs to be flagged and discussed in the PR, not merged
   silently.
6. Open a pull request against `main` describing the change and why it's needed. CI
   (`.github/workflows/ci.yml`) runs the build and test suite on every PR.

## Releasing

Releases are cut by a maintainer by pushing a `vX.Y.Z` tag — see the
[Releasing](README.md#releasing) section of the README. Contributors don't need to worry about versioning or
publishing.

## Code of Conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).
