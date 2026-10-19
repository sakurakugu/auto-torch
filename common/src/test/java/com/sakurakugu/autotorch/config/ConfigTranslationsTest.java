package com.sakurakugu.autotorch.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校验语言文件：键集合一致、每个客户端配置都有标签与提示、英文不含中文。 */
class ConfigTranslationsTest {
    private static final String LANG_PATH = "/assets/autotorch/lang/";
    private static final String CHINESE = "zh_cn";
    private static final String ENGLISH = "en_us";

    @Test
    void languageFilesShareTheSameKeys() {
        assertEquals(load(CHINESE).keySet(), load(ENGLISH).keySet());
    }

    @Test
    void chineseTranslationsMatchConfigComments() {
        Map<String, String> chinese = load(CHINESE);
        for (ConfigDefinitions.Value definition : allDefinitions()) {
            // 中文说明以配置文件注释为准，语言文件只做同一份文本的镜像，避免两边各自维护。
            assertEquals(definition.comment(), chinese.get(definition.translationKey()), definition.key());
        }
    }

    @Test
    void everySettingHasLabelAndTooltipInBothLanguages() {
        verifyTranslations(CHINESE);
        verifyTranslations(ENGLISH);
    }

    @Test
    void configTranslationKeysMatchConfigDefinitions() {
        Set<String> expected = allDefinitions().stream()
                .map(ConfigDefinitions.Value::translationKey)
                .collect(Collectors.toSet());
        for (String language : java.util.Arrays.asList(CHINESE, ENGLISH)) {
            Set<String> actual = load(language).keySet().stream()
                    .filter(key -> key.startsWith("config.autotorch."))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, language);
        }
    }

    @Test
    void rangeTooltipTranslationsExistInBothLanguages() {
        for (String language : java.util.Arrays.asList(CHINESE, ENGLISH)) {
            Map<String, String> translations = load(language);
            for (String key : java.util.Arrays.asList("screen.autotorch.config_range", "screen.autotorch.config_range_step")) {
                assertTrue(translations.containsKey(key), language + ": " + key);
            }
        }
    }

    @Test
    void englishTranslationsContainNoHanCharacters() {
        for (Map.Entry<String, String> entry : load(ENGLISH).entrySet()) {
            String text = entry.getValue();
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                assertFalse(character >= 0x4E00 && character <= 0x9FFF,
                        () -> "英文语言文件里仍有中文：" + entry.getKey());
            }
        }
    }

    private static void verifyTranslations(String language) {
        Map<String, String> translations = load(language);
        for (ConfigDefinitions.Value definition : allDefinitions()) {
            String text = translations.get(definition.translationKey());
            assertNotNull(text, language + " 缺少配置说明：" + definition.key());
            assertFalse(text.trim().isEmpty(), language + " 配置说明为空：" + definition.key());
            // 界面按首行取行标签、其余行取悬停提示，因此不允许空行与首尾空白。
            for (String line : text.split("\n", -1)) {
                assertFalse(line.trim().isEmpty(), language + " 配置说明含空行：" + definition.key());
                assertEquals(line.trim(), line, language + " 配置说明首尾有空白：" + definition.key());
            }
        }
    }

    /** 客户端与服务端配置共用一套翻译键，测试同时覆盖两者。 */
    private static List<ConfigDefinitions.Value> allDefinitions() {
        List<ConfigDefinitions.Value> definitions = new ArrayList<>(ConfigDefinitions.CLIENT);
        definitions.addAll(ConfigDefinitions.SERVER);
        return definitions;
    }

    private static Map<String, String> load(String language) {
        String path = LANG_PATH + language + ".json";
        try (InputStream stream = ConfigTranslationsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "找不到语言文件：" + path);
            JsonObject json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> translations = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
            return translations;
        } catch (IOException exception) {
            throw new AssertionError("无法读取语言文件：" + path, exception);
        }
    }
}
