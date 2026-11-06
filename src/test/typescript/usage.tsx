// How a React application uses the generated example. The compile test copies this file next to the generated code,
// so a change that breaks these types fails the build. It is never run.
import { skipToken, useQueryClient } from '@tanstack/react-query';
import {
    getProjectQueryOptions,
    listProjectsQueryKey,
    useCreateProject,
    useGetMe,
    useGetProject,
    useListProjects,
    useSuspenseGetProject,
    useWithdrawRequest,
} from './index';
import type { Person, Project } from './index';

export function Usage({ projectId }: { projectId: string | undefined }) {
    // The data keeps its type, or the type that select returns.
    const project = useGetProject({ projectId: projectId ?? '' });
    const name: string | undefined = project.data?.name;
    const count = useListProjects({}, { select: (projects) => projects.length });
    const total: number | undefined = count.data;
    const me = useGetMe();
    const person: Person | undefined = me.data;

    // Waits for the argument without a placeholder.
    const waiting = useGetProject(projectId === undefined ? skipToken : { projectId });
    const waitingData: Project | undefined = waiting.data;

    // Suspends, so the data is always there.
    const suspended = useSuspenseGetProject({ projectId: 'atlas' });
    const definite: Project = suspended.data;

    // Prefetching and cache access through the options and the keys.
    const queryClient = useQueryClient();
    void queryClient.query(getProjectQueryOptions({ projectId: 'atlas' }));
    queryClient.setQueryData(listProjectsQueryKey(), []);

    // A mutation takes its arguments once, in mutate.
    const create = useCreateProject({ onSuccess: (created) => created.id });
    create.mutate({ body: { name: 'Atlas', organization: 'ls1intum' } });
    const withdraw = useWithdrawRequest();
    withdraw.mutate({ requestId: '1' });

    return [name, total, person, waitingData, definite];
}
