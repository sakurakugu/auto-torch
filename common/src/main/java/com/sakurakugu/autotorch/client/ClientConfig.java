package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigBackend;

/** Loader-neutral facade for persistent client settings. */
public final class ClientConfig {
    private static ConfigBackend backend = new MemoryConfigBackend();

    private ClientConfig() {
    }

    public static void install(ConfigBackend value) {
        backend = value;
    }

    public static boolean isNearbyAutoTorchEnabled() { return bool("nearbyAutoTorch.enabled", false); }
    public static void setNearbyAutoTorchEnabled(boolean value) { setBool("nearbyAutoTorch.enabled", value); }
    public static int nearbyAutoTorchThreshold() { return clamp(integer("nearbyAutoTorch.lightThreshold", 4), 1, 16); }
    public static void setNearbyAutoTorchThreshold(int value) { setInt("nearbyAutoTorch.lightThreshold", value); }
    public static boolean includesSkyLight() { return bool("nearbyAutoTorch.includeSkyLight", true); }
    public static void setIncludesSkyLight(boolean value) { setBool("nearbyAutoTorch.includeSkyLight", value); }
    public static boolean isLightOverlayEnabled() { return bool("lightOverlay.enabled", false); }
    public static void setLightOverlayEnabled(boolean value) { setBool("lightOverlay.enabled", value); }
    public static int lightOverlayRange() { return clamp(integer("lightOverlay.horizontalRange", 16), 1, 64); }
    public static void setLightOverlayRange(int value) { setInt("lightOverlay.horizontalRange", value); }
    public static boolean showsLightOverlayNumbers() { return bool("lightOverlay.showNumbers", false); }
    public static void setShowsLightOverlayNumbers(boolean value) { setBool("lightOverlay.showNumbers", value); }
    public static boolean detectsSwampSlimes() { return bool("lightOverlay.detectSwampSlimes", false); }
    public static void setDetectsSwampSlimes(boolean value) { setBool("lightOverlay.detectSwampSlimes", value); }
    public static boolean detectsDrowned() { return bool("lightOverlay.detectDrowned", false); }
    public static void setDetectsDrowned(boolean value) { setBool("lightOverlay.detectDrowned", value); }
    public static boolean isSelectionOverlayEnabled() { return bool("selectionOverlay.enabled", true); }
    public static void setSelectionOverlayEnabled(boolean value) { setBool("selectionOverlay.enabled", value); }
    public static boolean usesSelectionLines() { return bool("selectionOverlay.linesOnly", false); }
    public static void setUsesSelectionLines(boolean value) { setBool("selectionOverlay.linesOnly", value); }
    public static boolean usesSmoothSpheres() { return bool("selectionOverlay.smoothSpheres", false); }
    public static void setUsesSmoothSpheres(boolean value) { setBool("selectionOverlay.smoothSpheres", value); }
    public static int defaultMaxTorches() { return clamp(integer("lightingTaskDefaults.maxTorches", 0), 0, 4096); }
    public static void setDefaultMaxTorches(int value) { setInt("lightingTaskDefaults.maxTorches", value); }
    public static int defaultMinSpacing() { return clamp(integer("lightingTaskDefaults.minSpacing", 8), 3, 12); }
    public static void setDefaultMinSpacing(int value) { setInt("lightingTaskDefaults.minSpacing", value); }
    public static boolean isDefaultUndergroundOnly() { return bool("lightingTaskDefaults.undergroundOnly", true); }
    public static void setDefaultUndergroundOnly(boolean value) { setBool("lightingTaskDefaults.undergroundOnly", value); }
    public static boolean creativeConsumesTorches() { return bool("lightingTaskDefaults.creativeConsumeTorches", false); }
    public static void setCreativeConsumesTorches(boolean value) { setBool("lightingTaskDefaults.creativeConsumeTorches", value); }
    public static boolean survivalConsumesTorches() { return bool("lightingTaskDefaults.survivalConsumeTorches", true); }
    public static void setSurvivalConsumesTorches(boolean value) { setBool("lightingTaskDefaults.survivalConsumeTorches", value); }
    public static boolean isWoodenAxeSelectionEnabled() { return bool("lightingTaskDefaults.woodenAxeSelectionEnabled", true); }
    public static void setWoodenAxeSelectionEnabled(boolean value) { setBool("lightingTaskDefaults.woodenAxeSelectionEnabled", value); }

    private static boolean bool(String key, boolean fallback) { return backend.getBoolean(key, fallback); }
    private static int integer(String key, int fallback) { return backend.getInt(key, fallback); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static void setBool(String key, boolean value) { backend.setBoolean(key, value); backend.save(); }
    private static void setInt(String key, int value) { backend.setInt(key, value); backend.save(); }

    private static final class MemoryConfigBackend implements ConfigBackend {
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public void setBoolean(String key, boolean value) { values.put(key, value); }
        public void setInt(String key, int value) { values.put(key, value); }
        public void save() { }
    }
}
