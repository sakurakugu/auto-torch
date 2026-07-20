package dev.sakurakugu.autotorch.neoforge;

import java.util.HashMap;
import java.util.Map;

import dev.sakurakugu.autotorch.config.ConfigBackend;
import dev.sakurakugu.autotorch.network.AreaZone;
import dev.sakurakugu.autotorch.network.StartLightingPayload;
import dev.sakurakugu.autotorch.server.ServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

final class NeoForgeConfigs {
    static final Backend CLIENT = client();
    static final Backend SERVER = server();

    private NeoForgeConfigs() {
    }

    private static Backend client() {
        Backend b = new Backend();
        b.push("nearbyAutoTorch");
        b.bool("enabled", false);
        b.integer("lightThreshold", 4, 1, 16);
        b.bool("includeSkyLight", true);
        b.pop();
        b.push("lightOverlay");
        b.bool("enabled", false);
        b.integer("horizontalRange", 16, 1, 64);
        b.bool("showNumbers", false);
        b.bool("detectSwampSlimes", false);
        b.bool("detectDrowned", false);
        b.pop();
        b.push("selectionOverlay");
        b.bool("enabled", true);
        b.bool("linesOnly", false);
        b.bool("smoothSpheres", false);
        b.pop();
        b.push("lightingTaskDefaults");
        b.integer("maxTorches", 0, 0, 4096);
        b.integer("minSpacing", 8, 3, 12);
        b.bool("undergroundOnly", true);
        b.bool("creativeConsumeTorches", false);
        b.bool("woodenAxeSelectionEnabled", true);
        b.pop();
        b.finish();
        return b;
    }

    private static Backend server() {
        Backend b = new Backend();
        b.push("limits");
        b.integer("maxBoxAxisLength", ServerConfig.HARD_MAX_BOX_AXIS_LENGTH, 1, ServerConfig.HARD_MAX_BOX_AXIS_LENGTH);
        b.integer("maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS, 1, AreaZone.MAX_SPHERE_RADIUS);
        b.integer("maxExclusions", StartLightingPayload.MAX_EXCLUSIONS, 0, StartLightingPayload.MAX_EXCLUSIONS);
        b.integer("maxTorchesPerTask", ServerConfig.HARD_MAX_TORCHES, 1, ServerConfig.HARD_MAX_TORCHES);
        b.bool("allowUnlimitedTorches", true);
        b.integer("minSpacing", 3, 3, 12);
        b.integer("maxSpacing", 12, 3, 12);
        b.integer("maxConcurrentTasks", 64, 1, 1024);
        b.pop();
        b.push("performance");
        b.integer("scanBudgetPerTaskTick", 12_000, 1, 120_000);
        b.integer("placeBudgetPerTaskTick", 8, 1, 64);
        b.integer("globalScanBudgetPerTick", 24_000, 1, 240_000);
        b.integer("globalPlaceBudgetPerTick", 16, 1, 256);
        b.integer("randomPlacementAttempts", 32, 1, 128);
        b.pop();
        b.finish();
        return b;
    }

    static final class Backend implements ConfigBackend {
        private final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        private final Map<String, ModConfigSpec.ConfigValue<?>> values = new HashMap<>();
        private String section;
        private ModConfigSpec spec;

        void push(String value) { section = value; builder.push(value); }
        void pop() { builder.pop(); section = null; }
        void bool(String key, boolean fallback) { values.put(section + "." + key, builder.define(key, fallback)); }
        void integer(String key, int fallback, int min, int max) {
            values.put(section + "." + key, builder.defineInRange(key, fallback, min, max));
        }
        void finish() { spec = builder.build(); }
        ModConfigSpec spec() { return spec; }

        @Override public boolean getBoolean(String key, boolean fallback) {
            ModConfigSpec.ConfigValue<?> value = values.get(key);
            return value == null ? fallback : (Boolean) value.get();
        }
        @Override public int getInt(String key, int fallback) {
            ModConfigSpec.ConfigValue<?> value = values.get(key);
            return value == null ? fallback : (Integer) value.get();
        }
        @Override public void setBoolean(String key, boolean value) { set(key, value); }
        @Override public void setInt(String key, int value) { set(key, value); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void set(String key, Object value) { ((ModConfigSpec.ConfigValue) values.get(key)).set(value); }
        @Override public void save() { spec.save(); }
    }
}
