package dev.sakurakugu.autotorch.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 持久化仅由本地客户端使用的自动放置设置。 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue NEARBY_AUTO_TORCH_ENABLED;
    private static final ModConfigSpec.IntValue NEARBY_AUTO_TORCH_THRESHOLD;
    private static final ModConfigSpec.BooleanValue NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT;
    private static final ModConfigSpec.BooleanValue LIGHT_OVERLAY_ENABLED;
    private static final ModConfigSpec.IntValue LIGHT_OVERLAY_RANGE;
    private static final ModConfigSpec.BooleanValue LIGHT_OVERLAY_NUMBERS;
    private static final ModConfigSpec.BooleanValue SWAMP_SLIME_DETECTION;
    private static final ModConfigSpec.BooleanValue DROWNED_DETECTION;
    private static final ModConfigSpec.BooleanValue SELECTION_OVERLAY_ENABLED;
    private static final ModConfigSpec.BooleanValue SELECTION_DISPLAY_LINES;
    private static final ModConfigSpec.BooleanValue SMOOTH_SPHERE_DISPLAY;
    private static final ModConfigSpec.IntValue DEFAULT_MAX_TORCHES;
    private static final ModConfigSpec.IntValue DEFAULT_MIN_SPACING;
    private static final ModConfigSpec.BooleanValue DEFAULT_UNDERGROUND_ONLY;
    private static final ModConfigSpec.BooleanValue CREATIVE_CONSUME_TORCHES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("nearbyAutoTorch");
        NEARBY_AUTO_TORCH_ENABLED = builder
                .comment("Automatically place a torch at a nearby valid position when its light is below the threshold.")
                .define("enabled", false);
        NEARBY_AUTO_TORCH_THRESHOLD = builder
                .comment("Place when the measured light is strictly lower than this value.")
                .defineInRange("lightThreshold", 4, 1, 16);
        NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT = builder
                .comment("Use the higher of block light and sky light instead of block light alone.")
                .define("includeSkyLight", true);
        builder.pop();

        builder.push("lightOverlay");
        LIGHT_OVERLAY_ENABLED = builder.define("enabled", false);
        LIGHT_OVERLAY_RANGE = builder.defineInRange("horizontalRange", 16, 1, 64);
        LIGHT_OVERLAY_NUMBERS = builder.define("showNumbers", false);
        SWAMP_SLIME_DETECTION = builder.define("detectSwampSlimes", false);
        DROWNED_DETECTION = builder.define("detectDrowned", false);
        builder.pop();

        builder.push("selectionOverlay");
        SELECTION_OVERLAY_ENABLED = builder.define("enabled", true);
        SELECTION_DISPLAY_LINES = builder.define("linesOnly", false);
        SMOOTH_SPHERE_DISPLAY = builder.define("smoothSpheres", false);
        builder.pop();

        builder.push("lightingTaskDefaults");
        DEFAULT_MAX_TORCHES = builder
                .comment("Default torch limit. Zero means unlimited.")
                .defineInRange("maxTorches", 0, 0, 4096);
        DEFAULT_MIN_SPACING = builder.defineInRange("minSpacing", 8, 3, 12);
        DEFAULT_UNDERGROUND_ONLY = builder.define("undergroundOnly", true);
        CREATIVE_CONSUME_TORCHES = builder
                .comment("Whether area lighting consumes inventory torches in Creative mode. Survival mode always disables consumption.")
                .define("creativeConsumeTorches", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean isNearbyAutoTorchEnabled() {
        return NEARBY_AUTO_TORCH_ENABLED.get();
    }

    public static void setNearbyAutoTorchEnabled(boolean enabled) {
        NEARBY_AUTO_TORCH_ENABLED.set(enabled);
        SPEC.save();
    }

    public static int nearbyAutoTorchThreshold() {
        return NEARBY_AUTO_TORCH_THRESHOLD.get();
    }

    public static void setNearbyAutoTorchThreshold(int threshold) {
        NEARBY_AUTO_TORCH_THRESHOLD.set(threshold);
        SPEC.save();
    }

    public static boolean includesSkyLight() {
        return NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT.get();
    }

    public static void setIncludesSkyLight(boolean includeSkyLight) {
        NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT.set(includeSkyLight);
        SPEC.save();
    }

    public static boolean isLightOverlayEnabled() {
        return LIGHT_OVERLAY_ENABLED.get();
    }

    public static void setLightOverlayEnabled(boolean enabled) {
        LIGHT_OVERLAY_ENABLED.set(enabled);
        SPEC.save();
    }

    public static int lightOverlayRange() {
        return LIGHT_OVERLAY_RANGE.get();
    }

    public static void setLightOverlayRange(int range) {
        LIGHT_OVERLAY_RANGE.set(range);
        SPEC.save();
    }

    public static boolean showsLightOverlayNumbers() {
        return LIGHT_OVERLAY_NUMBERS.get();
    }

    public static void setShowsLightOverlayNumbers(boolean showNumbers) {
        LIGHT_OVERLAY_NUMBERS.set(showNumbers);
        SPEC.save();
    }

    public static boolean detectsSwampSlimes() {
        return SWAMP_SLIME_DETECTION.get();
    }

    public static void setDetectsSwampSlimes(boolean enabled) {
        SWAMP_SLIME_DETECTION.set(enabled);
        SPEC.save();
    }

    public static boolean detectsDrowned() {
        return DROWNED_DETECTION.get();
    }

    public static void setDetectsDrowned(boolean enabled) {
        DROWNED_DETECTION.set(enabled);
        SPEC.save();
    }

    public static boolean isSelectionOverlayEnabled() {
        return SELECTION_OVERLAY_ENABLED.get();
    }

    public static void setSelectionOverlayEnabled(boolean enabled) {
        SELECTION_OVERLAY_ENABLED.set(enabled);
        SPEC.save();
    }

    public static boolean usesSelectionLines() {
        return SELECTION_DISPLAY_LINES.get();
    }

    public static void setUsesSelectionLines(boolean linesOnly) {
        SELECTION_DISPLAY_LINES.set(linesOnly);
        SPEC.save();
    }

    public static boolean usesSmoothSpheres() {
        return SMOOTH_SPHERE_DISPLAY.get();
    }

    public static void setUsesSmoothSpheres(boolean smooth) {
        SMOOTH_SPHERE_DISPLAY.set(smooth);
        SPEC.save();
    }

    public static int defaultMaxTorches() {
        return DEFAULT_MAX_TORCHES.get();
    }

    public static void setDefaultMaxTorches(int maxTorches) {
        DEFAULT_MAX_TORCHES.set(maxTorches);
        SPEC.save();
    }

    public static int defaultMinSpacing() {
        return DEFAULT_MIN_SPACING.get();
    }

    public static void setDefaultMinSpacing(int minSpacing) {
        DEFAULT_MIN_SPACING.set(minSpacing);
        SPEC.save();
    }

    public static boolean isDefaultUndergroundOnly() {
        return DEFAULT_UNDERGROUND_ONLY.get();
    }

    public static void setDefaultUndergroundOnly(boolean undergroundOnly) {
        DEFAULT_UNDERGROUND_ONLY.set(undergroundOnly);
        SPEC.save();
    }

    public static boolean creativeConsumesTorches() {
        return CREATIVE_CONSUME_TORCHES.get();
    }

    public static void setCreativeConsumesTorches(boolean consumeTorches) {
        CREATIVE_CONSUME_TORCHES.set(consumeTorches);
        SPEC.save();
    }
}
