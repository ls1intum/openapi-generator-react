package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/** Generates a client from a spec for the tests, and gives the generated code the pinned node_modules. */
final class Generation {

    static final Path TYPESCRIPT = Path.of("src/test/typescript").toAbsolutePath();

    private Generation() {
    }

    static void generate(Path spec, Path outputDir) {
        generate(spec, outputDir, configurator -> { });
    }

    static void generate(Path spec, Path outputDir, Consumer<CodegenConfigurator> options) {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(ReactQueryGenerator.GENERATOR_NAME)
                .setInputSpec(spec.toAbsolutePath().toString())
                .setOutputDir(outputDir.toString());
        options.accept(configurator);
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }

    /** Lets the generated code below {@code root} resolve React and TanStack Query from the pinned versions. */
    static void linkNodeModules(Path root) throws IOException {
        Files.createSymbolicLink(root.resolve("node_modules"), TYPESCRIPT.resolve("node_modules"));
    }
}
