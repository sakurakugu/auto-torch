package com.sakurakugu.autotorch.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sakurakugu.autotorch.config.ConfigDefinitions;

class ForgeConfigsTest {
    @TempDir
    Path configDirectory;

    @Test
    void createsCompleteDefaults() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        ForgeConfigs.Backend backend = backend(path);

        String toml = read(path);
        assertTrue(toml.contains("[nearbyAutoTorch]"));
        assertTrue(toml.contains("[lightingTaskDefaults]"));
        for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
            assertTrue(toml.contains(leafName(definition.key()) + " ="), definition.key());
        }
        assertFalse(backend.getBoolean("nearbyAutoTorch.enabled", true));
    }

    @Test
    void fillsMissingValuesAndPreservesUnknownValues() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        write(path, "futureValue = 42\n\n[nearbyAutoTorch]\nenabled = true\n");

        ForgeConfigs.Backend backend = backend(path);

        assertTrue(backend.getBoolean("nearbyAutoTorch.enabled", false));
        String toml = read(path);
        assertTrue(toml.contains("futureValue = 42"));
        assertTrue(toml.contains("lightThreshold = 4"));
    }

    @Test
    void repairsWrongTypesAndOutOfRangeIntegers() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        write(path, "[nearbyAutoTorch]\nenabled = \"yes\"\nlightThreshold = 99\n"
                + "\n[lightOverlay]\nhorizontalRange = 1.5\n");

        ForgeConfigs.Backend backend = backend(path);

        assertFalse(backend.getBoolean("nearbyAutoTorch.enabled", true));
        assertEquals(16, backend.getInt("nearbyAutoTorch.lightThreshold", 4));
        assertEquals(16, backend.getInt("lightOverlay.horizontalRange", 16));
    }

    @Test
    void malformedTomlIsNotOverwritten() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        String malformed = "broken = [\n";
        write(path, malformed);

        assertThrows(IllegalStateException.class, () -> backend(path));
        assertEquals(malformed, read(path));
    }

    @Test
    void persistsExplicitChanges() {
        Path path = configDirectory.resolve("autotorch-client.toml");
        ForgeConfigs.Backend backend = backend(path);
        backend.setBoolean("nearbyAutoTorch.enabled", true);
        backend.setInt("nearbyAutoTorch.lightThreshold", 99);
        backend.save();

        ForgeConfigs.Backend reloaded = backend(path);
        assertTrue(reloaded.getBoolean("nearbyAutoTorch.enabled", false));
        assertEquals(16, reloaded.getInt("nearbyAutoTorch.lightThreshold", 4));
    }

    private ForgeConfigs.Backend backend(Path path) {
        ForgeConfigs.Backend backend = new ForgeConfigs.Backend(ConfigDefinitions.CLIENT);
        backend.load(path.toFile(), configDirectory.resolve("missing.cfg").toFile());
        return backend;
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void write(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String leafName(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
