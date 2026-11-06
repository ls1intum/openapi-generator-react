# Repository Guidelines

## Project Structure
- `src/main/java/de/tum/cit/aet/openapi/` holds the generator. `ReactQueryGenerator` is the entry point, `TypeScriptSnippets` builds the TypeScript snippets that the templates print, `Names` converts case.
- `src/main/resources/react-query/` holds the Mustache templates of the generated code. `client`, `query-params` and `hook-options` are copied into every client as they are; `api`, `hooks` and `model` are rendered per tag or model.
- `src/main/resources/META-INF/services/` registers the generator with the OpenAPI Generator SPI.
- `example/example-openapi.yaml` is the spec of the README. `src/test/resources/fixtures` holds one spec per feature that the tests generate from, `src/test/resources/invalid` the specs that must fail.
- `src/test/typescript` holds the pinned compiler, React and TanStack Query, and the Vitest tests of the generated code.

## Commands
- `./gradlew build` compiles, runs all tests and builds the jar. It needs JDK 25, Node.js 24 and npm.
- `./gradlew test` runs the tests, `./gradlew generateExample` generates the example into `build/generated/example`.
- `./gradlew publishToMavenLocal` installs the generator into `~/.m2` for a consumer to try.

## Coding style
- Java 25, 4-space indentation, braces on the same line. Put logic in Java and keep the templates minimal: a template prints what a vendor extension holds.
- A name that a template reads from an operation, a model or a property is a vendor extension that starts with `x-`.
- The generated code is the product. It has to read like code a person wrote, with a doc comment for everything public, and it has to compile under `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`, `verbatimModuleSyntax` and `isolatedModules` and pass typescript-eslint `strictTypeChecked` and `stylisticTypeChecked` and the rules of `eslint-plugin-react-hooks` without disable comments, except the ones the template adds itself where a rule cannot be met. Check the output of a change, not only the tests.
- What the generator cannot produce correctly fails with an `IllegalArgumentException` that names the operation. Never emit code that does not work.

## Testing
- Every feature has a fixture, and `GeneratedCodeCompilesTest` type-checks the output of all of them with TypeScript 6 and 7 and lints it with `src/test/typescript/eslint.config.mjs`, also with `modelNamePrefix` and `modelNameSuffix` set.
- Behaviour of the generated client is tested in `src/test/typescript/*.test.ts(x)` through `GeneratedClientTest`, against a mocked `fetch`. `hooks.test.tsx` reads the fields it needs while rendering, because TanStack Query re-renders only for the fields a component read.
- A spec that must fail generation goes into `src/test/resources/invalid` with a test in `UnsupportedSpecsTest`.

## Commits and pull requests
- Short, imperative commit messages, e.g. "Add a mutation hook for PATCH".
- A pull request that changes the generated output says which files of the example change and why.
- Do not add AI attribution to commits or pull requests.
- Do not write "frontend" or "backend", in code, documentation, commit messages or pull requests. Name the component instead: web app, client, server, application server. This follows the [terminology guideline of Artemis](https://github.com/ls1intum/Artemis/blob/4c05813a14f7d07a986d7f18b1763022179503f1/documentation/docs/developer/guidelines/terminology.mdx).
