package com.sakurakugu.autotorch.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sakurakugu.autotorch.config.ConfigDefinitions;

class TomlConfigBackendTest {
    @TempDir
    Path configDirectory;

    @Test
    void createsCompleteClientAndServerDefaults() throws IOException {
        Path clientPath = configDirectory.resolve("autotorch-client.toml");
        Path serverPath = configDirectory.resolve("autotorch-server.toml");

        try (TomlConfigBackend ignoredClient = new TomlConfigBackend(clientPath, ConfigDefinitions.CLIENT);
                TomlConfigBackend ignoredServer = new TomlConfigBackend(serverPath, ConfigDefinitions.SERVER)) {
            assertTrue(Files.exists(clientPath));
            assertTrue(Files.exists(serverPath));
        }

        String clientToml = read(clientPath);
        String serverToml = read(serverPath);
        assertTrue(clientToml.contains("[nearbyAutoTorch]"));
        assertTrue(clientToml.contains("[lightingTaskDefaults]"));
        assertTrue(serverToml.contains("[limits]"));
        assertTrue(serverToml.contains("[performance]"));
        for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
            assertTrue(clientToml.contains(leafName(definition.key()) + " ="), definition.key());
        }
        for (ConfigDefinitions.Value definition : ConfigDefinitions.SERVER) {
            assertTrue(serverToml.contains(leafName(definition.key()) + " ="), definition.key());
        }
    }

    @Test
    void fillsMissingValuesAndPreservesUnknownValues() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        write(path, "futureValue = 42\n\n[nearbyAutoTorch]\nenabled = true\n");

        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertTrue(backend.getBoolean("nearbyAutoTorch.enabled", false));
        }

        String toml = read(path);
        assertTrue(toml.contains("futureValue = 42"));
        assertTrue(toml.contains("lightThreshold = 4"));
    }

    @Test
    void repairsWrongTypesAndOutOfRangeIntegers() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        write(path, "[nearbyAutoTorch]\n"
                + "enabled = \"yes\"\n"
                + "lightThreshold = 99\n\n"
                + "[lightOverlay]\n"
                + "horizontalRange = 1.5\n");

        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertFalse(backend.getBoolean("nearbyAutoTorch.enabled", true));
            assertEquals(16, backend.getInt("nearbyAutoTorch.lightThreshold", 4));
            assertEquals(16, backend.getInt("lightOverlay.horizontalRange", 16));
        }

        String toml = read(path);
        assertTrue(toml.contains("enabled = false"));
        assertTrue(toml.contains("lightThreshold = 16"));
        assertTrue(toml.contains("horizontalRange = 16"));
    }

    @Test
    void malformedTomlIsNotOverwritten() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        String malformed = "broken = [\n";
        write(path, malformed);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new TomlConfigBackend(path, ConfigDefinitions.CLIENT));

        assertTrue(exception.getMessage().contains(path.toString()));
        assertEquals(malformed, read(path));
    }

    @Test
    void ignoresLegacyPropertiesAndLeavesThemUntouched() throws IOException {
        Path legacyPath = configDirectory.resolve("autotorch-client.properties");
        Path tomlPath = configDirectory.resolve("autotorch-client.toml");
        String legacy = "nearbyAutoTorch.enabled=true\n";
        write(legacyPath, legacy);

        try (TomlConfigBackend backend = new TomlConfigBackend(tomlPath, ConfigDefinitions.CLIENT)) {
            assertFalse(backend.getBoolean("nearbyAutoTorch.enabled", true));
        }

        write(legacyPath, "nearbyAutoTorch.enabled=true\n");
        try (TomlConfigBackend backend = new TomlConfigBackend(tomlPath, ConfigDefinitions.CLIENT)) {
            assertFalse(backend.getBoolean("nearbyAutoTorch.enabled", true));
        }

        assertEquals(legacy, read(legacyPath));
        assertTrue(Files.exists(tomlPath));
    }

    @Test
    void persistsExplicitChanges() {
        Path path = configDirectory.resolve("autotorch-client.toml");
        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            backend.setBoolean("nearbyAutoTorch.enabled", true);
            backend.setInt("nearbyAutoTorch.lightThreshold", 99);
        }

        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertTrue(backend.getBoolean("nearbyAutoTorch.enabled", false));
            assertEquals(16, backend.getInt("nearbyAutoTorch.lightThreshold", 4));
        }
    }

    private static String leafName(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void write(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
