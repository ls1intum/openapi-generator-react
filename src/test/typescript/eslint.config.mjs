// The lint rules that the generated code has to pass: typescript-eslint's strict and stylistic type-checked sets, and the
// rules of React's hooks and of the React Compiler.
// The tests run it from the directory of the generated code, which holds the tsconfig.json.
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    { ignores: ['node_modules'] },
    js.configs.recommended,
    ...tseslint.configs.strictTypeChecked,
    ...tseslint.configs.stylisticTypeChecked,
    reactHooks.configs.flat.recommended,
    { languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: process.cwd() } } },
);
