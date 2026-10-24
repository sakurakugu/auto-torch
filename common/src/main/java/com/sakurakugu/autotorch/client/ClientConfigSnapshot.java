package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;

/** 不可变客户端配置快照：只在安装、修改或重载时读取后端并计算派生值。 */
public final class ClientConfigSnapshot {
    private final Map<Value, Object> values;
    public final boolean nearbyAutoTorchEnabled;
    public final int nearbyAutoTorchLightThreshold;
    public final boolean nearbyAutoTorchIncludeSkyLight;
    public final int nearbyAutoTorchScanInterval;
    public final int nearbyAutoTorchDarkConfirm;
    public final int nearbyAutoTorchRetryDelay;
    public final int nearbyAutoTorchHorizontalRadius;
    public final String nearbyAutoTorchItem;
    public final boolean lightOverlayEnabled;
    public final boolean lightOverlayRenderThrough;
    public final boolean lightOverlayNumberRotation;
    public final int lightOverlayHorizontalRange;
    public final int lightOverlayDownRange;
    public final int lightOverlayUpRange;
    public final int lightOverlayMode;
    public final boolean lightOverlayDetectSlimes;
    public final boolean lightOverlayDetectDrowned;
    public final int lightOverlayScanBudget;
    public final int lightOverlayRefreshInterval;
    public final int lightOverlayDrownedBudget;
    public final double lightOverlayNearLineWidth;
    public final double lightOverlayFarLineWidth;
    public final String lightOverlayAlwaysRiskColor;
    public final String lightOverlayNightRiskColor;
    public final String lightOverlaySafeColor;
    public final String lightOverlaySlimeColor;
    public final String lightOverlayDrownedColor;
    public final int lightOverlayMarkerAlpha;
    public final boolean selectionOverlayEnabled;
    public final boolean selectionOverlayLinesOnly;
    public final boolean selectionOverlaySmoothSpheres;
    public final double selectionOverlayNearLineWidth;
    public final double selectionOverlayFarLineWidth;
    public final String selectionOverlayDraftColor;
    public final String selectionOverlayMainColor;
    public final String selectionOverlayExclusionColor;
    public final int selectionOverlayFaceAlpha;
    public final int selectionOverlayExclusionFaceAlpha;
    public final int selectionOverlaySphereLongitudeSegments;
    public final int selectionOverlaySphereLatitudeSegments;
    public final boolean selectionToolEnabled;
    public final String selectionToolItem;
    public final int taskDefaultMaxTorches;
    public final int taskDefaultMinSpacing;
    public final int taskDefaultLightThreshold;
    public final boolean taskDefaultIncludeSkyLight;
    public final boolean taskDefaultCreativeConsumesTorches;
    public final boolean taskDefaultSurvivalConsumesTorches;
    public final int taskProgressInterval;
    public final String taskDefaultPlacementItem;
    public final int lightOverlayAlwaysRiskColorRgb;
    public final int lightOverlayNightRiskColorRgb;
    public final int lightOverlaySafeColorRgb;
    public final int lightOverlaySlimeColorRgb;
    public final int lightOverlayDrownedColorRgb;
    public final int selectionOverlayDraftColorRgb;
    public final int selectionOverlayMainColorRgb;
    public final int selectionOverlayExclusionColorRgb;
    private final double[] longitudeCos;
    private final double[] longitudeSin;
    private final double[] latitudeCos;
    private final double[] latitudeSin;

    private ClientConfigSnapshot(Map<Value, Object> values) {
        this.values = Collections.unmodifiableMap(values);
        nearbyAutoTorchEnabled = bool(NEARBY_AUTO_TORCH_ENABLED);
        nearbyAutoTorchLightThreshold = integer(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD);
        nearbyAutoTorchIncludeSkyLight = bool(NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT);
        nearbyAutoTorchScanInterval = integer(NEARBY_AUTO_TORCH_SCAN_INTERVAL);
        nearbyAutoTorchDarkConfirm = integer(NEARBY_AUTO_TORCH_DARK_CONFIRM);
        nearbyAutoTorchRetryDelay = integer(NEARBY_AUTO_TORCH_RETRY_DELAY);
        nearbyAutoTorchHorizontalRadius = integer(NEARBY_AUTO_TORCH_HORIZONTAL_RADIUS);
        nearbyAutoTorchItem = string(NEARBY_AUTO_TORCH_ITEM);
        lightOverlayEnabled = bool(LIGHT_OVERLAY_ENABLED);
        lightOverlayRenderThrough = bool(LIGHT_OVERLAY_RENDER_THROUGH);
        lightOverlayNumberRotation = bool(LIGHT_OVERLAY_NUMBER_ROTATION);
        lightOverlayHorizontalRange = integer(LIGHT_OVERLAY_HORIZONTAL_RANGE);
        lightOverlayDownRange = integer(LIGHT_OVERLAY_DOWN_RANGE);
        lightOverlayUpRange = integer(LIGHT_OVERLAY_UP_RANGE);
        lightOverlayMode = integer(LIGHT_OVERLAY_MODE);
        lightOverlayDetectSlimes = bool(LIGHT_OVERLAY_DETECT_SLIMES);
        lightOverlayDetectDrowned = bool(LIGHT_OVERLAY_DETECT_DROWNED);
        lightOverlayScanBudget = integer(LIGHT_OVERLAY_SCAN_BUDGET);
        lightOverlayRefreshInterval = integer(LIGHT_OVERLAY_REFRESH_INTERVAL);
        lightOverlayDrownedBudget = integer(LIGHT_OVERLAY_DROWNED_BUDGET);
        lightOverlayNearLineWidth = decimal(LIGHT_OVERLAY_NEAR_LINE_WIDTH);
        lightOverlayFarLineWidth = decimal(LIGHT_OVERLAY_FAR_LINE_WIDTH);
        lightOverlayAlwaysRiskColor = string(LIGHT_OVERLAY_ALWAYS_RISK_COLOR);
        lightOverlayNightRiskColor = string(LIGHT_OVERLAY_NIGHT_RISK_COLOR);
        lightOverlaySafeColor = string(LIGHT_OVERLAY_SAFE_COLOR);
        lightOverlaySlimeColor = string(LIGHT_OVERLAY_SLIME_COLOR);
        lightOverlayDrownedColor = string(LIGHT_OVERLAY_DROWNED_COLOR);
        lightOverlayMarkerAlpha = integer(LIGHT_OVERLAY_MARKER_ALPHA);
        selectionOverlayEnabled = bool(SELECTION_OVERLAY_ENABLED);
        selectionOverlayLinesOnly = bool(SELECTION_OVERLAY_LINES_ONLY);
        selectionOverlaySmoothSpheres = bool(SELECTION_OVERLAY_SMOOTH_SPHERES);
        selectionOverlayNearLineWidth = decimal(SELECTION_OVERLAY_NEAR_LINE_WIDTH);
        selectionOverlayFarLineWidth = decimal(SELECTION_OVERLAY_FAR_LINE_WIDTH);
        selectionOverlayDraftColor = string(SELECTION_OVERLAY_DRAFT_COLOR);
        selectionOverlayMainColor = string(SELECTION_OVERLAY_MAIN_COLOR);
        selectionOverlayExclusionColor = string(SELECTION_OVERLAY_EXCLUSION_COLOR);
        selectionOverlayFaceAlpha = integer(SELECTION_OVERLAY_FACE_ALPHA);
        selectionOverlayExclusionFaceAlpha = integer(SELECTION_OVERLAY_EXCLUSION_FACE_ALPHA);
        selectionOverlaySphereLongitudeSegments = integer(SELECTION_OVERLAY_SPHERE_LONGITUDE_SEGMENTS);
        selectionOverlaySphereLatitudeSegments = integer(SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS);
        selectionToolEnabled = bool(SELECTION_TOOL_ENABLED);
        selectionToolItem = string(SELECTION_TOOL_ITEM);
        taskDefaultMaxTorches = integer(TASK_DEFAULT_MAX_TORCHES);
        taskDefaultMinSpacing = integer(TASK_DEFAULT_MIN_SPACING);
        taskDefaultLightThreshold = integer(TASK_DEFAULT_LIGHT_THRESHOLD);
        taskDefaultIncludeSkyLight = bool(TASK_DEFAULT_INCLUDE_SKY_LIGHT);
        taskDefaultCreativeConsumesTorches = bool(TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES);
        taskDefaultSurvivalConsumesTorches = bool(TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES);
        taskProgressInterval = integer(TASK_PROGRESS_INTERVAL);
        taskDefaultPlacementItem = string(TASK_DEFAULT_PLACEMENT_ITEM);
        lightOverlayAlwaysRiskColorRgb = Integer.parseInt(lightOverlayAlwaysRiskColor, 16);
        lightOverlayNightRiskColorRgb = Integer.parseInt(lightOverlayNightRiskColor, 16);
        lightOverlaySafeColorRgb = Integer.parseInt(lightOverlaySafeColor, 16);
        lightOverlaySlimeColorRgb = Integer.parseInt(lightOverlaySlimeColor, 16);
        lightOverlayDrownedColorRgb = Integer.parseInt(lightOverlayDrownedColor, 16);
        selectionOverlayDraftColorRgb = Integer.parseInt(selectionOverlayDraftColor, 16);
        selectionOverlayMainColorRgb = Integer.parseInt(selectionOverlayMainColor, 16);
        selectionOverlayExclusionColorRgb = Integer.parseInt(selectionOverlayExclusionColor, 16);
        int longitude = selectionOverlaySphereLongitudeSegments;
        int latitude = selectionOverlaySphereLatitudeSegments;
        longitudeCos = new double[longitude + 1];
        longitudeSin = new double[longitude + 1];
        latitudeCos = new double[latitude + 1];
        latitudeSin = new double[latitude + 1];
        for (int i = 0; i <= longitude; i++) {
            double angle = Math.PI * 2.0 * i / longitude;
            longitudeCos[i] = Math.cos(angle);
            longitudeSin[i] = Math.sin(angle);
        }
        for (int i = 0; i <= latitude; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * i / latitude;
            latitudeCos[i] = Math.cos(angle);
            latitudeSin[i] = Math.sin(angle);
        }
    }

    /** 后端尚未加载时只使用默认值，不访问加载器的配置对象。 */
    public static ClientConfigSnapshot defaults() { return load(null); }

    public static ClientConfigSnapshot load(ConfigBackend backend) {
        Map<Value, Object> values = new HashMap<>();
        for (Value definition : CLIENT) {
            Object result;
            if (definition instanceof BooleanValue) {
                BooleanValue value = (BooleanValue) definition;
                result = backend == null ? value.defaultValue() : backend.getBoolean(value.key(), value.defaultValue());
            } else if (definition instanceof IntValue) {
                IntValue value = (IntValue) definition;
                result = value.clamp(backend == null ? value.defaultValue() : backend.getInt(value.key(), value.defaultValue()));
            } else if (definition instanceof DoubleValue) {
                DoubleValue value = (DoubleValue) definition;
                double raw = backend == null ? value.defaultValue() : backend.getDouble(value.key(), value.defaultValue());
                result = Double.isFinite(raw) ? value.clamp(raw) : value.defaultValue();
            } else {
                StringValue value = (StringValue) definition;
                String raw = backend == null ? value.defaultValue() : backend.getString(value.key(), value.defaultValue());
                result = raw != null && value.accepts(raw) ? raw : value.defaultValue();
            }
            values.put(definition, result);
        }
        return new ClientConfigSnapshot(values);
    }

    public Object get(Value value) { return values.get(value); }
    public boolean bool(BooleanValue value) { return (Boolean) get(value); }
    public int integer(IntValue value) { return (Integer) get(value); }
    public double decimal(DoubleValue value) { return (Double) get(value); }
    public String string(StringValue value) { return (String) get(value); }
    public double longitudeCos(int index) { return longitudeCos[index]; }
    public double longitudeSin(int index) { return longitudeSin[index]; }
    public double latitudeCos(int index) { return latitudeCos[index]; }
    public double latitudeSin(int index) { return latitudeSin[index]; }
}

