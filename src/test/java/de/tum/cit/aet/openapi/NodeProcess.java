package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class NodeProcess {

    private NodeProcess() {
    }

    /**
     * Runs {@code node} with the arguments and fails the test unless it exits with 0 within three minutes. The output
     * goes to a file, so a process that hangs cannot block the test, and it is killed.
     */
    static void assertSucceeds(Path workingDirectory, Map<String, String> environment, String failure, String... arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("node"));
        command.addAll(List.of(arguments));
        Path log = Files.createTempFile("node", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            builder.environment().putAll(environment);
            Process node = builder.start();
            boolean finished = node.waitFor(3, TimeUnit.MINUTES);
            if (!finished) {
                node.destroyForcibly().waitFor();
            }
            String output = Files.readString(log);
            assertTrue(finished, () -> "node did not finish within three minutes:\n" + output);
            assertEquals(0, node.exitValue(), () -> failure + ":\n" + output);
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
