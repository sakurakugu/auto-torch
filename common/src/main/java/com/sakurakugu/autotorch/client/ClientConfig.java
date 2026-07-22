package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;

import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;

/** Loader-neutral facade for persistent client settings. */
public final class ClientConfig {
    private static ConfigBackend backend = new MemoryConfigBackend();

    private ClientConfig() {
    }

    public static void install(ConfigBackend value) {
        backend = value;
    }

    public static boolean isNearbyAutoTorchEnabled() { return bool(NEARBY_AUTO_TORCH_ENABLED); }
    public static void setNearbyAutoTorchEnabled(boolean value) { setBool(NEARBY_AUTO_TORCH_ENABLED, value); }
    public static int nearbyAutoTorchThreshold() { return integer(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD); }
    public static void setNearbyAutoTorchThreshold(int value) { setInt(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD, value); }
    public static boolean includesSkyLight() { return bool(NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT); }
    public static void setIncludesSkyLight(boolean value) { setBool(NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT, value); }
    public static boolean isLightOverlayEnabled() { return bool(LIGHT_OVERLAY_ENABLED); }
    public static void setLightOverlayEnabled(boolean value) { setBool(LIGHT_OVERLAY_ENABLED, value); }
    public static int lightOverlayRange() { return integer(LIGHT_OVERLAY_HORIZONTAL_RANGE); }
    public static void setLightOverlayRange(int value) { setInt(LIGHT_OVERLAY_HORIZONTAL_RANGE, value); }
    public static boolean showsLightOverlayNumbers() { return bool(LIGHT_OVERLAY_SHOW_NUMBERS); }
    public static void setShowsLightOverlayNumbers(boolean value) { setBool(LIGHT_OVERLAY_SHOW_NUMBERS, value); }
    public static boolean detectsSwampSlimes() { return bool(LIGHT_OVERLAY_DETECT_SWAMP_SLIMES); }
    public static void setDetectsSwampSlimes(boolean value) { setBool(LIGHT_OVERLAY_DETECT_SWAMP_SLIMES, value); }
    public static boolean detectsDrowned() { return bool(LIGHT_OVERLAY_DETECT_DROWNED); }
    public static void setDetectsDrowned(boolean value) { setBool(LIGHT_OVERLAY_DETECT_DROWNED, value); }
    public static boolean isSelectionOverlayEnabled() { return bool(SELECTION_OVERLAY_ENABLED); }
    public static void setSelectionOverlayEnabled(boolean value) { setBool(SELECTION_OVERLAY_ENABLED, value); }
    public static boolean usesSelectionLines() { return bool(SELECTION_OVERLAY_LINES_ONLY); }
    public static void setUsesSelectionLines(boolean value) { setBool(SELECTION_OVERLAY_LINES_ONLY, value); }
    public static boolean usesSmoothSpheres() { return bool(SELECTION_OVERLAY_SMOOTH_SPHERES); }
    public static void setUsesSmoothSpheres(boolean value) { setBool(SELECTION_OVERLAY_SMOOTH_SPHERES, value); }
    public static int defaultMaxTorches() { return integer(TASK_DEFAULT_MAX_TORCHES); }
    public static void setDefaultMaxTorches(int value) { setInt(TASK_DEFAULT_MAX_TORCHES, value); }
    public static int defaultMinSpacing() { return integer(TASK_DEFAULT_MIN_SPACING); }
    public static void setDefaultMinSpacing(int value) { setInt(TASK_DEFAULT_MIN_SPACING, value); }
    public static boolean isDefaultUndergroundOnly() { return bool(TASK_DEFAULT_UNDERGROUND_ONLY); }
    public static void setDefaultUndergroundOnly(boolean value) { setBool(TASK_DEFAULT_UNDERGROUND_ONLY, value); }
    public static boolean creativeConsumesTorches() { return bool(TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES); }
    public static void setCreativeConsumesTorches(boolean value) { setBool(TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES, value); }
    public static boolean survivalConsumesTorches() { return bool(TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES); }
    public static void setSurvivalConsumesTorches(boolean value) { setBool(TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES, value); }
    public static boolean isWoodenAxeSelectionEnabled() { return bool(TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED); }
    public static void setWoodenAxeSelectionEnabled(boolean value) { setBool(TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED, value); }

    private static boolean bool(BooleanValue definition) {
        return backend.getBoolean(definition.key(), definition.defaultValue());
    }

    private static int integer(IntValue definition) {
        return definition.clamp(backend.getInt(definition.key(), definition.defaultValue()));
    }

    private static void setBool(BooleanValue definition, boolean value) {
        backend.setBoolean(definition.key(), value);
        backend.save();
    }

    private static void setInt(IntValue definition, int value) {
        backend.setInt(definition.key(), value);
        backend.save();
    }

    private static final class MemoryConfigBackend implements ConfigBackend {
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public void setBoolean(String key, boolean value) { values.put(key, value); }
        public void setInt(String key, int value) { values.put(key, value); }
        public void save() { }
    }
}
