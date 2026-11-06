package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the generated client against a mocked {@code fetch}, in Vitest with jsdom: requests, errors, hooks and query keys. */
class GeneratedClientTest {

    @TempDir
    Path tempDir;

    @Test
    void generatedClientBehavesAsDocumented() throws IOException, InterruptedException {
        Generation.generate(Path.of("example/example-openapi.yaml"), tempDir.resolve("example"));
        Generation.generate(Path.of("src/test/resources/fixtures/parameters-openapi.yaml"), tempDir.resolve("parameters"));
        Generation.linkNodeModules(tempDir);

        NodeProcess.assertSucceeds(Generation.TYPESCRIPT, Map.of("GENERATED_ROOT", tempDir.toString()),
                "The generated client does not behave as documented",
                "node_modules/vitest/vitest.mjs", "run");
    }
}
