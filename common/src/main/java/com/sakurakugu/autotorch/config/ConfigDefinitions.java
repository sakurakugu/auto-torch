package com.sakurakugu.autotorch.config;

import java.util.Arrays;
import java.util.List;

import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;

/**
 * 各加载器共用的配置目录：键名、默认值、取值范围与中文说明。
 * 说明的首行是界面行标签，其余行是悬停提示，同时也会写入配置文件注释。
 */
public final class ConfigDefinitions {
    public static final int HARD_MAX_BOX_AXIS_LENGTH = 321;
    public static final int HARD_MAX_TORCHES = 4096;

    public static final BooleanValue NEARBY_AUTO_TORCH_ENABLED = bool("nearbyAutoTorch.enabled", false,
            "附近自动照明\n启用后会在玩家周围约 2 格内的暗处自动放置方块");
    public static final IntValue NEARBY_AUTO_TORCH_LIGHT_THRESHOLD = integer("nearbyAutoTorch.lightThreshold", 4, 1, 16,
            "附近照明亮度阈值\n亮度低于该值时才放置方块");
    public static final BooleanValue NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT = bool("nearbyAutoTorch.includeSkyLight", false,
            "附近照明计入天空光\ntrue 取方块光与天空光中的较大值，false 只判断方块光");
    public static final IntValue NEARBY_AUTO_TORCH_SCAN_INTERVAL = integer("nearbyAutoTorch.scanIntervalTicks", 10, 1, 40,
            "附近照明扫描间隔\n单位为 tick");
    public static final IntValue NEARBY_AUTO_TORCH_DARK_CONFIRM = stepped("nearbyAutoTorch.darkConfirmTicks", 10, 5, 40, 5,
            "附近照明黑暗确认时间\n候选位置需要连续保持黑暗，单位为 tick");
    public static final IntValue NEARBY_AUTO_TORCH_RETRY_DELAY = stepped("nearbyAutoTorch.retryDelayTicks", 40, 10, 100, 10,
            "附近照明重试等待时间\n同一位置的再次尝试间隔，单位为 tick");
    public static final IntValue NEARBY_AUTO_TORCH_HORIZONTAL_RADIUS = integer("nearbyAutoTorch.horizontalRadius", 2, 1, 4,
            "附近照明水平半径\n纵向范围固定为 -2 至 +1");
    public static final StringValue NEARBY_AUTO_TORCH_ITEM = string("nearbyAutoTorch.item", "minecraft:torch",
            "附近照明放置物品\n可放置方块物品 ID");
    public static final BooleanValue LIGHT_OVERLAY_ENABLED = bool("lightOverlay.enabled", false,
            "光照显示\n显示相机附近的光照等级与刷怪风险");
    public static final BooleanValue LIGHT_OVERLAY_RENDER_THROUGH = bool("lightOverlay.renderThrough", false,
            "光照显示透视渲染\n开启后可以看到被方块遮挡的光照标记");
    public static final BooleanValue LIGHT_OVERLAY_NUMBER_ROTATION = bool("lightOverlay.numberRotation", true,
            "光照数字跟随视角\nfalse 时数字朝向固定");
    public static final IntValue LIGHT_OVERLAY_HORIZONTAL_RANGE = integer("lightOverlay.horizontalRange", 16, 1, 64,
            "光照显示水平半径\n以相机为中心，调大会明显增加每帧的扫描量");
    public static final IntValue LIGHT_OVERLAY_DOWN_RANGE = integer("lightOverlay.downRange", 16, 0, 64,
            "光照显示向下范围\n相对相机向下扫描的格数，与向上范围之和不超过 64");
    public static final IntValue LIGHT_OVERLAY_UP_RANGE = integer("lightOverlay.upRange", 4, 0, 64,
            "光照显示向上范围\n相对相机向上扫描的格数，与向下范围之和不超过 64");
    public static final IntValue LIGHT_OVERLAY_MODE = integer("lightOverlay.mode", 0, 0, 2,
            "光照显示样式\n0=X 标记，1=数字，2=方框数字");
    public static final BooleanValue LIGHT_OVERLAY_DETECT_SLIMES = bool("lightOverlay.detectSlimes", false,
            "史莱姆检测\n标记符合原版条件的史莱姆刷新位置，显示为紫色");
    public static final BooleanValue LIGHT_OVERLAY_DETECT_DROWNED = bool("lightOverlay.detectDrowned", false,
            "溺尸检测\n标记符合原版条件的溺尸刷新位置，需要逐帧检测水柱，显示为青色");
    public static final IntValue LIGHT_OVERLAY_SCAN_BUDGET = stepped("lightOverlay.scanBudgetPerTick", 12_000, 2_000, 48_000, 2_000,
            "光照显示扫描预算\n每 tick 最多扫描的方块数");
    public static final IntValue LIGHT_OVERLAY_REFRESH_INTERVAL = stepped("lightOverlay.refreshIntervalTicks", 100, 20, 400, 20,
            "光照显示复查间隔\n定期复查已缓存位置的间隔，单位为 tick");
    public static final IntValue LIGHT_OVERLAY_DROWNED_BUDGET = stepped("lightOverlay.drownedBudgetPerFrame", 64, 16, 256, 16,
            "溺尸遮挡检测预算\n每帧最多检测的方块数，26.1.2 及更早版本不使用此项");
    public static final DoubleValue LIGHT_OVERLAY_NEAR_LINE_WIDTH = decimal("lightOverlay.nearLineWidth", 2.5, 0.25, 8.0,
            "光照标记近处线宽\n摄像机附近标记的线宽");
    public static final DoubleValue LIGHT_OVERLAY_FAR_LINE_WIDTH = decimal("lightOverlay.farLineWidth", 0.75, 0.75, 2.5,
            "光照标记远处线宽\n远处标记的最低线宽");
    public static final StringValue LIGHT_OVERLAY_ALWAYS_RISK_COLOR = color("lightOverlay.alwaysRiskColor", "FF3030", "持续危险标记颜色");
    public static final StringValue LIGHT_OVERLAY_NIGHT_RISK_COLOR = color("lightOverlay.nightRiskColor", "FFD23C", "夜间危险标记颜色");
    public static final StringValue LIGHT_OVERLAY_SAFE_COLOR = color("lightOverlay.safeColor", "50E060", "安全标记颜色");
    public static final StringValue LIGHT_OVERLAY_SLIME_COLOR = color("lightOverlay.slimeColor", "E050E0", "史莱姆标记颜色");
    public static final StringValue LIGHT_OVERLAY_DROWNED_COLOR = color("lightOverlay.drownedColor", "40D8E8", "溺尸标记颜色");
    public static final IntValue LIGHT_OVERLAY_MARKER_ALPHA = integer("lightOverlay.markerAlpha", 224, 0, 255,
            "光照标记透明度\n范围为 0 至 255");
    public static final BooleanValue SELECTION_OVERLAY_ENABLED = bool("selectionOverlay.enabled", true,
            "选区显示\n显示照明范围与排除区的选区框");
    public static final BooleanValue SELECTION_OVERLAY_LINES_ONLY = bool("selectionOverlay.linesOnly", false,
            "选区只画轮廓\ntrue 只画轮廓线，false 显示半透明面");
    public static final BooleanValue SELECTION_OVERLAY_SMOOTH_SPHERES = bool("selectionOverlay.smoothSpheres", false,
            "平滑球形选区\n绘制更精细，开销也更高");
    public static final DoubleValue SELECTION_OVERLAY_NEAR_LINE_WIDTH = decimal("selectionOverlay.nearLineWidth", 3.0, 0.25, 8.0,
            "选区近处线宽\n主选区和排除区共用");
    public static final DoubleValue SELECTION_OVERLAY_FAR_LINE_WIDTH = decimal("selectionOverlay.farLineWidth", 0.75, 0.75, 3.0,
            "选区远处线宽\n远处选区的最低线宽");
    public static final StringValue SELECTION_OVERLAY_DRAFT_COLOR = color("selectionOverlay.draftColor", "70A0FF", "待确认选区颜色");
    public static final StringValue SELECTION_OVERLAY_MAIN_COLOR = color("selectionOverlay.mainColor", "50FF70", "主选区颜色");
    public static final StringValue SELECTION_OVERLAY_EXCLUSION_COLOR = color("selectionOverlay.exclusionColor", "FF5050", "排除区颜色");
    public static final IntValue SELECTION_OVERLAY_FACE_ALPHA = integer("selectionOverlay.faceAlpha", 40, 0, 255,
            "选区面透明度\n范围为 0 至 255");
    public static final IntValue SELECTION_OVERLAY_EXCLUSION_FACE_ALPHA = integer("selectionOverlay.exclusionFaceAlpha", 48, 0, 255,
            "排除区面透明度\n范围为 0 至 255");
    public static final IntValue SELECTION_OVERLAY_SPHERE_LONGITUDE_SEGMENTS = stepped("selectionOverlay.sphereLongitudeSegments", 24, 12, 48, 4,
            "球形选区水平分段数\n步进为 4");
    public static final IntValue SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS = stepped("selectionOverlay.sphereLatitudeSegments", 12, 12, 48, 4,
            "球形选区垂直分段数\n步进为 4");
    public static final StringValue SELECTION_TOOL_ITEM = string("selectionOverlay.toolItem", "minecraft:wooden_axe",
            "选区工具物品\n物品 ID");
    public static final BooleanValue SELECTION_TOOL_ENABLED = bool("selectionOverlay.selectionToolEnabled", true,
            "启用选区工具\n手持工具左键、右键可设置 A、B 点，关闭后恢复原版的破坏与方块交互，仍可在面板中输入坐标");
    public static final BooleanValue SELECTION_OVERLAY_SYMBOL_BUTTONS = bool("selectionOverlay.symbolButtons", false,
            "用符号显示按钮\n区间照明界面的转换与互换按钮，关闭时显示文字");
    public static final IntValue TASK_DEFAULT_MAX_TORCHES = stepped("lightingTaskDefaults.maxTorches", 0, 0, HARD_MAX_TORCHES, 128,
            "默认方块数量上限\n新建任务时的默认数量，受服务端 limits.maxTorchesPerTask 限制，0 表示不限");
    public static final IntValue TASK_DEFAULT_MIN_SPACING = integer("lightingTaskDefaults.minSpacing", 8, 1, 12,
            "默认方块间距\n方块坐标之间的三维直线距离，设为 8 时同一直线上两个方块之间隔 7 格");
    public static final IntValue TASK_DEFAULT_LIGHT_THRESHOLD = integer("lightingTaskDefaults.lightThreshold", 0, 0, 15,
            "默认亮度阈值\n只处理方块光小于或等于该值的位置，0 表示只处理方块光为 0 的位置");
    public static final BooleanValue TASK_DEFAULT_INCLUDE_SKY_LIGHT = bool("lightingTaskDefaults.includeSkyLight", false,
            "默认包含天空光\n新建任务时是否包含有天空光的位置，关闭后只处理无天空光的位置");
    public static final BooleanValue TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES = bool("lightingTaskDefaults.creativeConsumeTorches", false,
            "创造模式消耗物品\n新建任务时创造模式是否默认消耗背包中的所选放置物品");
    public static final BooleanValue TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES = bool("lightingTaskDefaults.survivalConsumeTorches", true,
            "生存模式消耗物品\n新建任务时生存模式是否默认消耗背包中的所选放置物品，单人游戏按此项，多人游戏以服务端 gameplay.survivalConsumesTorches 为准");
    public static final IntValue TASK_PROGRESS_INTERVAL = stepped("lightingTaskDefaults.progressIntervalTicks", 10, 10, 40, 10,
            "区间任务进度同步间隔\n单位为 tick");
    public static final StringValue TASK_DEFAULT_PLACEMENT_ITEM = string("lightingTaskDefaults.placementItem", "minecraft:torch",
            "任务放置物品\n可放置方块物品 ID");

    public static final IntValue LIMIT_MAX_BOX_AXIS_LENGTH = integer("limits.maxBoxAxisLength", HARD_MAX_BOX_AXIS_LENGTH, 1, HARD_MAX_BOX_AXIS_LENGTH,
            "长方体选区最大边长\n服务端允许的最大边长，调大会显著增加单个任务的扫描量与选区数据量");
    public static final IntValue LIMIT_MAX_SPHERE_RADIUS = integer("limits.maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS, 1, AreaZone.MAX_SPHERE_RADIUS,
            "球形选区最大半径\n服务端允许的最大半径，调大会显著增加单个任务的扫描量");
    public static final IntValue LIMIT_MAX_EXCLUSIONS = integer("limits.maxExclusions", StartLightingPayload.MAX_EXCLUSIONS, 0, StartLightingPayload.MAX_EXCLUSIONS,
            "排除区数量上限\n单个任务允许提交的排除区数量，0 表示不允许提交排除区");
    public static final IntValue LIMIT_MAX_TORCHES_PER_TASK = integer("limits.maxTorchesPerTask", HARD_MAX_TORCHES, 1, HARD_MAX_TORCHES,
            "单任务方块数量上限\n服务端强制限制的上限；客户端设为 0（不限）时不套用该值");
    public static final BooleanValue LIMIT_ALLOW_UNLIMITED_TORCHES = bool("limits.allowUnlimitedTorches", true,
            "允许不限方块数量\n是否允许客户端把方块上限设为 0（不限），关闭后客户端必须给出不超过 limits.maxTorchesPerTask 的具体上限");
    public static final BooleanValue LIGHTING_TASK_ENABLED = bool("lightingTask.enabled", true,
            "启用区间任务\n服务端是否允许运行区间自动照明任务，关闭后所有玩家都无法提交或继续区间任务");
    public static final IntValue LIMIT_MIN_SPACING = integer("limits.minSpacing", 1, 1, 12,
            "方块间距下限\n服务端允许客户端设置的最小间距，下限越大方块越分散，越可能无法满足目标亮度\n修改时不能大于上限；手动写反时读取会按较小值作为下限");
    public static final IntValue LIMIT_MAX_SPACING = integer("limits.maxSpacing", 12, 1, 12,
            "方块间距上限\n服务端允许客户端设置的最大间距\n修改时不能小于下限；手动写反时读取会按较大值作为上限");
    public static final IntValue LIMIT_MAX_CONCURRENT_TASKS = integer("limits.maxConcurrentTasks", 64, 1, 1024,
            "并发任务数量上限\n全服可同时运行的区间任务数量，调高会成倍增加扫描与方块更新负担");
    public static final BooleanValue GAMEPLAY_SURVIVAL_CONSUMES_TORCHES = bool("gameplay.survivalConsumesTorches", true,
            "生存模式消耗物品\n生存模式任务是否必须消耗玩家背包中的所选放置物品，关闭后生存模式可以凭空放置所选方块");
    public static final IntValue PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK = integer("performance.scanBudgetPerTaskTick", 12_000, 1, 120_000,
            "单任务扫描预算\n单个任务每 tick 最多扫描的方块数，调高会同时增加方块更新与光照计算负担");
    public static final IntValue PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK = integer("performance.placeBudgetPerTaskTick", 8, 1, 64,
            "单任务放置预算\n单个任务每 tick 最多放置的方块数，调高会提高方块更新与网络同步压力");
    public static final IntValue PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK = integer("performance.globalScanBudgetPerTick", 24_000, 1, 240_000,
            "全服扫描预算\n全服所有任务每 tick 最多扫描的方块总数，与每任务预算共同限制服务器负载");
    public static final IntValue PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK = integer("performance.globalPlaceBudgetPerTick", 16, 1, 256,
            "全服放置预算\n全服所有任务每 tick 最多放置的方块总数，与每任务预算共同限制服务器负载");
    public static final IntValue PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS = integer("performance.randomPlacementAttempts", 32, 1, 128,
            "放置位置尝试次数\n为每个暗点寻找可放置位置时的最大尝试次数，调高更容易找到位置，但单次放置的耗时也会增加");

    public static final List<Value> CLIENT = List.of(
            NEARBY_AUTO_TORCH_ENABLED, NEARBY_AUTO_TORCH_LIGHT_THRESHOLD,
            NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT, NEARBY_AUTO_TORCH_SCAN_INTERVAL,
            NEARBY_AUTO_TORCH_DARK_CONFIRM, NEARBY_AUTO_TORCH_RETRY_DELAY,
            NEARBY_AUTO_TORCH_HORIZONTAL_RADIUS, NEARBY_AUTO_TORCH_ITEM,
            LIGHT_OVERLAY_ENABLED, LIGHT_OVERLAY_RENDER_THROUGH,
            LIGHT_OVERLAY_NUMBER_ROTATION,
            LIGHT_OVERLAY_HORIZONTAL_RANGE, LIGHT_OVERLAY_DOWN_RANGE, LIGHT_OVERLAY_UP_RANGE,
            LIGHT_OVERLAY_MODE,
            LIGHT_OVERLAY_DETECT_SLIMES, LIGHT_OVERLAY_DETECT_DROWNED,
            LIGHT_OVERLAY_SCAN_BUDGET, LIGHT_OVERLAY_REFRESH_INTERVAL, LIGHT_OVERLAY_DROWNED_BUDGET,
            LIGHT_OVERLAY_NEAR_LINE_WIDTH, LIGHT_OVERLAY_FAR_LINE_WIDTH,
            LIGHT_OVERLAY_ALWAYS_RISK_COLOR, LIGHT_OVERLAY_NIGHT_RISK_COLOR,
            LIGHT_OVERLAY_SAFE_COLOR, LIGHT_OVERLAY_SLIME_COLOR, LIGHT_OVERLAY_DROWNED_COLOR,
            LIGHT_OVERLAY_MARKER_ALPHA,
            SELECTION_OVERLAY_ENABLED, SELECTION_OVERLAY_LINES_ONLY,
            SELECTION_OVERLAY_SMOOTH_SPHERES, SELECTION_OVERLAY_NEAR_LINE_WIDTH,
            SELECTION_OVERLAY_FAR_LINE_WIDTH, SELECTION_OVERLAY_DRAFT_COLOR,
            SELECTION_OVERLAY_MAIN_COLOR, SELECTION_OVERLAY_EXCLUSION_COLOR,
            SELECTION_OVERLAY_FACE_ALPHA, SELECTION_OVERLAY_EXCLUSION_FACE_ALPHA,
            SELECTION_OVERLAY_SPHERE_LONGITUDE_SEGMENTS, SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS,
            SELECTION_TOOL_ITEM, SELECTION_TOOL_ENABLED, SELECTION_OVERLAY_SYMBOL_BUTTONS,
            TASK_DEFAULT_MAX_TORCHES,
            TASK_DEFAULT_MIN_SPACING, TASK_DEFAULT_LIGHT_THRESHOLD, TASK_DEFAULT_INCLUDE_SKY_LIGHT,
            TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES, TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES,
            TASK_PROGRESS_INTERVAL, TASK_DEFAULT_PLACEMENT_ITEM
    );

    public static final List<Value> SERVER = List.of(
            LIGHTING_TASK_ENABLED,
            LIMIT_MAX_BOX_AXIS_LENGTH, LIMIT_MAX_SPHERE_RADIUS, LIMIT_MAX_EXCLUSIONS,
            LIMIT_MAX_TORCHES_PER_TASK, LIMIT_ALLOW_UNLIMITED_TORCHES,
            LIMIT_MIN_SPACING, LIMIT_MAX_SPACING, LIMIT_MAX_CONCURRENT_TASKS,
            GAMEPLAY_SURVIVAL_CONSUMES_TORCHES, PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK,
            PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK, PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK,
            PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK, PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS
    );

    /** 服务端设置界面的分类，标题同时用于分组重置提示。 */
    public enum ServerCategory {
        RULES("screen.autotorch.server_config_rules_title", List.of("lightingTask.", "gameplay.")),
        LIMITS("screen.autotorch.server_config_limits_title", List.of("limits.")),
        PERFORMANCE("screen.autotorch.server_config_performance_title", List.of("performance."));

        private final String titleKey;
        private final List<String> prefixes;

        ServerCategory(String titleKey, List<String> prefixes) {
            this.titleKey = titleKey;
            this.prefixes = prefixes;
        }

        public String titleKey() { return titleKey; }

        /** 分类内全部服务端配置，供管理员界面与分组重置共用。 */
        public List<Value> definitions() {
            return SERVER.stream().filter(value -> prefixes.stream().anyMatch(value.key()::startsWith)).toList();
        }

        /** 按枚举名查找分组，供重置载荷在服务端还原界面选中的分组。 */
        public static ServerCategory byName(String name) {
            for (ServerCategory category : values()) {
                if (category.name().equals(name)) return category;
            }
            return null;
        }
    }

    /** 界面与分组重置共用分类，确保各组设置互不混杂。 */
    public enum ClientCategory {
        LIGHTING_TASK("screen.autotorch.lighting_task_all_settings_title",
                List.of("selectionOverlay.", "lightingTaskDefaults.")),
        LIGHT_OVERLAY("screen.autotorch.light_overlay_all_settings_title", List.of("lightOverlay.")),
        NEARBY_AUTO_TORCH("screen.autotorch.nearby_auto_torch_all_settings_title", List.of("nearbyAutoTorch."));

        private final String titleKey;
        private final List<String> prefixes;

        ClientCategory(String titleKey, List<String> prefixes) {
            this.titleKey = titleKey;
            this.prefixes = prefixes;
        }

        public String titleKey() { return titleKey; }

        /** 分类内全部配置，供客户端命令、分组重置与“全部设置”页共用。 */
        public List<Value> definitions() {
            return CLIENT.stream().filter(value -> prefixes.stream().anyMatch(value.key()::startsWith)).toList();
        }
    }

    private ConfigDefinitions() {
    }

    private static BooleanValue bool(String key, boolean defaultValue, String comment) {
        return new BooleanValue(key, defaultValue, comment);
    }

    private static IntValue integer(String key, int defaultValue, int minValue, int maxValue, String comment) {
        return new IntValue(key, defaultValue, minValue, maxValue, 1, comment);
    }

    private static IntValue stepped(String key, int defaultValue, int minValue, int maxValue, int step, String comment) {
        return new IntValue(key, defaultValue, minValue, maxValue, step, comment);
    }

    private static DoubleValue decimal(String key, double defaultValue, double minValue, double maxValue, String comment) {
        return new DoubleValue(key, defaultValue, minValue, maxValue, comment);
    }

    private static StringValue string(String key, String defaultValue, String comment) {
        return new StringValue(key, defaultValue, comment);
    }

    private static StringValue color(String key, String defaultValue, String comment) {
        // 颜色统一追加一行格式说明，界面上作为悬停提示的第二行。
        return string(key, defaultValue, comment + "\n格式为 6 位十六进制 RGB");
    }

    public sealed interface Value permits BooleanValue, IntValue, DoubleValue, StringValue {
        String key();

        /** 中文说明：首行是界面行标签，其余行是悬停提示与配置注释细节；可用 \n 分行。 */
        String comment();

        /** 界面显示用的语言键；对应语言的文本同样以首行作标签、其余行作提示。 */
        default String translationKey() {
            return "config.autotorch." + key();
        }

        /**
         * 供 Forge、NeoForge 使用的注释行；这两个加载器会自行追加 Default 与 Range。
         * 每行都带一个前导空格：TOML 写出器在 # 之后不补空格，加载器自带的说明也是这么写的。
         */
        default String[] commentLines() {
            String[] lines = comment().split("\n");
            String[] prefixed = new String[lines.length];
            for (int i = 0; i < lines.length; i++) {
                prefixed[i] = " " + lines[i];
            }
            return prefixed;
        }

        /** 供 Night Config 后端使用的注释行；这些后端没有默认值与范围说明，由这里补齐。 */
        default String[] fullCommentLines() {
            String[] lines = commentLines();
            String[] bounds = boundsLines();
            String[] all = Arrays.copyOf(lines, lines.length + bounds.length);
            System.arraycopy(bounds, 0, all, lines.length, bounds.length);
            return all;
        }

        /** 默认值的展示文字，供界面里的重置按钮提示使用。 */
        String defaultValueText();

        /** 取值范围说明行，与 {@link #commentLines()} 一样带前导空格；布尔与文本配置没有范围。 */
        default String[] rangeLines() {
            return new String[0];
        }

        /** 默认值与取值范围说明行，与 {@link #commentLines()} 一样带前导空格。 */
        default String[] boundsLines() {
            String[] ranges = rangeLines();
            String[] lines = Arrays.copyOf(ranges, ranges.length + 1);
            // 默认值固定在说明行最前面，范围行顺延到后面。
            System.arraycopy(lines, 0, lines, 1, ranges.length);
            lines[0] = " 默认值：" + defaultValueText();
            return lines;
        }
    }

    public record BooleanValue(String key, boolean defaultValue, String comment) implements Value {
        @Override
        public String defaultValueText() {
            return Boolean.toString(defaultValue);
        }
    }

    public record IntValue(String key, int defaultValue, int minValue, int maxValue, int step, String comment) implements Value {
        public IntValue {
            if (minValue > defaultValue || defaultValue > maxValue || step < 1
                    || (defaultValue - minValue) % step != 0 || (maxValue - minValue) % step != 0) {
                throw new IllegalArgumentException("Default value must be within the configured range: " + key);
            }
        }

        @Override
        public String defaultValueText() {
            return Integer.toString(defaultValue);
        }

        @Override
        public String[] rangeLines() {
            return new String[] { " 取值范围：" + minValue + " ~ " + maxValue + "，步进：" + step };
        }

        public int clamp(int value) {
            int bounded = Math.max(minValue, Math.min(maxValue, value));
            // 0 在部分配置中表示“不限/关闭”，正数不能因为步进对齐被折成 0；
            // 这类配置改成较大步进后，旧文件里的非步进值就近对齐，并至少保留一个步进。
            if (minValue == 0 && step > 1 && bounded > 0) {
                int steps = Math.max(1, Math.round(bounded / (float) step));
                return Math.min(maxValue, steps * step);
            }
            return minValue + (bounded - minValue) / step * step;
        }

        public boolean accepts(int value) {
            return value >= minValue && value <= maxValue && (value - minValue) % step == 0;
        }
    }

    public record DoubleValue(String key, double defaultValue, double minValue, double maxValue, String comment) implements Value {
        public DoubleValue {
            if (!Double.isFinite(minValue) || !Double.isFinite(maxValue) || !Double.isFinite(defaultValue)
                    || minValue > defaultValue || defaultValue > maxValue) {
                throw new IllegalArgumentException("Default value must be within the configured range: " + key);
            }
        }
        @Override
        public String defaultValueText() {
            return Double.toString(defaultValue);
        }

        @Override
        public String[] rangeLines() {
            return new String[] { " 取值范围：" + minValue + " ~ " + maxValue };
        }
        public double clamp(double value) {
            return Double.isFinite(value) ? Math.max(minValue, Math.min(maxValue, value)) : defaultValue;
        }
    }

    public record StringValue(String key, String defaultValue, String comment) implements Value {
        @Override
        public String defaultValueText() {
            return defaultValue;
        }

        public boolean accepts(String value) {
            return value != null && (!key.endsWith("Color") || value.matches("(?i)[0-9a-f]{6}"));
        }
    }
}

