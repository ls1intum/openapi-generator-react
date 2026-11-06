// What the types of the errors let a consumer do. The compile test copies this file next to the generated errors
// fixture, so a change that breaks these types fails the build. It is never run.
import { createThing, getThing, isApiError, isCreateThingError, isGetThingError } from './index';
import type { ApiError, ArchiveThingError, CreateThingError, GetOnlyDefaultError, GetPlainError, GetThingError, Problem } from './index';

/** One declared error body is typed; the response may still have a text body or none. */
export async function title(): Promise<string | undefined> {
    try {
        await getThing({ id: 'a' });
    } catch (caught) {
        if (isGetThingError(caught)) {
            const body = caught.body;
            return typeof body === 'object' ? body.title : body;
        }
    }
    return undefined;
}

/** Several declared bodies make a union that is told apart by what only one of them has. */
export async function problems(): Promise<number> {
    try {
        await createThing({ body: { id: 'a' } });
    } catch (caught) {
        if (isCreateThingError(caught) && typeof caught.body === 'object') {
            return 'errors' in caught.body ? caught.body.errors.length : caught.body.status;
        }
    }
    return 0;
}

/** Any error from the client narrows to ApiError, with a body of an unknown type. */
export function unknownBody(caught: unknown): unknown {
    return isApiError(caught) ? caught.body : undefined;
}

/** A default response is the declared body of the errors when the operation has a success response. */
export function defaultBody(error: ArchiveThingError): readonly Problem[] | string | undefined {
    return error.body;
}

/** Without a declared error body, the error is a plain ApiError. */
export function undeclared(plain: GetPlainError, onlyDefault: GetOnlyDefaultError): ApiError[] {
    return [plain, onlyDefault];
}

/** The error type of an operation is as wide as what the operation declares: a createThing error may be a validation problem. */
export function distinct(error: CreateThingError): GetThingError {
    // @ts-expect-error a createThing error may carry a validation problem, which a getThing error cannot
    return error;
}
