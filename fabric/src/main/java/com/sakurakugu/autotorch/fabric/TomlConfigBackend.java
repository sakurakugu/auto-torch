package com.sakurakugu.autotorch.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;

final class TomlConfigBackend implements ConfigBackend, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(TomlConfigBackend.class.getName());

    private final Path path;
    private final CommentedFileConfig config;
    private final List<Value> definitions;
    private final Map<String, Value> definitionIndex;
    private boolean closed;

    TomlConfigBackend(Path path, List<Value> definitions) {
        this.path = path;
        this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
        this.definitionIndex = indexDefinitions(this.definitions);
        this.config = CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();

        try {
            config.load();
        } catch (RuntimeException exception) {
            config.close();
            throw new IllegalStateException("Cannot read TOML configuration " + path, exception);
        }
        try {
            if (repairKnownValues()) {
                save();
            }
        } catch (RuntimeException exception) {
            config.close();
            throw exception;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        Object value = config.getRaw(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        Value knownDefinition = definitionIndex.get(key);
        BooleanValue definition = knownDefinition instanceof BooleanValue
                ? (BooleanValue) knownDefinition : new BooleanValue(key, fallback);
        warnInvalidType(key, value, "boolean", definition.defaultValue());
        config.set(key, definition.defaultValue());
        save();
        return definition.defaultValue();
    }

    @Override
    public int getInt(String key, int fallback) {
        Value knownDefinition = definitionIndex.get(key);
        IntValue definition = knownDefinition instanceof IntValue ? (IntValue) knownDefinition : null;
        Object value = config.getRaw(key);
        Integer integerValue = asInteger(value);
        if (integerValue == null) {
            int repaired = definition == null ? fallback : definition.defaultValue();
            warnInvalidType(key, value, "integer", repaired);
            config.set(key, repaired);
            save();
            return repaired;
        }
        if (definition == null) {
            return integerValue;
        }
        int clamped = definition.clamp(integerValue);
        if (clamped != integerValue) {
            LOGGER.log(Level.WARNING,
                    "Configuration {0} field {1} is outside [{2}, {3}]; corrected from {4} to {5}",
                    new Object[] { path, key, definition.minValue(), definition.maxValue(), integerValue, clamped });
            config.set(key, clamped);
            save();
        }
        return clamped;
    }

    @Override
    public void setBoolean(String key, boolean value) {
        config.set(key, value);
    }

    @Override
    public void setInt(String key, int value) {
        Value knownDefinition = definitionIndex.get(key);
        IntValue definition = knownDefinition instanceof IntValue ? (IntValue) knownDefinition : null;
        int corrected = definition == null ? value : definition.clamp(value);
        if (corrected != value) {
            LOGGER.log(Level.WARNING,
                    "Configuration {0} field {1} is outside [{2}, {3}]; corrected from {4} to {5}",
                    new Object[] { path, key, definition.minValue(), definition.maxValue(), value, corrected });
        }
        config.set(key, corrected);
    }

    @Override
    public void save() {
        try {
            config.save();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot write TOML configuration " + path, exception);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                save();
            } finally {
                closed = true;
                config.close();
            }
        }
    }

    private boolean repairKnownValues() {
        boolean[] changed = { !Files.exists(path) };
        config.bulkUpdate(values -> {
            for (Value definition : definitions) {
                Object raw = values.getRaw(definition.key());
                if (definition instanceof BooleanValue) {
                    BooleanValue booleanValue = (BooleanValue) definition;
                    if (!(raw instanceof Boolean)) {
                        if (raw != null) {
                            warnInvalidType(definition.key(), raw, "boolean", booleanValue.defaultValue());
                        }
                        values.set(definition.key(), booleanValue.defaultValue());
                        changed[0] = true;
                    }
                } else if (definition instanceof IntValue) {
                    IntValue intValue = (IntValue) definition;
                    Integer parsed = asInteger(raw);
                    int corrected;
                    if (parsed == null) {
                        corrected = intValue.defaultValue();
                        if (raw != null) {
                            warnInvalidType(definition.key(), raw, "integer", corrected);
                        }
                    } else {
                        corrected = intValue.clamp(parsed);
                        if (corrected != parsed) {
                            LOGGER.log(Level.WARNING,
                                    "Configuration {0} field {1} is outside [{2}, {3}]; corrected from {4} to {5}",
                                    new Object[] { path, definition.key(), intValue.minValue(),
                                            intValue.maxValue(), parsed, corrected });
                        }
                    }
                    if (parsed == null || corrected != parsed) {
                        values.set(definition.key(), corrected);
                        changed[0] = true;
                    }
                }
            }
        });
        return changed[0];
    }

    private void warnInvalidType(String key, Object value, String expectedType, Object fallback) {
        String actualType = value == null ? "missing" : value.getClass().getSimpleName();
        LOGGER.log(Level.WARNING,
                "Configuration {0} field {1} must be {2}, but was {3}; corrected to {4}",
                new Object[] { path, key, expectedType, actualType, fallback });
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

    private static Map<String, Value> indexDefinitions(List<Value> definitions) {
        Map<String, Value> indexed = new HashMap<>();
        for (Value definition : new ArrayList<>(definitions)) {
            if (indexed.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate configuration key: " + definition.key());
            }
        }
        return Collections.unmodifiableMap(indexed);
    }
}
