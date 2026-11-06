/**
 * A `fetch` for `configureApi({ fetch })` that, after a 401 with the code `token_expired`, refreshes the sign-in once and
 * repeats the call. Parallel calls that fail together share one refresh. The README shows this recipe and the test checks it.
 */
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
