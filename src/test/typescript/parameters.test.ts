import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearThings, configureApi, createThing, getByNumber, getThing, listThings } from '@generated/parameters/api';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(() => Promise.resolve(new Response('{"id":"1"}', { headers: { 'Content-Type': 'application/json' } })));
    configureApi({ basePath: '', fetch: fetchMock });
});

function sent(): { url: string; init: RequestInit; headers: Headers } {
    const [url, init = {}] = fetchMock.mock.calls.at(-1) ?? [];
    return { url: String(url), init, headers: new Headers(init.headers) };
}

describe('parameters that share a name', () => {
    it('get a digit suffix, in the order path, query, header, so that body stays free for the body', async () => {
        await getThing({
            id: 'p',
            id2: 'q',
            body2: 'b',
            delete: 'd',
            pageSize: 3,
            tags: ['a', 'b'],
            ids: [1, 2],
            filter: { name: 'n', min: 1 },
            xTraceId: 'trace',
            xTenant: 'tenant',
        });

        expect(sent().url).toBe(
            '/things/p?id=q&body=b&delete=d&page-size=3&tags=a&tags=b&ids=1%7C2&filter%5Bname%5D=n&filter%5Bmin%5D=1',
        );
        expect(sent().headers.get('X-Trace-Id')).toBe('trace');
        expect(sent().headers.get('X-Tenant')).toBe('tenant');
    });

    it('leaves out an optional header that is not set', async () => {
        await getThing({ id: 'p', xTenant: 'tenant' });

        expect(sent().url).toBe('/things/p');
        expect(sent().headers.has('X-Trace-Id')).toBe(false);
    });
});

describe('path parameters', () => {
    it('are not encoded when they are numbers or booleans', async () => {
        await getByNumber({ count: 3, enabled: true });

        expect(sent().url).toBe('/things/3/true');
    });
});

describe('operations without arguments', () => {
    it('need none, and an optional body may be left out', async () => {
        await listThings();
        expect(sent().url).toBe('/things');

        await createThing();
        expect(sent().init.method).toBe('POST');
        expect(sent().init.body).toBeNull();

        await clearThings();
        expect(sent().init.method).toBe('DELETE');
    });
});
