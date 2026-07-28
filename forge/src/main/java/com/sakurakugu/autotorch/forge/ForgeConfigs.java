package com.sakurakugu.autotorch.forge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

final class ForgeConfigs {
    static final Backend CLIENT = create(ConfigDefinitions.CLIENT);
    static final Backend SERVER = create(ConfigDefinitions.SERVER);

    private ForgeConfigs() {
    }

    private static Backend create(List<Value> definitions) {
        Backend b = new Backend();
        definitions.forEach(b::define);
        b.finish();
        return b;
    }

    static final class Backend implements ConfigBackend {
        private final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        private final Map<String, ForgeConfigSpec.ConfigValue<?>> values = new HashMap<>();
        private final Map<String, List<String>> paths = new HashMap<>();
        private ForgeConfigSpec spec;
        private ModConfig config;

        void define(Value definition) {
            List<String> path = Arrays.asList(definition.key().split("\\."));
            paths.put(definition.key(), path);
            if (definition instanceof BooleanValue) {
                BooleanValue value = (BooleanValue) definition;
                values.put(value.key(), builder.define(path, value.defaultValue()));
            } else if (definition instanceof IntValue) {
                IntValue value = (IntValue) definition;
                values.put(value.key(), builder.defineInRange(
                        path, value.defaultValue(), value.minValue(), value.maxValue()));
            }
        }
        void finish() { spec = builder.build(); }
        ForgeConfigSpec spec() { return spec; }
        void attach(ModConfig config) { this.config = config; }

        @Override public boolean getBoolean(String key, boolean fallback) {
            ForgeConfigSpec.ConfigValue<?> value = values.get(key);
            return value == null ? fallback : (Boolean) value.get();
        }
        @Override public int getInt(String key, int fallback) {
            ForgeConfigSpec.ConfigValue<?> value = values.get(key);
            return value == null ? fallback : (Integer) value.get();
        }
        @Override public void setBoolean(String key, boolean value) { set(key, value); }
        @Override public void setInt(String key, int value) { set(key, value); }
        private void set(String key, Object value) {
            if (config != null && paths.containsKey(key)) {
                config.getConfigData().set(paths.get(key), value);
            }
        }
        @Override public void save() {
            if (config != null) config.save();
        }
    }
}
