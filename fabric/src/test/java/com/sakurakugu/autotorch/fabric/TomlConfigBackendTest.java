package com.sakurakugu.autotorch.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // 构造后端即写出默认配置；这里只需要它们在断言后关闭以落盘，
        // 用 try/finally 而不是 try-with-resources，避免“资源未被引用”的编译告警。
        TomlConfigBackend client = new TomlConfigBackend(clientPath, ConfigDefinitions.CLIENT);
        TomlConfigBackend server = new TomlConfigBackend(serverPath, ConfigDefinitions.SERVER);
        try {
            assertTrue(Files.exists(clientPath));
            assertTrue(Files.exists(serverPath));
        } finally {
            client.close();
            server.close();
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

    @Test
    void writesChineseCommentsForEveryKey() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertTrue(Files.exists(path));
        }

        List<String> lines = Files.readAllLines(path);
        Map<String, Integer> indexes = keyLineIndexes(lines);
        for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
            Integer index = indexes.get(definition.key());
            assertNotNull(index, definition.key());
            // 说明行紧贴在键上方，顺序是用途说明在前、默认值与范围在后。
            String[] expected = definition.fullCommentLines();
            for (int offset = 1; offset <= expected.length; offset++) {
                assertEquals("#" + expected[expected.length - offset],
                        lines.get(index - offset).trim(), definition.key());
            }
        }
    }

    @Test
    void addsCommentsToExistingFileWithoutChangingValues() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        Files.write(path, "futureValue = 42\n\n[nearbyAutoTorch]\nenabled = true\nlightThreshold = 9\n"
                .getBytes(StandardCharsets.UTF_8));

        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertTrue(backend.getBoolean("nearbyAutoTorch.enabled", false));
            assertEquals(9, backend.getInt("nearbyAutoTorch.lightThreshold", 4));
        }

        List<String> lines = Files.readAllLines(path);
        Map<String, Integer> indexes = keyLineIndexes(lines);
        // 玩家已改的值与未知键都要保留。
        assertEquals("futureValue = 42", lines.get(0).trim());
        assertEquals("enabled = true", lines.get(indexes.get("nearbyAutoTorch.enabled")).trim());
        assertEquals("lightThreshold = 9", lines.get(indexes.get("nearbyAutoTorch.lightThreshold")).trim());
        // 说明只写给已知配置键，未知键不会被加上说明。
        int expectedComments = 0;
        for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
            expectedComments += definition.fullCommentLines().length;
        }
        int actualComments = 0;
        for (String line : lines) {
            if (line.trim().startsWith("#")) {
                actualComments++;
            }
        }
        assertEquals(expectedComments, actualComments);
    }

    @Test
    void keepsCommentsStableAcrossReloads() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertEquals(4, backend.getInt("nearbyAutoTorch.lightThreshold", 0));
        }
        String first = String.join("\n", Files.readAllLines(path));

        // 说明写过之后再次加载，文件内容不应再变化。
        try (TomlConfigBackend backend = new TomlConfigBackend(path, ConfigDefinitions.CLIENT)) {
            assertEquals(4, backend.getInt("nearbyAutoTorch.lightThreshold", 0));
        }

        assertEquals(first, String.join("\n", Files.readAllLines(path)));
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

    /** 按 [表名.键名] 找出每个配置键所在的行号。 */
    private static Map<String, Integer> keyLineIndexes(List<String> lines) {
        Map<String, Integer> indexes = new HashMap<>();
        String section = "";
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
            } else if (line.contains(" = ")) {
                indexes.put(section + "." + line.substring(0, line.indexOf(" = ")), index);
            }
        }
        return indexes;
    }
}
