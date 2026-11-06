// What the generated types let a consumer do. The compile test copies this file next to the generated responses
// fixture, so a change that breaks these types fails the build. It is never run.
import { BagKindEnum } from './index';
import type { Bag, Shape } from './index';

/** A union of objects with a single-value discriminator narrows in a switch, and the compiler checks it is exhaustive. */
export function area(shape: Shape): number {
    if (typeof shape === 'string') {
        return 0;
    }
    switch (shape.type) {
        case 'circle':
            return Math.PI * shape.radius ** 2;
        case 'square':
            return shape.side ** 2;
    }
}

/** An enum is a value and a type: the const object names the members, the derived union types them. */
export function isLarge(bag: Bag): boolean {
    const kind: BagKindEnum = bag.kind;
    return kind === BagKindEnum.Large;
}

/** Models are immutable. */
export function renamed(bag: Bag): Bag {
    // @ts-expect-error a property of a model is readonly
    bag.final = true;
    return { ...bag, final: true };
}
