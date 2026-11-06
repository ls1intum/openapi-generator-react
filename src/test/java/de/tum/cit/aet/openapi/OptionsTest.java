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

    @Test
    void withoutHooksOnlyTheFunctionsAreGenerated() throws IOException {
        Generation.generate(EXAMPLE, tempDir.resolve("plain"), configurator -> configurator.addAdditionalProperty("generateHooks", "false"));

        assertFalse(Files.exists(tempDir.resolve("plain/api/projects-hooks.ts")));
        assertFalse(Files.exists(tempDir.resolve("plain/api/hook-options.ts")));
        assertTrue(Files.exists(tempDir.resolve("plain/api/projects-api.ts")));
        try (var files = Files.walk(tempDir.resolve("plain"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".ts")).toList()) {
                assertFalse(Files.readString(file).contains("tanstack"), file + " must not need TanStack Query");
                assertFalse(Files.readString(file).contains("hook"), file + " must not mention hooks");
            }
        }
    }

    @Test
    void importsCarryTheExtensionThatWasAskedFor() throws IOException {
        Generation.generate(EXAMPLE, tempDir.resolve("ts"), configurator -> configurator.addAdditionalProperty("importExtension", "ts"));

        assertTrue(Files.readString(tempDir.resolve("ts/api/projects-api.ts")).contains("from '../models/project.ts';"));
        assertTrue(Files.readString(tempDir.resolve("ts/index.ts")).contains("export * from './api/index.ts';"));
        assertTrue(Files.readString(tempDir.resolve("ts/api/index.ts")).contains("export * from './projects-hooks.ts';"));
    }

    @Test
    void anUnknownImportExtensionFails() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> Generation.generate(EXAMPLE, tempDir.resolve("bad"), configurator -> configurator.addAdditionalProperty("importExtension", "mjs")));
    }
}
