# OpenAPI Generator for React

[![Build](https://github.com/ls1intum/openapi-generator-react/actions/workflows/build.yml/badge.svg)](https://github.com/ls1intum/openapi-generator-react/actions)

A custom [OpenAPI Generator](https://openapi-generator.tech/) that turns an OpenAPI 3 document into a typed client for
React applications: readonly models, a small `fetch` client, and [TanStack Query](https://tanstack.com/query) hooks. It
is the React counterpart of
[`openapi-generator-angular22`](https://github.com/ls1intum/openapi-generator-angular22) and follows the same rule:
the generated code has to read like code a person would write.

**Status:** version 0.1.0, the first version. It covers JSON APIs. Multipart and other non-JSON request bodies are not
supported yet and fail the generation with a clear message, see [Not supported yet](#not-supported-yet).

## What you get

- **Models** as `interface`s with `readonly` properties and `readonly T[]` collections. Enums are a `const` object with a
  type derived from it.
- **One function per operation**, with one `params` argument that holds the path, query and header parameters and the
  `body`. It returns the parsed response and throws an `ApiError` for an error status.
- **A query hook for every GET**: `useGetProject`, a suspense hook `useSuspenseGetProject` for `<Suspense>` and error
  boundaries, and `getProjectQueryOptions` and `getProjectQueryKey` for prefetching, loaders and cache access.
- **A mutation hook for every POST, PUT, PATCH and DELETE**: `useCreateProject`.
- **No runtime dependency** besides `@tanstack/react-query` (version 5) and React. The `fetch` client is generated into
  your project, so you can read it, and `configureApi` sets the base path, credentials and headers once.
- **Strict-mode safe**: the output type-checks with `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`,
  `verbatimModuleSyntax` and `isolatedModules`, with TypeScript 6 and 7, and passes the strict and stylistic type-checked
  rules of typescript-eslint without exceptions. The tests check all of it.

## Why this generator

There are good generators for TypeScript, and several do more than this one. Orval, Kubb and Hey API also generate
validation schemas (Zod), mock handlers (MSW), and clients for other frameworks and query libraries. This one does less
and is built around different choices:

| | This generator | [Orval](https://orval.dev) | [Kubb](https://kubb.dev) | [Hey API `openapi-ts`](https://heyapi.dev) | `openapi-typescript` + `openapi-fetch` + `openapi-react-query` |
| --- | --- | --- | --- | --- | --- |
| Runs on | The JVM, as an OpenAPI Generator plugin: Gradle, Maven or CLI | Node.js 22.18 or newer | Node.js or Bun | Node.js 22 or newer | Node.js |
| Version in November 2026 | 0.1.0 | 8.46 | 5.15 | 0.99 (not 1.0 yet) | 7.13, 0.17 and 0.5 (the last release of `openapi-react-query` was in February; `openapi-typescript` declares TypeScript `^5.x` as its peer) |
| React Query hooks | yes, with `select`, `skipToken` and suspense hooks | yes | yes | yes, as a plugin | yes, `openapi-react-query` |
| Other query libraries and frameworks | no | SWR, Vue, Svelte, Solid, Angular and more | SWR, Vue | other plugins | no |
| Validation (Zod), mocks (MSW) | no | yes | yes | Zod plugin | no |
| Generated code needs | React and TanStack Query, or nothing with `generateHooks=false` | per target | per plugin | per plugin | the runtime packages |

The facts about the other tools are those of their READMEs and of npm on 2026-11-06; check them before you decide.

**Choose this one when**

- **Your server is built on the JVM.** The Gradle or Maven build of a Spring Boot project already runs OpenAPI
  Generator, so the client is regenerated in the same step as the server's OpenAPI document, and nobody needs a Node.js
  toolchain for the generation. A CI check can fail when the checked-in client differs from a fresh one.
- **You want the output to be small and readable.** Per tag, one file of plain functions and one of hooks; per model,
  one interface. There are no classes besides `ApiError`, no proxies, no decorators, no runtime library, and no generated code you would
  not write yourself. The support code is three short files in your project.
- **You want the quality to be a guarantee, not a hope.** The tests compile the output of every fixture with
  TypeScript 6 and 7 under the strictest options, lint it with typescript-eslint's strict and stylistic type-checked
  rules and the rules of hooks and of the React Compiler, and run it. A change that breaks one of them cannot be
  merged.
- **You want the same approach as the Angular client.** It is the React counterpart of the AET generator
  [`openapi-generator-angular22`](https://github.com/ls1intum/openapi-generator-angular22), with the same options style,
  the same test strategy and the same owner.

**Choose another one when** you need runtime validation of responses (this generator trusts the server and casts the
parsed JSON), generated mocks, a framework other than React, SWR instead of TanStack Query, or infinite queries today.

## Modern TypeScript

The generated code is checked against the options below, with TypeScript 6 and 7 in every test run:

| What | How |
| --- | --- |
| `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`, `noPropertyAccessFromIndexSignature`, `noImplicitOverride`, `noUnusedLocals`, `noUnusedParameters` | The output compiles. Optional arguments accept `undefined` explicitly, so callers do not trip over `exactOptionalPropertyTypes`. |
| `verbatimModuleSyntax`, `isolatedModules` | Type imports are `import type`, and the index files use `export type *`. |
| `erasableSyntaxOnly` | No `enum`, no `namespace`, no parameter properties. Enums are `const` objects with a derived type, so the code runs under Node's type stripping and in every transpiler that only erases types. |
| `module: nodenext` and `bundler` | `importExtension` writes relative imports with `.ts` (for Node's type stripping and `rewriteRelativeImportExtensions`) or `.js`, or none (for bundlers). Every variant is compiled. |
| Immutability | `readonly` properties and `readonly T[]`, `Readonly<Record<string, T>>` for maps. |
| `unknown`, not `any` | An untyped schema is `unknown`. There is no `any` in the output. |
| Narrowing | A `oneOf` becomes a union. Where the branches have a single-value discriminator, a `switch` narrows it and the compiler checks that it is exhaustive. |
| Web standards | `fetch`, `Headers`, `URLSearchParams`, `AbortSignal` and `Response`: no HTTP library. The client needs ES2022 and the DOM types. |
| Lint | typescript-eslint `strictTypeChecked` and `stylisticTypeChecked`, and `eslint-plugin-react-hooks` with the React Compiler rules, without exceptions where a rule can be met. |

With `importExtension=ts` and `generateHooks=false`, the generated client runs on Node.js 24 as it is, without a build
step.

What is not modern, on purpose: dates are `string`, as they are in JSON, and the parsed response is cast to its type
without runtime validation. Both would need a schema library at runtime, which this generator avoids.

## Why TanStack Query

The question is fair, and the answer is in two parts.

1. **The hooks are a layer on top of plain functions.** Every operation is first a plain async function in
   `<tag>-api.ts` that needs nothing but `fetch`. The hooks in `<tag>-hooks.ts` are the only code that depends on
   TanStack Query. With `generateHooks=false` the generator writes the functions only, and the project needs no
   TanStack Query. A hooks layer for another library would be another set of templates, not another generator.
2. **For a React app, TanStack Query is the best fit for what the hooks have to do.** Data from a server needs a cache
   with keys, deduplication of equal requests, cancellation, retries, refetching when it is stale, invalidation after a
   write, and Suspense. React 19 gives the primitives (`use`, Suspense) but no cache; writing one is the work that
   TanStack Query already does. Its keys fit generated code well: the tag, the operation and the arguments are the key.
   SWR is lighter and simpler, and a good choice for an app that mostly reads; it has a smaller feature set.

The cost: it is a runtime dependency, and its API moves. Version 5.106 already marks `fetchQuery` and
`prefetchQuery` as deprecated in favour of `queryClient.query`. The generated hooks use only `useQuery`,
`useSuspenseQuery`, `useMutation`, `queryOptions` and `skipToken`, which are the stable core, and the tests run against
the pinned version, so a breaking change shows up in CI.

## Generated code

The example is generated from [`example/example-openapi.yaml`](example/example-openapi.yaml).

### Models

```typescript
export interface AccessRequest {
    readonly id: string;
    readonly projectId: string;
    readonly status: RequestStatus;
    readonly requestedUntil: string;
    readonly decidedAt?: string;
}

export const RequestStatus = {
    Open: 'OPEN',
    Approved: 'APPROVED',
    Rejected: 'REJECTED',
    Withdrawn: 'WITHDRAWN',
} as const;

export type RequestStatus = (typeof RequestStatus)[keyof typeof RequestStatus];
```

An enum is one `const` object, and its type is derived from it: the values exist once, at runtime (`RequestStatus.Open`) and
in the type (`'OPEN' | 'APPROVED' | ...`). It is not a TypeScript `enum`, which does not run under Node's type stripping.

A property that the spec marks `nullable` is typed `| null`. A `oneOf` schema becomes a union of its branches. A map is
`Readonly<Record<string, T>>`. An operation whose response has no body resolves to `undefined`.

### Operation functions

```typescript
/** The arguments of {@link listRequests}. */
export interface ListRequestsParams {
    readonly projectId: string;
    /** Only requests in one of these states. */
    readonly status?: readonly RequestStatus[] | undefined;
}

/** Lists the requests of a project. */
export function listRequests(params: ListRequestsParams, options?: RequestOptions): Promise<readonly AccessRequest[]> {
    const query = new URLSearchParams();
    appendQueryParam(query, 'status', params.status);
    return request<readonly AccessRequest[]>(
        {
            method: 'GET',
            path: `/projects/${encodeURIComponent(params.projectId)}/requests`,
            query,
            responseType: 'json',
        },
        options,
    );
}
```

The `params` object is the one argument, whatever the operation has: it keeps call sites readable, it is the same
object a hook uses for its query key, and adding a parameter to the spec does not change the order of the others. An
operation without parameters takes none, and `params` may be left out when all of its members are optional. A path,
query or header parameter that has the same name as another one gets a digit suffix, and `body` is reserved for the
request body.

### Hooks

```typescript
export const requestsQueryKey = ['requests'] as const;

export function listRequestsQueryKey(params: ListRequestsParams) {
    return [...requestsQueryKey, 'listRequests', params] as const;
}

export function listRequestsQueryOptions(params: ListRequestsParams) {
    return queryOptions<readonly AccessRequest[], ListRequestsError>({
        queryKey: listRequestsQueryKey(params),
        queryFn: ({ signal }) => listRequests(params, { signal }),
    });
}

export function useListRequests(params: ListRequestsParams, options?: QueryHookOptions<readonly AccessRequest[], ListRequestsError>) {
    return useQuery<readonly AccessRequest[], ListRequestsError>({ ...listRequestsQueryOptions(params), ...options });
}

export function useCreateRequest(options?: MutationHookOptions<AccessRequest, CreateRequestParams, CreateRequestError>) {
    return useMutation<AccessRequest, CreateRequestError, CreateRequestParams>({
        mutationFn: (params) => createRequest(params),
        ...options,
    });
}
```

Made for React:

- **`select`** may return another type, so a component can subscribe to a part of the data and render again only when
  that part changes: `useListProjects({}, { select: (projects) => projects.length })` is typed `number | undefined`.
- **`skipToken`** as the arguments waits for them without a placeholder and without `enabled`:
  `useGetProject(projectId === undefined ? skipToken : { projectId })`.
- **Suspense** hooks return data that is always there, and `getProjectQueryOptions` plugs into route loaders and
  `queryClient.query`.
- **Stable, serializable keys**: the arguments object is part of the key, so equal arguments share one cache entry
  whatever the identity of the object, and a new object per render costs nothing.
- **Immutable types** (`readonly` everywhere) match how React state is updated, and a mutable object is assignable
  to them.
- **No re-renders from the client itself**: it has no state, no context and no provider. TanStack Query renders again
  only for the fields a component reads.
- **React Compiler and the rules of hooks**: the generated hooks pass `eslint-plugin-react-hooks` with the compiler
  rules; the tests lint them.
- **`useClientDirective`** (default `false`) starts the hooks files with `'use client'`, for Next.js and other
  frameworks with React Server Components. The plain functions in `*-api.ts` need no directive and work on the server.

Every query key starts with the key of its tag (`requestsQueryKey`), then the operation name and the arguments, so
`queryClient.invalidateQueries({ queryKey: requestsQueryKey })` refetches everything of a tag, and
`getProjectQueryKey({ projectId })` addresses one entry. A hook takes the options of `useQuery` or `useMutation`
without the key and the function it sets itself.

### In a component

```tsx
import { useQueryClient } from '@tanstack/react-query';
import { configureApi, projectsQueryKey, useCreateProject, useGetProject } from './api-client';

// Once, at start-up.
configureApi({ basePath: '/api', credentials: 'include', headers: () => ({ 'X-CSRF-Token': readCsrfToken() }) });

function ProjectPage({ projectId }: { projectId: string }) {
    const queryClient = useQueryClient();
    const project = useGetProject({ projectId });
    const create = useCreateProject({
        onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsQueryKey }),
    });

    if (project.isPending) return <Spinner />;
    if (project.error?.status === 404) return <NotFound />;
    return <h1>{project.data?.name}</h1>;
}
```

`ApiError` has the `status`, the `response` and the `body`: the parsed JSON of the error response, its text when it is
not JSON, or `undefined`.

### Errors

The error of a hook, such as `project.error` above, is typed. For each operation the generated file holds the type of
what it throws and a guard for code that catches the error itself:

```ts
// What getProject throws for an error status; the body is a Problem when the response is JSON.
export type GetProjectError = ApiError<Problem>;
export function isGetProjectError(error: unknown): error is GetProjectError;

try {
    await getProject({ projectId });
} catch (caught) {
    if (isGetProjectError(caught) && typeof caught.body === 'object') {
        showMessage(caught.body.title); // typed: a Problem
    }
}
```

The body type is the union of the schemas that the operation declares for its error responses (`4XX`, `5XX`, a status
code, or `default` when there is also a 2xx response). Without one, the error is a plain `ApiError` and the body is
`unknown`. The type comes from the API description: the guard checks the class, not the shape of the body, so a gateway
that answers with its own page gives a `string`, which the type allows (`TBody | string | undefined`).
`isApiError(error)` narrows any error of the client to `ApiError`.

### The client and query parameters

`api/client.ts` holds `configureApi`, `ApiError` and `request`. `api/query-params.ts` holds `appendQueryParam`, which
sends each query parameter in its declared `style` and `explode`:

- `form` with explode, the default, repeats the key of an array and sends an object as one key per property. A nested
  object gets dotted keys (`range.min=1`), which is how Spring binds a query DTO.
- Without explode, `form`, `spaceDelimited` and `pipeDelimited` join an array, or an object's keys and values, with a
  comma, a space or a pipe.
- `deepObject` sends `filter[name]=x`.
- A parameter declared with JSON content goes out as JSON.
- `null` and `undefined` are left out, dates go out as ISO 8601, and a value that has no OpenAPI query format, such as
  an array inside an array, throws instead of sending `[object Object]`.

### Authentication

The client has no login logic. `configureApi` takes three things that cover the usual setups:

- `credentials: 'include'` for a session or token in `HttpOnly` cookies.
- `headers`, called for every request, for a CSRF token read from a cookie or for a bearer token you already hold.
- `fetch`, for everything else. Wrap `fetch` to refresh an expired sign-in: the wrapper below answers a `401` with the
  code `token_expired` by calling your refresh function once, shared by parallel calls, and then repeats the call. The
  client sends JSON bodies as strings, so repeating a call is safe. The refresh function must not use the wrapped `fetch`,
  or an expired refresh token would refresh forever.

```ts
export function createRefreshingFetch(
    refresh: () => Promise<boolean>,
    base: typeof fetch = (input, init) => globalThis.fetch(input, init),
): typeof fetch {
    let refreshing: Promise<boolean> | undefined;

    return async (input, init) => {
        const response = await base(input, init);
        if (response.status !== 401 || !(await isTokenExpired(response))) {
            return response;
        }
        refreshing ??= refresh().finally(() => {
            refreshing = undefined;
        });
        return (await refreshing) ? base(input, init) : response;
    };
}

async function isTokenExpired(response: Response): Promise<boolean> {
    const body: unknown = await response.clone().json().catch(() => undefined);
    return typeof body === 'object' && body !== null && 'code' in body && body.code === 'token_expired';
}
```

```ts
configureApi({
    credentials: 'include',
    headers: () => ({ 'X-CSRF-Token': readCookie('csrf') }),
    fetch: createRefreshingFetch(async () => (await fetch('/api/session/refresh', { method: 'POST' })).ok),
});
```

The generator's tests run this code against a mocked `fetch`. Adjust how it recognises an expired sign-in to your server.

## Installation

The generator is not on Maven Central yet. Until it is, build it from source with `./gradlew publishToMavenLocal` and
add `mavenLocal()` to the repositories.

### Gradle

```kotlin
plugins {
    id("org.openapi.generator") version "7.21.0"
}

dependencies {
    openapiGenerator("de.tum.cit.aet:openapi-generator-react:0.1.0")
}

openApiGenerate {
    generatorName.set("react-query")
    inputSpec.set("$projectDir/src/main/resources/openapi.yaml")
    outputDir.set("$projectDir/../client/src/api-client")
}
```

### Maven

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.21.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <generatorName>react-query</generatorName>
                <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
                <output>${project.basedir}/../client/src/api-client</output>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>de.tum.cit.aet</groupId>
            <artifactId>openapi-generator-react</artifactId>
            <version>0.1.0</version>
        </dependency>
    </dependencies>
</plugin>
```

### CLI

```bash
java -cp openapi-generator-react-0.1.0.jar:openapi-generator-cli-7.21.0.jar \
    org.openapitools.codegen.OpenAPIGenerator generate \
    -g react-query \
    -i openapi.yaml \
    -o ./src/api-client
```

The client needs `react` (19) and `@tanstack/react-query` (5) in the project that contains it. TanStack Query is a separate
npm package that the generator does not ship, and only the hooks files import it: with `generateHooks=false` the
generator writes the plain functions only, and the client needs neither TanStack Query nor React.

### Keeping the client in sync

Generate the client in CI and fail when it differs from the committed one:

```bash
./gradlew openApiGenerate
git diff --exit-code -- client/src/api-client
```

## Configuration

| Option | Default | Description |
| --- | --- | --- |
| `generateHooks` | `true` | Generate the TanStack Query hooks. `false` writes the plain functions only, without any dependency on TanStack Query |
| `importExtension` | `none` | The file extension of relative imports: `none` (bundlers), `ts` (Node's type stripping, `allowImportingTsExtensions`) or `js` (`nodenext`) |
| `useClientDirective` | `false` | Start the hooks files with `'use client'` |

The standard options of the OpenAPI Generator apply as well, among them
`modelNamePrefix`, `modelNameSuffix`, `apiPackage` (default `api`), `modelPackage` (default `models`),
`typeMappings` and `nameMappings`.

A file is written per model and per tag, with the names in kebab-case (`access-request.ts`, `requests-api.ts`,
`requests-hooks.ts`). The tag of an operation without one is `default`.

## Not supported yet

What the generator cannot produce correctly fails the generation, with a message that names the operation:

- request bodies of `multipart/form-data`, `application/x-www-form-urlencoded`, text and binary content,
- cookie parameters,
- an arguments type (`<OperationId>Params`) that has the name of a schema: rename one of them or set `modelNameSuffix`.

Not covered by a failure, and different from what you may expect:

- A `oneOf` schema that declares properties of its own becomes the union of its branches without them, and a schema that
  extends a `oneOf` schema with `allOf` is generated without its parent. Both log a warning.
- There is no `useInfiniteQuery` hook yet, and a POST that only reads (a search) becomes a mutation.
- A mutation hook does not invalidate or update the cache; pass `onSuccess` and use the query keys of the tag.
- The response type of an operation is that of its first 2xx response. The type of an error does not depend on the status: it is the union of the declared error bodies.

## Building from source

```bash
./gradlew build            # compiles, runs the tests, builds the jar
./gradlew generateExample  # generates example/example-openapi.yaml into build/generated/example
```

The tests need Node.js 24 and npm: `./gradlew test` runs `npm ci` in `src/test/typescript` first. They generate a
client for every spec in `src/test/resources/fixtures` and for the example, type-check it with the pinned TypeScript
under strict compiler options, and run the generated example in Vitest against a mocked `fetch`.

## Contributing

See [AGENTS.md](AGENTS.md). Releases are described in [RELEASING.md](RELEASING.md).

## License

MIT, see [LICENSE](LICENSE).
