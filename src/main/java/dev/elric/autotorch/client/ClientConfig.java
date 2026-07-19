package dev.sakurakugu.autotorch.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 持久化仅由本地客户端使用的自动放置设置。 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue NEARBY_AUTO_TORCH_ENABLED;
    private static final ModConfigSpec.IntValue NEARBY_AUTO_TORCH_THRESHOLD;
    private static final ModConfigSpec.BooleanValue NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT;

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
}
