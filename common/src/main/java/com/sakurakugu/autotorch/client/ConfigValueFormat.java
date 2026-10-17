package com.sakurakugu.autotorch.client;

import java.math.BigDecimal;

import com.sakurakugu.autotorch.config.ConfigDefinitions;

/** 数值配置的滑块换算：对齐步进、夹紧范围并生成滑块上的显示文本。 */
final class ConfigValueFormat {
    /** 小数配置的滑块步进，保证拖动结果都是易读的 0.25 的倍数。 */
    static final double DECIMAL_STEP = 0.25;

    private ConfigValueFormat() {
    }

    /** 数值设置在区间内的比例，用于摆放滑柄；越界值会被夹到 0~1。 */
    static double fraction(ConfigDefinitions.Value definition, double value) {
        double minimum = minimum(definition);
        double maximum = maximum(definition);
        return Math.max(0.0, Math.min(1.0, (value - minimum) / (maximum - minimum)));
    }

    /** 滑块比例对应的具体数值，已对齐步进并夹紧到配置范围。 */
    static double valueAt(ConfigDefinitions.Value definition, double fraction) {
        double minimum = minimum(definition);
        return snap(definition, minimum + fraction * (maximum(definition) - minimum));
    }

    /** 把原始数值对齐到步进并夹紧到范围；整数设置取整，小数设置保留 0.25 的倍数。 */
    static double snap(ConfigDefinitions.Value definition, double raw) {
        double minimum = minimum(definition);
        double step = step(definition);
        double snapped = minimum + Math.round((raw - minimum) / step) * step;
        snapped = Math.max(minimum, Math.min(maximum(definition), snapped));
        return definition instanceof ConfigDefinitions.IntValue ? Math.rint(snapped) : Math.round(snapped / step) * step;
    }

    /** 滑块上显示的数值：整数直接输出，小数去掉多余的 0（例如 2.50 显示为 2.5）。 */
    static String format(ConfigDefinitions.Value definition, double value) {
        if (definition instanceof ConfigDefinitions.IntValue) return Integer.toString((int) Math.round(value));
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static double minimum(ConfigDefinitions.Value definition) {
        if (definition instanceof ConfigDefinitions.IntValue value) return value.minValue();
        if (definition instanceof ConfigDefinitions.DoubleValue value) return value.minValue();
        throw new IllegalArgumentException("不是数值设置：" + definition.key());
    }

    private static double maximum(ConfigDefinitions.Value definition) {
        if (definition instanceof ConfigDefinitions.IntValue value) return value.maxValue();
        if (definition instanceof ConfigDefinitions.DoubleValue value) return value.maxValue();
        throw new IllegalArgumentException("不是数值设置：" + definition.key());
    }

    private static double step(ConfigDefinitions.Value definition) {
        if (definition instanceof ConfigDefinitions.IntValue value) return value.step();
        if (definition instanceof ConfigDefinitions.DoubleValue) return DECIMAL_STEP;
        throw new IllegalArgumentException("不是数值设置：" + definition.key());
    }
}
