import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    ApiError,
    configureApi,
    createProject,
    exportMembers,
    getMe,
    getProject,
    listProjects,
    withdrawRequest,
} from '@generated/example/api';

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
