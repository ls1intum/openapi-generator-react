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

- **Models** as `interface`s with `readonly` properties and `readonly T[]` collections. Enums are a union type plus a
  `const` object.
- **One function per operation**, with one `params` argument that holds the path, query and header parameters and the
  `body`. It returns the parsed response and throws an `ApiError` for an error status.
- **A query hook for every GET**: `useGetProject`, plus `getProjectQueryOptions` and `getProjectQueryKey` for
  prefetching and cache access.
- **A mutation hook for every POST, PUT, PATCH and DELETE**: `useCreateProject`.
- **No runtime dependency** besides `@tanstack/react-query` (version 5) and React. The `fetch` client is generated into
  your project, so you can read it, and `configureApi` sets the base path, credentials and headers once.
- **Strict-mode safe**: the output type-checks with `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`,
  `verbatimModuleSyntax` and `isolatedModules`, with TypeScript 6 and 7, and passes the strict and stylistic type-checked
  rules of typescript-eslint without exceptions. The tests check all of it.

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

export type RequestStatus = 'OPEN' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export const RequestStatus = {
    Open: 'OPEN',
    Approved: 'APPROVED',
    Rejected: 'REJECTED',
    Withdrawn: 'WITHDRAWN',
} as const;
```

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
    return queryOptions<readonly AccessRequest[], ApiError>({
        queryKey: listRequestsQueryKey(params),
        queryFn: ({ signal }) => listRequests(params, { signal }),
    });
}

export function useListRequests(params: ListRequestsParams, options?: QueryHookOptions<readonly AccessRequest[]>) {
    return useQuery<readonly AccessRequest[], ApiError>({ ...listRequestsQueryOptions(params), ...options });
}

export function useCreateRequest(options?: MutationHookOptions<AccessRequest, CreateRequestParams>) {
    return useMutation<AccessRequest, ApiError, CreateRequestParams>({
        mutationFn: (params) => createRequest(params),
        ...options,
    });
}
```

Every query key starts with the key of its tag (`requestsQueryKey`), then the operation name and the arguments, so
`queryClient.invalidateQueries({ queryKey: requestsQueryKey })` refetches everything of a tag, and
`getProjectQueryKey({ projectId })` addresses one entry. A hook takes the options of `useQuery` or `useMutation`
without the key and the function it sets itself.

### In a component

```tsx
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, configureApi, projectsQueryKey, useCreateProject, useGetProject } from './api-client';

// Once, at start-up.
configureApi({ basePath: '/api', credentials: 'include', headers: () => ({ 'X-CSRF-Token': readCsrfToken() }) });

function ProjectPage({ projectId }: { projectId: string }) {
    const queryClient = useQueryClient();
    const project = useGetProject({ projectId });
    const create = useCreateProject({
        onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsQueryKey }),
    });

    if (project.isPending) return <Spinner />;
    if (project.error instanceof ApiError && project.error.status === 404) return <NotFound />;
    return <h1>{project.data?.name}</h1>;
}
```

`ApiError` has the `status`, the `response` and the `body`: the parsed JSON of the error response, its text when it is
not JSON, or `undefined`.

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

The client needs `react` (19) and `@tanstack/react-query` (5) in the project that contains it.

### Keeping the client in sync

Generate the client in CI and fail when it differs from the committed one:

```bash
./gradlew openApiGenerate
git diff --exit-code -- client/src/api-client
```

## Configuration

The generator has no options of its own yet. The standard ones of the OpenAPI Generator apply, among them
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
- The response type of an operation is that of its first 2xx response, and error responses are not typed: `ApiError.body` is `unknown`.

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
