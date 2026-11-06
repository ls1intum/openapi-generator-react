import { defineConfig } from 'vitest/config';

// The Java tests generate the clients into a directory and pass it in GENERATED_ROOT.
const generatedRoot = process.env['GENERATED_ROOT'];
if (generatedRoot === undefined) {
    throw new Error('GENERATED_ROOT is not set. Run the tests with ./gradlew test, which generates the clients first.');
}

export default defineConfig({
    resolve: { alias: { '@generated': generatedRoot } },
    server: { fs: { strict: false } },
    test: {
        environment: 'jsdom',
        include: ['*.test.ts', '*.test.tsx'],
    },
});
