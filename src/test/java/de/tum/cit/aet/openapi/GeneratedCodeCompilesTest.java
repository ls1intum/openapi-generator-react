package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Type-checks the generated code of every fixture under the strictest compiler options a consumer may use, and lints it
 * with the strict and stylistic type-checked rules of typescript-eslint.
 */
class GeneratedCodeCompilesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    private static final Map<String, Consumer<CodegenConfigurator>> OPTION_SETS = Map.of(
            "defaults", configurator -> { },
            "model-affixes", configurator -> configurator.addAdditionalProperty("modelNamePrefix", "Api")
                    .addAdditionalProperty("modelNameSuffix", "Model"),
            "ts-extensions", configurator -> configurator.addAdditionalProperty("importExtension", "ts"),
            "js-extensions", configurator -> configurator.addAdditionalProperty("importExtension", "js"),
            "functions-only", configurator -> configurator.addAdditionalProperty("generateHooks", "false"),
            "use-client", configurator -> configurator.addAdditionalProperty("useClientDirective", "true"));

    @TempDir
    Path tempDir;

    @Test
    void generatedCodeOfEverySpecCompiles() throws IOException, InterruptedException {
        List<Path> specs;
        try (Stream<Path> files = Files.list(FIXTURES)) {
            specs = Stream.concat(files, Stream.of(Path.of("example/example-openapi.yaml")))
                    .filter(file -> file.toString().endsWith(".yaml")).sorted().toList();
        }
        for (Path spec : specs) {
            String name = spec.getFileName().toString().replace("-openapi.yaml", "");
            OPTION_SETS.forEach((options, properties) -> Generation.generate(spec, tempDir.resolve(name).resolve(options), properties));
        }
        // How an application uses the generated example, compiled and linted with it.
        Files.copy(Generation.TYPESCRIPT.resolve("usage.tsx"), tempDir.resolve("example/defaults/usage.tsx"));
        // What the types let a consumer do with a union.
        Files.copy(Generation.TYPESCRIPT.resolve("narrowing.ts"), tempDir.resolve("responses/defaults/narrowing.ts"));
        // What the types of the errors let a consumer do.
        Files.copy(Generation.TYPESCRIPT.resolve("error-types.ts"), tempDir.resolve("errors/defaults/error-types.ts"));
        Generation.linkNodeModules(tempDir);
        Files.writeString(tempDir.resolve("tsconfig.json"), """
                { "extends": "%s", "include": ["**/*.ts", "**/*.tsx"], "exclude": ["node_modules"] }
                """.formatted(Generation.TYPESCRIPT.resolve("tsconfig.json")));

        // TypeScript 6, which the pinned types and the linters of most projects use, and the native TypeScript 7.
        for (String compiler : List.of("typescript", "typescript-7")) {
            NodeProcess.assertSucceeds(tempDir, Map.of(), "Generated code does not compile with " + compiler,
                    Generation.TYPESCRIPT.resolve("node_modules").resolve(compiler).resolve("bin/tsc").toString(), "-p", "tsconfig.json");
        }
        NodeProcess.assertSucceeds(tempDir, Map.of(), "Generated code does not pass the lint rules",
                Generation.TYPESCRIPT.resolve("node_modules/eslint/bin/eslint.js").toString(),
                "--config", Generation.TYPESCRIPT.resolve("eslint.config.mjs").toString(), "--max-warnings", "0", ".");
    }
}
