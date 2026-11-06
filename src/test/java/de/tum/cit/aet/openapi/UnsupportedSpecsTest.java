package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the generator cannot produce correctly fails with a message that names the operation, instead of wrong code. */
class UnsupportedSpecsTest {

    @TempDir
    Path tempDir;

    @Test
    void aMultipartBodyFails() {
        assertFails("multipart", "upload", "form or multipart");
    }

    @Test
    void aBodyThatIsNotJsonFails() {
        assertFails("text-body", "putNote", "not JSON");
    }

    @Test
    void aCookieParameterFails() {
        assertFails("cookie", "getMe", "Cookie parameters");
    }

    @Test
    void aQueryStyleOpenApiDoesNotAllowFails() {
        assertFails("query-simple-style", "listThings", "style simple");
    }

    @Test
    void anArgumentsTypeThatClashesWithASchemaFails() {
        assertFails("params-name-clash", "createProject", "CreateProjectParams");
    }

    private void assertFails(String spec, String operation, String reason) {
        Throwable failure = assertThrows(Throwable.class,
                () -> Generation.generate(Path.of("src/test/resources/invalid/" + spec + "-openapi.yaml"), tempDir));
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains(operation), () -> "The message does not name " + operation + ":\n" + messages);
        assertTrue(messages.toString().contains(reason), () -> "The message does not say " + reason + ":\n" + messages);
    }
}
