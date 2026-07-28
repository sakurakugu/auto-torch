package com.sakurakugu.autotorch.forge;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Forge 1.7.10 的 TOML 配置后端。 */
final class ForgeConfigs {
    static final Backend CLIENT = new Backend(ConfigDefinitions.CLIENT);
    static final Backend SERVER = new Backend(ConfigDefinitions.SERVER);

    private ForgeConfigs() {}

    static void init(File configDir) {
        CLIENT.load(new File(configDir, "autotorch-client.toml"),
                new File(configDir, "autotorch-client.cfg"));
        SERVER.load(new File(configDir, "autotorch-server.toml"),
                new File(configDir, "autotorch-server.cfg"));
    }

    static final class Backend implements ConfigBackend {
        private static final Logger LOGGER = LogManager.getLogger(Backend.class);

        private final List<Value> definitions;
        private final Map<String, Value> definitionIndex = new HashMap<>();
        private CommentedFileConfig config;
        private File file;

        Backend(List<Value> definitions) {
            this.definitions = definitions;
            for (Value definition : definitions) {
                if (definitionIndex.put(definition.key(), definition) != null) {
                    throw new IllegalArgumentException("Duplicate configuration key: " + definition.key());
                }
            }
        }

        void load(File tomlFile, File legacyFile) {
            file = tomlFile;
            boolean tomlExisted = tomlFile.isFile();
            Map<String, Object> legacyValues = tomlExisted
                    ? new LinkedHashMap<String, Object>() : readLegacyValues(legacyFile);
            config = CommentedFileConfig.builder(tomlFile, TomlFormat.instance())
                    .sync()
                    .preserveInsertionOrder()
                    .build();
            try {
                config.load();
                boolean changed = !tomlExisted;
                for (Value definition : definitions) {
                    String key = definition.key();
                    Object raw = config.getRaw(key);
                    if (raw == null && legacyValues.containsKey(key)) {
                        raw = legacyValues.get(key);
                        config.set(key, raw);
                        changed = true;
                    }
                    changed |= repair(definition, raw);
                }
                if (changed) save();
            } catch (RuntimeException exception) {
                config.close();
                config = null;
                throw new IllegalStateException("Cannot read TOML configuration " + tomlFile, exception);
            }
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            Object value = raw(key);
            if (value instanceof Boolean) return (Boolean) value;
            Value definition = definitionIndex.get(key);
            boolean repaired = definition instanceof BooleanValue
                    ? ((BooleanValue) definition).defaultValue() : fallback;
            warnInvalidType(key, value, "boolean", repaired);
            config.set(key, repaired);
            save();
            return repaired;
        }

        @Override
        public int getInt(String key, int fallback) {
            Object value = raw(key);
            Integer parsed = asInteger(value);
            Value definition = definitionIndex.get(key);
            IntValue intDefinition = definition instanceof IntValue ? (IntValue) definition : null;
            if (parsed == null) {
                int repaired = intDefinition == null ? fallback : intDefinition.defaultValue();
                warnInvalidType(key, value, "integer", repaired);
                config.set(key, repaired);
                save();
                return repaired;
            }
            if (intDefinition == null) return parsed;
            int repaired = intDefinition.clamp(parsed);
            if (repaired != parsed) {
                warnOutOfRange(key, intDefinition, parsed, repaired);
                config.set(key, repaired);
                save();
            }
            return repaired;
        }

        @Override
        public void setBoolean(String key, boolean value) {
            requireLoaded();
            config.set(key, value);
        }

        @Override
        public void setInt(String key, int value) {
            requireLoaded();
            Value definition = definitionIndex.get(key);
            IntValue intDefinition = definition instanceof IntValue ? (IntValue) definition : null;
            int repaired = intDefinition == null ? value : intDefinition.clamp(value);
            if (repaired != value) warnOutOfRange(key, intDefinition, value, repaired);
            config.set(key, repaired);
        }

        @Override
        public void save() {
            requireLoaded();
            try {
                config.save();
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Cannot write TOML configuration " + file, exception);
            }
        }

        private Object raw(String key) {
            requireLoaded();
            return config.getRaw(key);
        }

        private boolean repair(Value definition, Object raw) {
            if (definition instanceof BooleanValue) {
                if (raw instanceof Boolean) return false;
                boolean repaired = ((BooleanValue) definition).defaultValue();
                if (raw != null) warnInvalidType(definition.key(), raw, "boolean", repaired);
                config.set(definition.key(), repaired);
                return true;
            }
            IntValue intDefinition = (IntValue) definition;
            Integer parsed = asInteger(raw);
            int repaired = parsed == null ? intDefinition.defaultValue() : intDefinition.clamp(parsed);
            if (parsed == null && raw != null) {
                warnInvalidType(definition.key(), raw, "integer", repaired);
            } else if (parsed != null && repaired != parsed) {
                warnOutOfRange(definition.key(), intDefinition, parsed, repaired);
            }
            if (parsed == null || repaired != parsed) {
                config.set(definition.key(), repaired);
                return true;
            }
            return false;
        }

        private Map<String, Object> readLegacyValues(File legacyFile) {
            Map<String, Object> values = new LinkedHashMap<>();
            if (!legacyFile.isFile()) return values;
            Configuration legacy = new Configuration(legacyFile);
            legacy.load();
            for (Value definition : definitions) {
                String[] parts = definition.key().split("\\.", 2);
                String category = parts[0];
                String name = parts.length == 1 ? parts[0] : parts[1];
                if (definition instanceof BooleanValue) {
                    boolean fallback = ((BooleanValue) definition).defaultValue();
                    values.put(definition.key(), legacy.get(category, name, fallback).getBoolean(fallback));
                } else {
                    IntValue intDefinition = (IntValue) definition;
                    int value = legacy.get(category, name, intDefinition.defaultValue(), "",
                            intDefinition.minValue(), intDefinition.maxValue()).getInt(intDefinition.defaultValue());
                    values.put(definition.key(), intDefinition.clamp(value));
                }
            }
            LOGGER.info("Migrating legacy configuration {} to {}", legacyFile, file);
            return values;
        }

        private void warnInvalidType(String key, Object value, String expectedType, Object fallback) {
            String actualType = value == null ? "missing" : value.getClass().getSimpleName();
            LOGGER.warn("Configuration {} field {} must be {}, but was {}; corrected to {}",
                    file, key, expectedType, actualType, fallback);
        }

        private void warnOutOfRange(String key, IntValue definition, int value, int repaired) {
            LOGGER.warn("Configuration {} field {} is outside [{}, {}]; corrected from {} to {}",
                    file, key, definition.minValue(), definition.maxValue(), value, repaired);
        }

        private void requireLoaded() {
            if (config == null) throw new IllegalStateException("Configuration is not loaded");
        }

        private static Integer asInteger(Object value) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
                return ((Number) value).intValue();
            }
            if (value instanceof Long) {
                long longValue = (Long) value;
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                }
            }
            return null;
        }
    }
}
