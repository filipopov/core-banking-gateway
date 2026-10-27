package io.github.filipopov.gateway.app;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the shared WireMock mappings in {@code mocks/<provider>} at the repository root. */
final class MockFiles {

    private MockFiles() {
    }

    static String dir(String provider) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("mocks").resolve(provider);
            if (Files.isDirectory(candidate.resolve("mappings"))) {
                return candidate.toString();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find mocks/" + provider + " above " + Path.of("").toAbsolutePath());
    }
}
