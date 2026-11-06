// The lint rules that the generated code has to pass: typescript-eslint's strict and stylistic type-checked sets.
// The tests run it from the directory of the generated code, which holds the tsconfig.json.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    { ignores: ['node_modules'] },
    js.configs.recommended,
    ...tseslint.configs.strictTypeChecked,
    ...tseslint.configs.stylisticTypeChecked,
    { languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: process.cwd() } } },
);
