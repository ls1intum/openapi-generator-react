package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionsTest {

    private static final Path EXAMPLE = Path.of("example/example-openapi.yaml");

    @TempDir
    Path tempDir;

    @Test
    void theHooksFilesStartWithUseClientOnlyWhenAsked() throws IOException {
        Generation.generate(EXAMPLE, tempDir.resolve("off"));
        Generation.generate(EXAMPLE, tempDir.resolve("on"), configurator -> configurator.addAdditionalProperty("useClientDirective", "true"));

        assertFalse(Files.readString(tempDir.resolve("off/api/projects-hooks.ts")).contains("'use client'"));
        String hooks = Files.readString(tempDir.resolve("on/api/projects-hooks.ts"));
        assertTrue(hooks.contains("*/\n\n'use client';\n\nimport "), "The directive comes before the imports and after the header");
        assertFalse(Files.readString(tempDir.resolve("on/api/projects-api.ts")).contains("'use client'"), "The plain functions need no directive");
    }
}
