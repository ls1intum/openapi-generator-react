import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    ApiError,
    configureApi,
    createProject,
    exportMembers,
    getMe,
    getProject,
    isApiError,
    isGetProjectError,
    listProjects,
    withdrawRequest,
} from '@generated/example/api';
import { createRefreshingFetch } from './refreshing-fetch';

const fetchMock = vi.fn<typeof fetch>();

function respond(body: BodyInit | null, init: ResponseInit = {}): void {
    fetchMock.mockResolvedValueOnce(new Response(body, init));
}

function json(body: unknown, init: ResponseInit = {}): void {
    respond(JSON.stringify(body), { ...init, headers: { 'Content-Type': 'application/json', ...init.headers } });
}

/** The URL and the options of the request the client sent last. */
function sent(): { url: string; init: RequestInit; headers: Headers } {
    const [url, init = {}] = fetchMock.mock.calls.at(-1) ?? [];
    return { url: String(url), init, headers: new Headers(init.headers) };
}

beforeEach(() => {
    fetchMock.mockReset();
    configureApi({ basePath: '/api', credentials: 'same-origin', headers: () => undefined, fetch: fetchMock });
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('a GET', () => {
    it('puts the path parameter into the URL, encoded, and parses the JSON', async () => {
        json({ id: 'a/b', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 });

        const project = await getProject({ projectId: 'a/b' });

        expect(project.name).toBe('Atlas');
        expect(sent().url).toBe('/api/projects/a%2Fb');
        expect(sent().init.method).toBe('GET');
        expect(sent().headers.get('Accept')).toBe('application/json');
        expect(sent().init.body).toBeNull();
        expect(sent().headers.has('Content-Type')).toBe(false);
    });

    it('sends the query parameters it is given and leaves out the others', async () => {
        json([]);

        await listProjects({ organization: 'ls1intum', page: 0 });

        expect(sent().url).toBe('/api/projects?organization=ls1intum&page=0');
    });

    it('sends no question mark without query parameters, and works without arguments', async () => {
        json([]);

        await listProjects();

        expect(sent().url).toBe('/api/projects');
    });

    it('reads text', async () => {
        respond('tumId\nge12abc\n', { headers: { 'Content-Type': 'text/csv' } });

        await expect(exportMembers({ projectId: 'atlas' })).resolves.toBe('tumId\nge12abc\n');
    });

    it('passes the abort signal on', async () => {
        json({});
        const controller = new AbortController();

        await getMe({ signal: controller.signal });

        expect(sent().init.signal).toBe(controller.signal);
    });
});

describe('a request with a body', () => {
    it('sends the body as JSON', async () => {
        json({ id: 'atlas' }, { status: 201 });

        await createProject({ body: { name: 'Atlas', organization: 'ls1intum' } });

        expect(sent().init.method).toBe('POST');
        expect(sent().init.body).toBe('{"name":"Atlas","organization":"ls1intum"}');
        expect(sent().headers.get('Content-Type')).toBe('application/json');
    });
});

describe('a response without a body', () => {
    it('resolves to undefined', async () => {
        respond(null, { status: 204 });

        await expect(withdrawRequest({ requestId: '5c0b9a4e-0000-4000-8000-000000000000' })).resolves.toBeUndefined();
        expect(sent().init.method).toBe('DELETE');
        expect(sent().headers.has('Accept')).toBe(false);
    });
});

describe('an error response', () => {
    it('throws an ApiError with the status and the parsed JSON body', async () => {
        json({ message: 'No such project' }, { status: 404, statusText: 'Not Found' });

        const error = await getProject({ projectId: 'missing' }).catch((caught: unknown) => caught);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ status: 404, body: { message: 'No such project' }, message: '404 Not Found' });
    });

    it('keeps the text of a body that is not JSON', async () => {
        respond('Bad gateway', { status: 502, headers: { 'Content-Type': 'text/plain' } });

        const error = await getMe().catch((caught: unknown) => caught);

        expect(error).toMatchObject({ status: 502, body: 'Bad gateway' });
    });

    it('has no body when the response has none', async () => {
        respond(null, { status: 403 });

        const error = await getMe().catch((caught: unknown) => caught);

        expect(error).toMatchObject({ status: 403, body: undefined });
    });
});

describe('configureApi', () => {
    it('changes the base path, the credentials and the headers of every request', async () => {
        json({});
        configureApi({ basePath: 'https://lanyard.example/api', credentials: 'include', headers: () => ({ 'X-CSRF-Token': 'abc' }) });

        await getMe();

        expect(sent().url).toBe('https://lanyard.example/api/me');
        expect(sent().init.credentials).toBe('include');
        expect(sent().headers.get('X-CSRF-Token')).toBe('abc');
    });

    it('lets the headers of a call win over the configured ones', async () => {
        json({});
        configureApi({ headers: () => ({ 'X-Trace': 'configured' }) });

        await getMe({ headers: { 'X-Trace': 'call' } });

        expect(sent().headers.get('X-Trace')).toBe('call');
    });

    it('calls the global fetch, looked up per request, when none is configured', async () => {
        const stub = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
        vi.stubGlobal('fetch', stub);
        configureApi({ fetch: undefined });

        await getMe();

        expect(stub).toHaveBeenCalledOnce();
    });
});

describe('the typed error of an operation', () => {
    it('narrows what getProject threw and gives the declared body', async () => {
        json({ title: 'Not Found', status: 404 }, { status: 404, statusText: 'Not Found', headers: { 'Content-Type': 'application/problem+json' } });

        const error = await getProject({ projectId: 'missing' }).catch((caught: unknown) => caught);

        expect(isGetProjectError(error)).toBe(true);
        expect(isApiError(error)).toBe(true);
        if (isGetProjectError(error) && typeof error.body === 'object') {
            expect(error.body.title).toBe('Not Found');
            expect(error.status).toBe(404);
        } else {
            expect.unreachable('the error is a GetProjectError with a JSON body');
        }
    });

    it('does not take another kind of error for an ApiError', () => {
        expect(isGetProjectError(new Error('network down'))).toBe(false);
        expect(isApiError('404')).toBe(false);
    });

    it('leaves a body that is not JSON as text', async () => {
        respond('Bad Gateway', { status: 502, headers: { 'Content-Type': 'text/html' } });

        const error = await getProject({ projectId: 'a' }).catch((caught: unknown) => caught);

        expect(isGetProjectError(error) && error.body).toBe('Bad Gateway');
    });
});

describe('the refreshing fetch of the README', () => {
    const expired = () => new Response(JSON.stringify({ code: 'token_expired' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
    const person = { tumId: 'ge12abc', name: 'Ada', roles: [] };

    /** A server that answers 401 until the sign-in was refreshed. */
    function serverWithExpiredSignIn(isRefreshed: () => boolean) {
        fetchMock.mockImplementation(() =>
            Promise.resolve(
                isRefreshed()
                    ? new Response(JSON.stringify(person), { headers: { 'Content-Type': 'application/json' } })
                    : expired(),
            ),
        );
    }

    it('refreshes once and repeats the call', async () => {
        let refreshed = false;
        const refresh = vi.fn(() => {
            refreshed = true;
            return Promise.resolve(true);
        });
        serverWithExpiredSignIn(() => refreshed);
        configureApi({ fetch: createRefreshingFetch(refresh, fetchMock) });

        await expect(getMe()).resolves.toEqual(person);

        expect(refresh).toHaveBeenCalledOnce();
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('lets parallel calls share one refresh', async () => {
        let refreshed = false;
        const refresh = vi.fn(() => {
            refreshed = true;
            return Promise.resolve(true);
        });
        serverWithExpiredSignIn(() => refreshed);
        configureApi({ fetch: createRefreshingFetch(refresh, fetchMock) });

        await Promise.all([getMe(), getMe(), getMe()]);

        expect(refresh).toHaveBeenCalledOnce();
    });

    it('repeats a call with a JSON body', async () => {
        let refreshed = false;
        fetchMock.mockImplementation(() =>
            Promise.resolve(
                refreshed
                    ? new Response(JSON.stringify({ id: 'p', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 }), { status: 201, headers: { 'Content-Type': 'application/json' } })
                    : expired(),
            ),
        );
        configureApi({
            fetch: createRefreshingFetch(() => {
                refreshed = true;
                return Promise.resolve(true);
            }, fetchMock),
        });

        await createProject({ body: { name: 'Atlas', organization: 'ls1intum' } });

        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(sent().init.body).toBe(JSON.stringify({ name: 'Atlas', organization: 'ls1intum' }));
    });

    it('does not refresh for another 401', async () => {
        const refresh = vi.fn(() => Promise.resolve(true));
        json({ code: 'bad_credentials' }, { status: 401 });
        configureApi({ fetch: createRefreshingFetch(refresh, fetchMock) });

        await expect(getMe()).rejects.toMatchObject({ status: 401 });

        expect(refresh).not.toHaveBeenCalled();
    });

    it('throws the 401 when the refresh fails', async () => {
        serverWithExpiredSignIn(() => false);
        configureApi({ fetch: createRefreshingFetch(() => Promise.resolve(false), fetchMock) });

        await expect(getMe()).rejects.toBeInstanceOf(ApiError);
        expect(fetchMock).toHaveBeenCalledOnce();
    });
});
