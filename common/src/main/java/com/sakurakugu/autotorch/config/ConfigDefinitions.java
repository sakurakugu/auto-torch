package com.sakurakugu.autotorch.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;

/** 各加载器共用的配置目录：键名、默认值、取值范围与中文说明。 */
public final class ConfigDefinitions {
    public static final int HARD_MAX_BOX_AXIS_LENGTH = 321;
    public static final int HARD_MAX_TORCHES = 4096;

    public static final BooleanValue NEARBY_AUTO_TORCH_ENABLED = bool("nearbyAutoTorch.enabled", false,
            "是否启用附近自动插火把；开启后会在玩家周围约 2 格内的暗处自动放置火把");
    public static final IntValue NEARBY_AUTO_TORCH_LIGHT_THRESHOLD = integer("nearbyAutoTorch.lightThreshold", 4, 1, 16,
            "附近自动插火把的亮度阈值；亮度低于该值时才放置火把");
    public static final BooleanValue NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT = bool("nearbyAutoTorch.includeSkyLight", false,
            "亮度判断是否计入天空光；\ntrue 取方块光与天空光中的较大值，false 只判断方块光");
    public static final BooleanValue LIGHT_OVERLAY_ENABLED = bool("lightOverlay.enabled", false,
            "是否启用光照显示");
    public static final BooleanValue LIGHT_OVERLAY_RENDER_THROUGH = bool("lightOverlay.renderThrough", false,
            "是否开启透视渲染；开启后可以看到被方块遮挡的光照标记");
    public static final BooleanValue LIGHT_OVERLAY_NUMBER_ROTATION = bool("lightOverlay.numberRotation", true,
            "光照数字是否跟随视角旋转；false 时数字朝向固定");
    public static final IntValue LIGHT_OVERLAY_HORIZONTAL_RANGE = integer("lightOverlay.horizontalRange", 16, 1, 64,
            "以相机为中心的水平显示半径；调大会明显增加每帧的扫描量");
    public static final IntValue LIGHT_OVERLAY_DOWN_RANGE = integer("lightOverlay.downRange", 16, 0, 64,
            "相对相机向下扫描的格数；\n0 表示不向下显示，与 upRange 之和不超过 64");
    public static final IntValue LIGHT_OVERLAY_UP_RANGE = integer("lightOverlay.upRange", 4, 0, 64,
            "相对相机向上扫描的格数；\n0 表示不向上显示，与 downRange 之和不超过 64");
    public static final IntValue LIGHT_OVERLAY_MODE = integer("lightOverlay.mode", 0, 0, 2,
            "光照显示样式：0=X 标记，1=数字，2=方框数字");
    public static final BooleanValue LIGHT_OVERLAY_DETECT_SWAMP_SLIMES = bool("lightOverlay.detectSwampSlimes", false,
            "是否标记符合原版条件的沼泽史莱姆刷新位置；显示为紫色");
    public static final BooleanValue LIGHT_OVERLAY_DETECT_DROWNED = bool("lightOverlay.detectDrowned", false,
            "是否标记符合原版条件的溺尸刷新位置；需要逐帧检测水柱，显示为青色");
    public static final BooleanValue SELECTION_OVERLAY_ENABLED = bool("selectionOverlay.enabled", true,
            "是否显示照明范围与排除区的选区框");
    public static final BooleanValue SELECTION_OVERLAY_LINES_ONLY = bool("selectionOverlay.linesOnly", false,
            "选区显示方式：true 只画轮廓线，false 显示半透明面");
    public static final BooleanValue SELECTION_OVERLAY_SMOOTH_SPHERES = bool("selectionOverlay.smoothSpheres", false,
            "是否使用更平滑的球形选区显示；绘制更精细，开销也更高");
    public static final IntValue TASK_DEFAULT_MAX_TORCHES = integer("lightingTaskDefaults.maxTorches", 0, 0, HARD_MAX_TORCHES,
            "新建任务时默认的火把数量上限；\n0 表示不限，但仍受服务端 limits.maxTorchesPerTask 限制");
    public static final IntValue TASK_DEFAULT_MIN_SPACING = integer("lightingTaskDefaults.minSpacing", 8, 1, 12,
            "新建任务时默认的火把间距；指火把坐标之间的三维直线距离，设为 8 时同一直线上两个火把之间隔 7 格");
    public static final IntValue TASK_DEFAULT_LIGHT_THRESHOLD = integer("lightingTaskDefaults.lightThreshold", 0, 0, 15,
            "新建任务时默认的亮度阈值；处理方块光小于或等于该值的位置，\n0 表示只处理方块光为 0 的位置");
    public static final BooleanValue TASK_DEFAULT_UNDERGROUND_ONLY = bool("lightingTaskDefaults.undergroundOnly", true,
            "新建任务时是否默认只处理无天空光的位置");
    public static final BooleanValue TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES = bool("lightingTaskDefaults.creativeConsumeTorches", false,
            "新建任务时创造模式是否默认消耗背包中的火把");
    public static final BooleanValue TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES = bool("lightingTaskDefaults.survivalConsumeTorches", true,
            "新建任务时生存模式是否默认消耗背包中的火把；\n单人游戏按此项，多人游戏以服务端 gameplay.survivalConsumesTorches 为准");
    public static final BooleanValue TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED = bool("lightingTaskDefaults.woodenAxeSelectionEnabled", true,
            "是否启用木斧左键、右键选取 A、B 点；关闭后木斧恢复原版的破坏与方块交互");

    public static final IntValue LIMIT_MAX_BOX_AXIS_LENGTH = integer("limits.maxBoxAxisLength", HARD_MAX_BOX_AXIS_LENGTH, 1, HARD_MAX_BOX_AXIS_LENGTH,
            "服务端允许的长方体选区最大边长；\n调大会显著增加单个任务的扫描量与选区数据量");
    public static final IntValue LIMIT_MAX_SPHERE_RADIUS = integer("limits.maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS, 1, AreaZone.MAX_SPHERE_RADIUS,
            "服务端允许的球形选区最大半径；\n调大会显著增加单个任务的扫描量");
    public static final IntValue LIMIT_MAX_EXCLUSIONS = integer("limits.maxExclusions", StartLightingPayload.MAX_EXCLUSIONS, 0, StartLightingPayload.MAX_EXCLUSIONS,
            "单个任务允许提交的排除区数量上限；\n0 表示不允许提交排除区");
    public static final IntValue LIMIT_MAX_TORCHES_PER_TASK = integer("limits.maxTorchesPerTask", HARD_MAX_TORCHES, 1, HARD_MAX_TORCHES,
            "服务端强制限制的单任务火把数量上限；\n即使客户端把火把上限设为 0（不限），实际放置也不会超过该值");
    public static final BooleanValue LIMIT_ALLOW_UNLIMITED_TORCHES = bool("limits.allowUnlimitedTorches", true,
            "是否允许客户端把火把数量上限设为 0（不限）；\n关闭后客户端必须给出具体上限，实际仍受 limits.maxTorchesPerTask 限制");
    public static final BooleanValue LIGHTING_TASK_ENABLED = bool("lightingTask.enabled", true,
            "服务端是否允许运行区间自动插火把任务；\n关闭后所有玩家都无法提交或继续区间任务");
    public static final IntValue LIMIT_MIN_SPACING = integer("limits.minSpacing", 1, 1, 12,
            "服务端允许客户端设置的火把间距下限；\n下限越大火把越分散，越可能无法满足目标亮度");
    public static final IntValue LIMIT_MAX_SPACING = integer("limits.maxSpacing", 12, 1, 12,
            "服务端允许客户端设置的火把间距上限");
    public static final IntValue LIMIT_MAX_CONCURRENT_TASKS = integer("limits.maxConcurrentTasks", 64, 1, 1024,
            "全服可同时运行的区间任务数量上限；\n调高会成倍增加扫描与方块更新负担");
    public static final BooleanValue GAMEPLAY_SURVIVAL_CONSUMES_TORCHES = bool("gameplay.survivalConsumesTorches", true,
            "生存模式任务是否必须消耗玩家背包中的普通火把；\n关闭后生存模式可以凭空放置火把");
    public static final IntValue PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK = integer("performance.scanBudgetPerTaskTick", 12_000, 1, 120_000,
            "单个任务每 tick 最多扫描的方块数；\n调高会同时增加方块更新与光照计算负担");
    public static final IntValue PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK = integer("performance.placeBudgetPerTaskTick", 8, 1, 64,
            "单个任务每 tick 最多放置的火把数；\n调高会提高方块更新与网络同步压力");
    public static final IntValue PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK = integer("performance.globalScanBudgetPerTick", 24_000, 1, 240_000,
            "全服所有任务每 tick 最多扫描的方块总数；\n与每任务预算共同限制服务器负载");
    public static final IntValue PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK = integer("performance.globalPlaceBudgetPerTick", 16, 1, 256,
            "全服所有任务每 tick 最多放置的火把总数；\n与每任务预算共同限制服务器负载");
    public static final IntValue PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS = integer("performance.randomPlacementAttempts", 32, 1, 128,
            "为每个暗点寻找可放置位置时的最大尝试次数；\n调高更容易找到位置，但单次放置的耗时也会增加");

    public static final List<Value> CLIENT = Collections.unmodifiableList(Arrays.asList(
            NEARBY_AUTO_TORCH_ENABLED, NEARBY_AUTO_TORCH_LIGHT_THRESHOLD,
            NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT,
            LIGHT_OVERLAY_ENABLED, LIGHT_OVERLAY_RENDER_THROUGH,
            LIGHT_OVERLAY_NUMBER_ROTATION,
            LIGHT_OVERLAY_HORIZONTAL_RANGE, LIGHT_OVERLAY_DOWN_RANGE, LIGHT_OVERLAY_UP_RANGE,
            LIGHT_OVERLAY_MODE,
            LIGHT_OVERLAY_DETECT_SWAMP_SLIMES, LIGHT_OVERLAY_DETECT_DROWNED,
            SELECTION_OVERLAY_ENABLED, SELECTION_OVERLAY_LINES_ONLY,
            SELECTION_OVERLAY_SMOOTH_SPHERES, TASK_DEFAULT_MAX_TORCHES,
            TASK_DEFAULT_MIN_SPACING, TASK_DEFAULT_LIGHT_THRESHOLD, TASK_DEFAULT_UNDERGROUND_ONLY,
            TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES, TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES,
            TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED
    ));

    public static final List<Value> SERVER = Collections.unmodifiableList(Arrays.asList(
            LIGHTING_TASK_ENABLED,
            LIMIT_MAX_BOX_AXIS_LENGTH, LIMIT_MAX_SPHERE_RADIUS, LIMIT_MAX_EXCLUSIONS,
            LIMIT_MAX_TORCHES_PER_TASK, LIMIT_ALLOW_UNLIMITED_TORCHES,
            LIMIT_MIN_SPACING, LIMIT_MAX_SPACING, LIMIT_MAX_CONCURRENT_TASKS,
            GAMEPLAY_SURVIVAL_CONSUMES_TORCHES, PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK,
            PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK, PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK,
            PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK, PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS
    ));

    private ConfigDefinitions() {
    }

    private static BooleanValue bool(String key, boolean defaultValue, String comment) {
        return new BooleanValue(key, defaultValue, comment);
    }

    private static IntValue integer(String key, int defaultValue, int minValue, int maxValue, String comment) {
        return new IntValue(key, defaultValue, minValue, maxValue, comment);
    }

    public interface Value {
        String key();

        /** 中文用途说明，可用换行符分多行写特殊值含义与性能影响。 */
        String comment();

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

        /** 默认值与取值范围说明行，与 commentLines() 一样带前导空格。 */
        String[] boundsLines();
    }

    public static final class BooleanValue implements Value {
        private final String key;
        private final boolean defaultValue;
        private final String comment;

        public BooleanValue(String key, boolean defaultValue, String comment) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.comment = comment;
        }

        @Override
        public String key() {
            return key;
        }

        public boolean defaultValue() {
            return defaultValue;
        }

        @Override
        public String comment() {
            return comment;
        }

        @Override
        public String[] boundsLines() {
            return new String[] { " 默认值：" + defaultValue };
        }
    }

    public static final class IntValue implements Value {
        private final String key;
        private final int defaultValue;
        private final int minValue;
        private final int maxValue;
        private final String comment;

        public IntValue(String key, int defaultValue, int minValue, int maxValue, String comment) {
            if (minValue > defaultValue || defaultValue > maxValue) {
                throw new IllegalArgumentException("Default value must be within the configured range: " + key);
            }
            this.key = key;
            this.defaultValue = defaultValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.comment = comment;
        }

        @Override
        public String key() {
            return key;
        }

        public int defaultValue() {
            return defaultValue;
        }

        public int minValue() {
            return minValue;
        }

        public int maxValue() {
            return maxValue;
        }

        @Override
        public String comment() {
            return comment;
        }

        @Override
        public String[] boundsLines() {
            return new String[] { " 默认值：" + defaultValue, " 取值范围：" + minValue + " ~ " + maxValue };
        }

        public int clamp(int value) {
            return Math.max(minValue, Math.min(maxValue, value));
        }
    }
}
