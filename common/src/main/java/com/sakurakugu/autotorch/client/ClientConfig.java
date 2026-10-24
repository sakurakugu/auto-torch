package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.DoubleValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.StringValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;

import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;

/** Loader-neutral facade for persistent client settings. */
public final class ClientConfig {
    private static ConfigBackend backend = new MemoryConfigBackend();
    private static volatile ClientConfigSnapshot cached = ClientConfigSnapshot.defaults();
    private static volatile ClientConfigSnapshot applied;
    private static volatile ItemCache itemCache;

    private ClientConfig() {
    }

    public static void install(ConfigBackend value) {
        backend = value;
        reload();
    }

    /** Forge 配置在注册后才加载，由加载事件发布第一个快照。 */
    public static void installDeferred(ConfigBackend value) {
        backend = value;
        cached = ClientConfigSnapshot.defaults();
        itemCache = null;
    }

    public static ClientConfigSnapshot snapshot() { return cached; }

    /** 重载可来自文件监听线程；只发布不可变快照，世界状态在客户端 tick 更新。 */
    public static synchronized void reload() {
        cached = ClientConfigSnapshot.load(backend);
        itemCache = null;
    }

    public static void applyPendingReload() {
        ClientConfigSnapshot current = cached;
        if (applied != current) {
            applied = current;
            LightOverlayState.reloadConfig();
            SelectionState.reloadConfig();
        }
    }

    /** 恢复全部客户端配置并立即持久化。 */
    public static void resetDefaults() {
        reset(CLIENT);
    }

    public static void resetNearbyAutoTorchDefaults() {
        resetDefaults(ClientCategory.NEARBY_AUTO_TORCH);
    }

    public static void resetLightOverlayDefaults() {
        resetDefaults(ClientCategory.LIGHT_OVERLAY);
    }

    public static void resetLightingTaskDefaults() {
        resetDefaults(ClientCategory.LIGHTING_TASK);
    }

    public static void resetDefaults(ClientCategory category) {
        reset(category.definitions());
    }

    /** 只恢复一个客户端配置项。 */
    public static void resetDefaults(Value definition) {
        if (!CLIENT.contains(definition)) throw new IllegalArgumentException("Unknown client setting");
        reset(List.of(definition));
    }

    /** 只恢复指定 TOML 配置节，未知节名不会触发全部重置。 */
    public static void resetDefaults(String section) {
        List<Value> definitions = CLIENT.stream().filter(value -> value.key().startsWith(section + ".")).toList();
        if (definitions.isEmpty()) throw new IllegalArgumentException("Unknown client section: " + section);
        reset(definitions);
    }

    private static void reset(List<Value> definitions) {
        for (var definition : definitions) {
            if (definition instanceof BooleanValue value) backend.setBoolean(value.key(), value.defaultValue());
            else if (definition instanceof IntValue value) backend.setInt(value.key(), value.defaultValue());
            else if (definition instanceof DoubleValue value) backend.setDouble(value.key(), value.defaultValue());
            else if (definition instanceof StringValue value) backend.setString(value.key(), value.defaultValue());
        }
        backend.save();
        reload();
        LightOverlayState.reloadConfig();
        SelectionState.reloadConfig();
    }

    public static Object get(Value definition) {
        return cached.get(definition);
    }

    public static void set(Value definition, String rawValue) {
        try {
            if (definition instanceof BooleanValue value) {
                if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                backend.setBoolean(value.key(), Boolean.parseBoolean(rawValue));
            } else if (definition instanceof IntValue value) {
                int parsed = Integer.parseInt(rawValue);
                if (!value.accepts(parsed)) throw new IllegalArgumentException();
                backend.setInt(value.key(), parsed);
            } else if (definition instanceof DoubleValue value) {
                double parsed = Double.parseDouble(rawValue);
                if (!Double.isFinite(parsed) || parsed < value.minValue() || parsed > value.maxValue()) throw new IllegalArgumentException();
                backend.setDouble(value.key(), parsed);
            } else if (definition instanceof StringValue value) {
                if (!value.accepts(rawValue)) throw new IllegalArgumentException();
                if (value == SELECTION_TOOL_ITEM || value == NEARBY_AUTO_TORCH_ITEM || value == TASK_DEFAULT_PLACEMENT_ITEM) {
                    Item item = resolveItem(rawValue);
                    if (item == null || item == Items.AIR || (value != SELECTION_TOOL_ITEM && !(item instanceof BlockItem))) {
                        throw new IllegalArgumentException();
                    }
                }
                backend.setString(value.key(), rawValue);
            }
            backend.save();
            reload();
            if (definition.key().startsWith("lightOverlay.")) LightOverlayState.reloadConfig();
            if (definition.key().startsWith("selectionOverlay.")) SelectionState.reloadConfig();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid value for " + definition.key(), exception);
        }
    }

    public static boolean isNearbyAutoTorchEnabled() { return bool(NEARBY_AUTO_TORCH_ENABLED); }
    public static void setNearbyAutoTorchEnabled(boolean value) { setBool(NEARBY_AUTO_TORCH_ENABLED, value); }
    public static int nearbyAutoTorchThreshold() { return integer(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD); }
    public static void setNearbyAutoTorchThreshold(int value) { setInt(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD, value); }
    public static boolean includesSkyLight() { return bool(NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT); }
    public static void setIncludesSkyLight(boolean value) { setBool(NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT, value); }
    public static boolean isLightOverlayEnabled() { return bool(LIGHT_OVERLAY_ENABLED); }
    public static void setLightOverlayEnabled(boolean value) { setBool(LIGHT_OVERLAY_ENABLED, value); }
    public static boolean isLightOverlayRenderThrough() { return bool(LIGHT_OVERLAY_RENDER_THROUGH); }
    public static void setLightOverlayRenderThrough(boolean value) { setBool(LIGHT_OVERLAY_RENDER_THROUGH, value); }
    public static boolean rotatesLightOverlayNumbers() { return bool(LIGHT_OVERLAY_NUMBER_ROTATION); }
    public static void setRotatesLightOverlayNumbers(boolean value) { setBool(LIGHT_OVERLAY_NUMBER_ROTATION, value); }
    public static int lightOverlayRange() { return integer(LIGHT_OVERLAY_HORIZONTAL_RANGE); }
    public static void setLightOverlayRange(int value) { setInt(LIGHT_OVERLAY_HORIZONTAL_RANGE, value); }
    public static int lightOverlayDownRange() { return integer(LIGHT_OVERLAY_DOWN_RANGE); }
    public static void setLightOverlayDownRange(int value) { setInt(LIGHT_OVERLAY_DOWN_RANGE, value); }
    public static int lightOverlayUpRange() { return integer(LIGHT_OVERLAY_UP_RANGE); }
    public static void setLightOverlayUpRange(int value) { setInt(LIGHT_OVERLAY_UP_RANGE, value); }
    public static int lightOverlayMode() { return integer(LIGHT_OVERLAY_MODE); }
    public static void setLightOverlayMode(int value) { setInt(LIGHT_OVERLAY_MODE, value); }
    public static boolean detectsSlimes() { return bool(LIGHT_OVERLAY_DETECT_SLIMES); }
    public static void setDetectsSlimes(boolean value) { setBool(LIGHT_OVERLAY_DETECT_SLIMES, value); }
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
    public static int defaultTaskLightThreshold() { return integer(TASK_DEFAULT_LIGHT_THRESHOLD); }
    public static void setDefaultTaskLightThreshold(int value) { setInt(TASK_DEFAULT_LIGHT_THRESHOLD, value); }
    public static boolean isDefaultIncludeSkyLight() { return bool(TASK_DEFAULT_INCLUDE_SKY_LIGHT); }
    public static void setDefaultIncludeSkyLight(boolean value) { setBool(TASK_DEFAULT_INCLUDE_SKY_LIGHT, value); }
    public static boolean creativeConsumesTorches() { return bool(TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES); }
    public static void setCreativeConsumesTorches(boolean value) { setBool(TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES, value); }
    public static boolean survivalConsumesTorches() { return bool(TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES); }
    public static void setSurvivalConsumesTorches(boolean value) { setBool(TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES, value); }
    public static boolean isSelectionToolEnabled() { return bool(SELECTION_TOOL_ENABLED); }
    public static void setSelectionToolEnabled(boolean value) { setBool(SELECTION_TOOL_ENABLED, value); }
    /** 区间插火把界面的转换与互换按钮是否用符号显示；关闭时显示文字。 */
    public static boolean usesSymbolButtons() { return bool(SELECTION_OVERLAY_SYMBOL_BUTTONS); }

    public static int nearbyScanInterval() { return integer(NEARBY_AUTO_TORCH_SCAN_INTERVAL); }
    public static int nearbyDarkConfirmTicks() { return integer(NEARBY_AUTO_TORCH_DARK_CONFIRM); }
    public static int nearbyRetryDelay() { return integer(NEARBY_AUTO_TORCH_RETRY_DELAY); }
    public static int nearbyHorizontalRadius() { return integer(NEARBY_AUTO_TORCH_HORIZONTAL_RADIUS); }
    public static String nearbyItemId() { return string(NEARBY_AUTO_TORCH_ITEM); }
    public static double doubleValue(DoubleValue value) {
        return cached.decimal(value);
    }
    public static String stringValue(StringValue value) { return string(value); }
    public static Item selectionToolItem() {
        return items(cached).selectionTool;
    }
    public static BlockItem nearbyPlacementItem() {
        return (BlockItem) items(cached).nearbyPlacement;
    }

    /** 与区间任务实际放置的默认方块状态保持一致。 */
    public static int taskPlacementLightEmission() {
        return items(cached).taskPlacementLightEmission;
    }

    public static Component taskPlacementName() {
        Item item = items(cached).taskPlacement;
        return item == null ? Component.literal(cached.taskDefaultPlacementItem) : itemName(item);
    }

    public static Component nearbyPlacementName() {
        return itemName(items(cached).nearbyPlacement);
    }

    private static Component itemName(Item item) {
        return new ItemStack(item).getHoverName();
    }

    private static Item resolveItem(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id == null ? null : Registry.ITEM.getOptional(id).orElse(null);
    }
    public static int intValue(IntValue value) {
        return cached.integer(value);
    }

    private static boolean bool(BooleanValue definition) {
        return cached.bool(definition);
    }

    private static int integer(IntValue definition) {
        return cached.integer(definition);
    }

    private static String string(StringValue definition) {
        return cached.string(definition);
    }

    private static void setBool(BooleanValue definition, boolean value) {
        backend.setBoolean(definition.key(), value);
        backend.save();
        reload();
    }

    private static void setInt(IntValue definition, int value) {
        backend.setInt(definition.key(), value);
        backend.save();
        reload();
    }

    public static BlockItem nearbyPlacementItem(ClientConfigSnapshot config) {
        return (BlockItem) items(config).nearbyPlacement;
    }

    private static ItemCache items(ClientConfigSnapshot config) {
        ItemCache current = itemCache;
        if (current == null || current.config != config) {
            // 首次实际交互时注册表已经就绪；之后只在配置变化时重新解析 ID。
            current = new ItemCache(config);
            itemCache = current;
        }
        return current;
    }

    private static final class ItemCache {
        private final ClientConfigSnapshot config;
        private final Item selectionTool;
        private final Item nearbyPlacement;
        private final Item taskPlacement;
        private final int taskPlacementLightEmission;

        private ItemCache(ClientConfigSnapshot config) {
            this.config = config;
            Item tool = resolveItem(config.selectionToolItem);
            selectionTool = tool == null || tool == Items.AIR ? Items.WOODEN_AXE : tool;
            Item placement = resolveItem(config.nearbyAutoTorchItem);
            nearbyPlacement = placement instanceof BlockItem ? placement : Items.TORCH;
            taskPlacement = resolveItem(config.taskDefaultPlacementItem);
            taskPlacementLightEmission = taskPlacement instanceof BlockItem blockItem
                    ? blockItem.getBlock().defaultBlockState().getLightEmission() : 0;
        }
    }

    private static final class MemoryConfigBackend implements ConfigBackend {
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public double getDouble(String key, double fallback) { return (double) values.getOrDefault(key, fallback); }
        public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        public void setBoolean(String key, boolean value) { values.put(key, value); }
        public void setInt(String key, int value) { values.put(key, value); }
        public void setDouble(String key, double value) { values.put(key, value); }
        public void setString(String key, String value) { values.put(key, value); }
        public void save() { }
    }
}
