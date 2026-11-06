# Changelog

## 0.1.0 (not released yet)

The first version.

- Models as interfaces with readonly properties, enums as a union type plus a `const` object, `oneOf` as a union.
- One function per operation with a single `params` argument, a `fetch` client with `configureApi` and `ApiError`.
- TanStack Query hooks: a query hook, query options and a query key per GET, a mutation hook per POST, PUT, PATCH and DELETE.
- The output compiles with TypeScript 6 and 7 under strict options and passes typescript-eslint `strictTypeChecked` and `stylisticTypeChecked`.
- Made for React: `select` may change the result type, `skipToken` as arguments, suspense hooks (`useSuspenseGetProject`), the hooks pass the rules of hooks and the React Compiler, and the option `useClientDirective` for React Server Components.
- Modern TypeScript: enums are a `const` object with a derived type (no `enum`, so `erasableSyntaxOnly` and Node's type stripping work), the options `importExtension` (`none`, `ts`, `js`) and `generateHooks`, and the tests compile with `erasableSyntaxOnly`, `noPropertyAccessFromIndexSignature` and `nodenext`-style imports.
- Typed errors: `ApiError<TBody>`, a `...Error` type and an `is...Error` guard per operation from the declared error responses, used by the hooks, and `isApiError`.
- Query parameters in every OpenAPI style, text and binary responses, `void` for responses without a body.
- Generation fails with a message that names the operation for request bodies other than JSON and for cookie parameters.
