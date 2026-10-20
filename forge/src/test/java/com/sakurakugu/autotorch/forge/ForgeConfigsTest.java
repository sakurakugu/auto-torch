package com.sakurakugu.autotorch.forge;

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
    void createsCompleteServerDefaults() throws IOException {
        Path path = configDirectory.resolve("autotorch-server.toml");
        ForgeConfigs.Backend backend = serverBackend(path);

        String toml = read(path);
        assertTrue(toml.contains("[limits]"));
        assertTrue(toml.contains("[performance]"));
        for (ConfigDefinitions.Value definition : ConfigDefinitions.SERVER) {
            assertTrue(toml.contains(leafName(definition.key()) + " ="), definition.key());
        }
        assertTrue(backend.getBoolean("lightingTask.enabled", false));
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

    /** 浮点与文本是 1.13.2 新增的配置类型，1.9.4 的 TOML 后端也要能读写与修复。 */
    @Test
    void readsAndWritesDecimalAndStringValues() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        ForgeConfigs.Backend backend = backend(path);

        assertEquals(2.5, backend.getDouble("lightOverlay.nearLineWidth", -1));
        assertEquals("FF3030", backend.getString("lightOverlay.alwaysRiskColor", null));
        assertEquals("minecraft:wooden_axe", backend.getString("selectionOverlay.toolItem", null));

        backend.setDouble("lightOverlay.nearLineWidth", 3.25);
        backend.setString("lightOverlay.alwaysRiskColor", "aBc123");
        backend.setString("selectionOverlay.toolItem", "minecraft:stick");
        backend.save();

        ForgeConfigs.Backend reloaded = backend(path);
        assertEquals(3.25, reloaded.getDouble("lightOverlay.nearLineWidth", -1));
        assertEquals("aBc123", reloaded.getString("lightOverlay.alwaysRiskColor", null));
        assertEquals("minecraft:stick", reloaded.getString("selectionOverlay.toolItem", null));
    }

    @Test
    void repairsInvalidDecimalAndStringValues() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        write(path, "[lightOverlay]\nnearLineWidth = 99.0\nfarLineWidth = \"oops\"\n"
                + "alwaysRiskColor = \"zzzzzz\"\n\n[nearbyAutoTorch]\nitem = 42\n");

        ForgeConfigs.Backend backend = backend(path);

        // 超范围浮点按定义裁剪；类型错误与非法颜色回退默认值。
        assertEquals(8.0, backend.getDouble("lightOverlay.nearLineWidth", -1));
        assertEquals(0.75, backend.getDouble("lightOverlay.farLineWidth", -1));
        assertEquals("FF3030", backend.getString("lightOverlay.alwaysRiskColor", null));
        assertEquals("minecraft:torch", backend.getString("nearbyAutoTorch.item", null));
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

    @Test
    void writesChineseCommentsForEveryKey() throws IOException {
        Path path = configDirectory.resolve("autotorch-client.toml");
        backend(path);

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
        write(path, "futureValue = 42\n\n[nearbyAutoTorch]\nenabled = true\nlightThreshold = 9\n");

        ForgeConfigs.Backend backend = backend(path);

        assertTrue(backend.getBoolean("nearbyAutoTorch.enabled", false));
        assertEquals(9, backend.getInt("nearbyAutoTorch.lightThreshold", 4));

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
        backend(path);
        String first = read(path);

        // 说明写过之后再次加载，文件内容不应再变化。
        backend(path);

        assertEquals(first, read(path));
    }

    private ForgeConfigs.Backend backend(Path path) {
        ForgeConfigs.Backend backend = new ForgeConfigs.Backend(ConfigDefinitions.CLIENT);
        backend.load(path.toFile(), configDirectory.resolve("missing.cfg").toFile());
        return backend;
    }

    private ForgeConfigs.Backend serverBackend(Path path) {
        ForgeConfigs.Backend backend = new ForgeConfigs.Backend(ConfigDefinitions.SERVER);
        backend.load(path.toFile(), configDirectory.resolve("missing.cfg").toFile());
        return backend;
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
