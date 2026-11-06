import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    ApiError,
    configureApi,
    getProjectQueryKey,
    getProjectQueryOptions,
    listProjectsQueryKey,
    projectsQueryKey,
    useCreateProject,
    useGetProject,
    useGetMe,
    useListProjects,
    useWithdrawRequest,
} from '@generated/example/api';

const fetchMock = vi.fn<typeof fetch>();

function json(body: unknown, init: ResponseInit = {}): void {
    fetchMock.mockResolvedValueOnce(
        new Response(JSON.stringify(body), { ...init, headers: { 'Content-Type': 'application/json' } }),
    );
}

function setUp(): { client: QueryClient; wrapper: (props: { children: ReactNode }) => ReactNode } {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return {
        client,
        wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
    };
}

beforeEach(() => {
    fetchMock.mockReset();
    configureApi({ basePath: '/api', fetch: fetchMock });
});

// Vitest has no globals, so Testing Library cannot unmount the hooks of a test by itself.
afterEach(cleanup);

describe('a query hook', () => {
    it('loads the data', async () => {
        json({ id: 'atlas', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 });
        const { wrapper } = setUp();

        const { result } = renderHook(() => useGetProject({ projectId: 'atlas' }), { wrapper });

        await waitFor(() => expect(result.current.isSuccess).toBe(true));
        expect(result.current.data?.name).toBe('Atlas');
        expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/projects/atlas');
    });

    it('takes the options of useQuery', async () => {
        json([{ id: 'a' }, { id: 'b' }]);
        const { wrapper } = setUp();

        const { result } = renderHook(() => useListProjects({ page: 1 }, { select: (projects) => projects.slice(0, 1) }), { wrapper });

        await waitFor(() => expect(result.current.isSuccess).toBe(true));
        expect(result.current.data).toEqual([{ id: 'a' }]);
    });

    it('does not fetch while it is disabled', () => {
        const { wrapper } = setUp();

        renderHook(() => useGetProject({ projectId: 'atlas' }, { enabled: false }), { wrapper });

        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('fails with an ApiError', async () => {
        json({ message: 'No' }, { status: 403 });
        const { wrapper } = setUp();

        const { result } = renderHook(() => useGetMe(), { wrapper });

        await waitFor(() => expect(result.current.isError).toBe(true));
        expect(result.current.error).toBeInstanceOf(ApiError);
    });

    it('stores its data under the query key', async () => {
        json({ id: 'atlas', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 });
        const { client, wrapper } = setUp();

        const { result } = renderHook(() => useGetProject({ projectId: 'atlas' }), { wrapper });
        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(client.getQueryData(getProjectQueryKey({ projectId: 'atlas' }))).toEqual(result.current.data);
    });
});

describe('the query keys', () => {
    it('start with the key of the tag, then the operation and the arguments', () => {
        expect(projectsQueryKey).toEqual(['projects']);
        expect(getProjectQueryKey({ projectId: 'atlas' })).toEqual(['projects', 'getProject', { projectId: 'atlas' }]);
        expect(listProjectsQueryKey()).toEqual(['projects', 'listProjects', {}]);
    });

    it('can be prefetched through the query options', async () => {
        json({ id: 'atlas', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 });
        const { client } = setUp();

        await client.prefetchQuery(getProjectQueryOptions({ projectId: 'atlas' }));

        expect(client.getQueryData(getProjectQueryKey({ projectId: 'atlas' }))).toMatchObject({ name: 'Atlas' });
    });
});

describe('a mutation hook', () => {
    it('sends the request when mutate is called, and refetches what the tag key invalidates', async () => {
        json({ id: 'atlas', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 });
        json({ id: 'atlas', name: 'Atlas', organization: 'ls1intum', maxMonths: 12 }, { status: 201 });
        const { client, wrapper } = setUp();
        // TanStack Query re-renders a component only for the fields it read while rendering, so read them here.
        const query = renderHook(
            () => {
                const { data, isSuccess } = useGetProject({ projectId: 'atlas' });
                return { data, isSuccess };
            },
            { wrapper },
        );
        await waitFor(() => expect(query.result.current.isSuccess).toBe(true));

        json({ id: 'atlas', name: 'Atlas 2', organization: 'ls1intum', maxMonths: 12 });
        const mutation = renderHook(
            () => useCreateProject({ onSuccess: () => client.invalidateQueries({ queryKey: projectsQueryKey }) }),
            { wrapper },
        );
        await act(async () => {
            await mutation.result.current.mutateAsync({ body: { name: 'Atlas', organization: 'ls1intum' } });
        });

        const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
        expect(post?.[1]?.body).toBe('{"name":"Atlas","organization":"ls1intum"}');
        await waitFor(() => expect(query.result.current.data?.name).toBe('Atlas 2'));
    });

    it('fails with an ApiError', async () => {
        fetchMock.mockResolvedValueOnce(new Response('{}', { status: 409, headers: { 'Content-Type': 'application/json' } }));
        const { wrapper } = setUp();

        const { result } = renderHook(() => useWithdrawRequest(), { wrapper });
        await act(async () => {
            await result.current.mutateAsync({ requestId: '5c0b9a4e-0000-4000-8000-000000000000' }).catch(() => undefined);
        });

        await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiError));
    });
});
