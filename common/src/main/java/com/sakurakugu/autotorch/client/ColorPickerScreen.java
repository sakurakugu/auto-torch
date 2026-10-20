package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.client.ui.LegacyWidgetSprites;
import java.util.Locale;
import java.util.function.Consumer;
import com.sakurakugu.autotorch.client.Button;
import com.sakurakugu.autotorch.client.EditBox;
import com.sakurakugu.autotorch.client.Screen;
import net.minecraft.util.text.ITextComponent;

/**
 * 颜色编辑器：饱和度/明度方块与色相条组成渐变图，十六进制输入与它们双向同步；
 * 确认前不写入配置。
 */
final class ColorPickerScreen extends Screen {
    /** 顶部颜色预览条的高度。 */
    private static final int PREVIEW_HEIGHT = 20;
    /** 右侧色相条的宽度。 */
    private static final int HUE_BAR_WIDTH = 14;
    /** 方块与色相条之间的间距。 */
    private static final int GROUP_GAP = 8;
    /** 三原色条之间的间距，以及三原色条组与渐变方块之间的间距。 */
    private static final int RGB_BAR_GAP = 3;
    private static final int RGB_GROUP_GAP = 8;
    private static final int RGB_BAR_WIDTH = 10;
    /** 十六进制输入框与下方拖动区之间的间距，让两者稍微分开一些。 */
    private static final int HEX_GROUP_GAP = 11;
    /** 内容区顶部的纵坐标，位于标题和说明文字下方。 */
    private static final int PANEL_TOP = 38;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final Consumer<String> selected;
    private int rgb;
    /** 色相，0~1。 */
    private float hue;
    /** 饱和度，0~1。 */
    private float saturation;
    /** 明度，0~1。 */
    private float value;
    private String text;
    private boolean syncingHex;
    private EditBox hex;
    private Button confirm;

    ColorPickerScreen(Screen parent, String initial, String fallback, Consumer<String> selected) {
        super(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.color_picker_title"));
        this.parent = parent;
        this.selected = selected;
        text = initial.matches("(?i)[0-9a-f]{6}") ? initial : fallback;
        rgb = Integer.parseInt(text, 16);
        applyRgbToHsv();
    }

    @Override
    protected void init() {
        // 缩放窗口后会重建页面，旧控件还需从独立的绘制列表中移除。
        // 1.9.4 的按钮输入列表是 buttonList，绘制/文本框列表是 children()。
        buttonList.clear();
        children().clear();
        int contentWidth = Math.min(260, width - 32);
        int left = (width - contentWidth) / 2;
        // 高度不够时缩小渐变方块，保证底部按钮仍然可见。
        int rgbGroupWidth = RGB_BAR_WIDTH * 3 + RGB_BAR_GAP * 2;
        int availableSquareWidth = contentWidth - rgbGroupWidth - RGB_GROUP_GAP - GROUP_GAP - HUE_BAR_WIDTH;
        int squareSize = Math.max(48, Math.min(120, Math.min(height - 129, availableSquareWidth)));
        int squareY = PANEL_TOP + PREVIEW_HEIGHT + 6 + 18 + HEX_GROUP_GAP;
        int groupWidth = squareSize + GROUP_GAP + HUE_BAR_WIDTH;
        int totalWidth = rgbGroupWidth + RGB_GROUP_GAP + groupWidth;
        int totalLeft = left + Math.max(0, (contentWidth - totalWidth) / 2);
        int rgbLeft = totalLeft;
        int groupLeft = rgbLeft + rgbGroupWidth + RGB_GROUP_GAP;
        hex = new EditBox(font, left, PANEL_TOP + PREVIEW_HEIGHT + 7, contentWidth, 16,
                LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.color_picker_hex")));
        hex.setMaxLength(6);
        hex.setValue(text);
        addButton(hex);
        int rgbBarY = squareY + 10;
        int rgbBarHeight = squareSize - 10;
        addButton(new RgbBar(rgbLeft, rgbBarY, RGB_BAR_WIDTH, rgbBarHeight, 16, "R"));
        addButton(new RgbBar(rgbLeft + RGB_BAR_WIDTH + RGB_BAR_GAP, rgbBarY,
                RGB_BAR_WIDTH, rgbBarHeight, 8, "G"));
        addButton(new RgbBar(rgbLeft + (RGB_BAR_WIDTH + RGB_BAR_GAP) * 2, rgbBarY,
                RGB_BAR_WIDTH, rgbBarHeight, 0, "B"));
        addButton(new SaturationValueArea(groupLeft, squareY, squareSize, squareSize));
        addButton(new HueBar(groupLeft + squareSize + GROUP_GAP, squareY, HUE_BAR_WIDTH, squareSize));
        int buttonY = squareY + squareSize + 10;
        confirm = addButton(ButtonBuilder.builder(new net.minecraft.util.text.TextComponentTranslation("gui.done"),
                button -> selected.accept(String.format(Locale.ROOT, "%06X", rgb)))
                .bounds(left + contentWidth / 2 + 4, buttonY, contentWidth / 2 - 4, BUTTON_HEIGHT).build());
        addButton(ButtonBuilder.builder(new net.minecraft.util.text.TextComponentTranslation("gui.cancel"), button -> onClose())
                .bounds(left, buttonY, contentWidth / 2 - 4, BUTTON_HEIGHT).build());
        hex.setResponder(this::updateFromHex);
        updateFromHex(text);
    }

    /** 十六进制输入变化：合法时同步 RGB 与渐变图位置，非法时禁用确认按钮。 */
    private void updateFromHex(String textValue) {
        text = textValue;
        boolean valid = text.matches("(?i)[0-9a-f]{6}");
        confirm.active = valid;
        hex.setTextColor(valid ? 0xFFE0E0E0 : 0xFFFF5555);
        if (valid) {
            rgb = Integer.parseInt(text, 16);
            // 拖动渐变图时也会回写输入框，此时保留当前色相，避免灰色、黑色丢失色相。
            if (!syncingHex) applyRgbToHsv();
        }
    }

    /** 按当前 HSV 重新计算 RGB 并回填输入框，回填过程不反向覆盖色相。 */
    private void applyHsv() {
        rgb = hsvToRgb(hue, saturation, value);
        syncingHex = true;
        hex.setValue(String.format(Locale.ROOT, "%06X", rgb));
        syncingHex = false;
    }

    /** RGB 条调整后同步 HSV 状态和十六进制输入框。 */
    private void applyRgbChannels(int red, int green, int blue) {
        // 通道值限定在 0~255，避免越界值污染 rgb 的高位，进而让十六进制文本变长。
        rgb = clampChannel(red) << 16 | clampChannel(green) << 8 | clampChannel(blue);
        applyRgbToHsv();
        syncingHex = true;
        hex.setValue(String.format(Locale.ROOT, "%06X", rgb));
        syncingHex = false;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** 从 RGB 反推 HSV；无彩色时保留原色相，方便把明度拖到 0 后再拖回来。 */
    private void applyRgbToHsv() {
        float[] hsv = rgbToHsv(rgb);
        if (hsv[1] > 0) hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    /** RGB 转 HSV，返回 {色相, 饱和度, 明度}，范围都是 0~1；无彩色时色相为 0。 */
    static float[] rgbToHsv(int rgb) {
        float red = (rgb >> 16 & 255) / 255f;
        float green = (rgb >> 8 & 255) / 255f;
        float blue = (rgb & 255) / 255f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float saturation = max <= 0 ? 0 : delta / max;
        if (delta <= 0) return new float[] { 0, saturation, max };
        float hue;
        if (max == red) {
            hue = ((green - blue) / delta + 6f) % 6f;
        } else if (max == green) {
            hue = (blue - red) / delta + 2f;
        } else {
            hue = (red - green) / delta + 4f;
        }
        return new float[] { hue / 6f, saturation, max };
    }

    /** HSV 转 24 位 RGB，色相、饱和度、明度都限定在 0~1。 */
    static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue % 1f + 1f) % 1f;
        float s = Math.max(0f, Math.min(1f, saturation));
        float v = Math.max(0f, Math.min(1f, value));
        if (s <= 0) {
            int gray = Math.round(v * 255);
            return gray << 16 | gray << 8 | gray;
        }
        float scaled = h * 6f;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * fraction);
        float t = v * (1f - s * (1f - fraction));
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0: { red = v; green = t; blue = p; } break;
            case 1: { red = q; green = v; blue = p; } break;
            case 2: { red = p; green = v; blue = t; } break;
            case 3: { red = p; green = q; blue = v; } break;
            case 4: { red = t; green = p; blue = v; } break;
            default: { red = v; green = p; blue = q; } break;
        }
        return Math.round(red * 255) << 16 | Math.round(green * 255) << 8 | Math.round(blue * 255);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        renderBackground();
        super.render(mouseX, mouseY, partialTick);
        for (Object child : children()) {
            if (child instanceof EditBox) ((EditBox) child).render(mouseX, mouseY, partialTick);
        }
        drawCenteredString(font, LegacyText.string(title), width / 2, 12, 0xFFFFFFFF);
        drawCenteredString(font, LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.color_picker_hint")),
                width / 2, 25, 0xFFB0B0B0);
        int contentWidth = Math.min(260, width - 32);
        int left = (width - contentWidth) / 2;
        fill(left, PANEL_TOP, left + contentWidth, PANEL_TOP + PREVIEW_HEIGHT, 0xFF000000 | rgb);
        LegacyWidgetSprites.outline(left, PANEL_TOP, contentWidth, PREVIEW_HEIGHT, 0xFF888888);
    }

    @Override public void onClose() { minecraft.displayGuiScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    /**
     * 饱和度/明度方块：横向为饱和度、纵向为明度。每一列画一条从纯色到黑色的竖直渐变，
     * 合起来就是标准的 HSV 渐变图。
     */
    private final class SaturationValueArea extends Button {
        private boolean dragging;

        SaturationValueArea(int x, int y, int width, int height) {
            super(x, y, width, height,
                    LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.color_picker_saturation_value")),
                    button -> { });
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            for (int column = 0; column < getWidth(); column++) {
                float columnSaturation = getWidth() <= 1 ? 0 : column / (float) (getWidth() - 1);
                int pure = hsvToRgb(hue, columnSaturation, 1f);
                fillGradient(xPosition + column, yPosition, xPosition + column + 1, (yPosition + height),
                        0xFF000000 | pure, 0xFF000000);
            }
            LegacyWidgetSprites.outline(xPosition, yPosition, getWidth(), height, 0xFF888888);
            int markerX = xPosition + Math.round(saturation * Math.max(0, getWidth() - 1));
            int markerY = yPosition + Math.round((1f - value) * Math.max(0, height - 1));
            LegacyWidgetSprites.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);
            LegacyWidgetSprites.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isValidClickButton(button) || !isMouseOver(mouseX, mouseY)) return false;
            dragging = true;
            updateFromMouse(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!dragging || !isValidClickButton(button)) return false;
            updateFromMouse(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!dragging || !isValidClickButton(button)) return false;
            dragging = false;
            return true;
        }

        private void updateFromMouse(double mouseX, double mouseY) {
            saturation = (float) clamp01((mouseX - xPosition) / Math.max(1.0, getWidth() - 1));
            value = 1f - (float) clamp01((mouseY - yPosition) / Math.max(1.0, height - 1));
            applyHsv();
        }
    }

    /** 竖向色相条：从上到下依次为红、黄、绿、青、蓝、洋红。 */
    private final class HueBar extends Button {
        private boolean dragging;

        HueBar(int x, int y, int width, int height) {
            super(x, y, width, height, LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.color_picker_hue")),
                    button -> { });
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            for (int segment = 0; segment < 6; segment++) {
                int top = yPosition + segment * height / 6;
                int bottom = yPosition + (segment + 1) * height / 6;
                fillGradient(xPosition, top, (xPosition + getWidth()), bottom,
                        0xFF000000 | hsvToRgb(segment / 6f, 1f, 1f),
                        0xFF000000 | hsvToRgb((segment + 1) / 6f, 1f, 1f));
            }
            LegacyWidgetSprites.outline(xPosition, yPosition, getWidth(), height, 0xFF888888);
            int markerY = yPosition + Math.round(hue * Math.max(0, height - 1));
            fill(xPosition - 2, markerY - 1, (xPosition + getWidth()) + 2, markerY + 1, 0xFF000000);
            fill(xPosition - 1, markerY, (xPosition + getWidth()) + 1, markerY + 1, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isValidClickButton(button) || !isMouseOver(mouseX, mouseY)) return false;
            dragging = true;
            updateFromMouse(mouseY);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!dragging || !isValidClickButton(button)) return false;
            updateFromMouse(mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!dragging || !isValidClickButton(button)) return false;
            dragging = false;
            return true;
        }

        private void updateFromMouse(double mouseY) {
            hue = (float) clamp01((mouseY - yPosition) / Math.max(1.0, height - 1));
            applyHsv();
        }
    }

    /** 单个可拖动的 RGB 通道条，顶部为该通道的最大值，底部为零。 */
    private final class RgbBar extends Button {
        private final int shift;
        private final String label;
        private boolean dragging;

        RgbBar(int x, int y, int width, int height, int shift, String label) {
            super(x, y, width, height, LegacyText.string(new net.minecraft.util.text.TextComponentString(label)), button -> { });
            this.shift = shift;
            this.label = label;
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            int channel = (rgb >> shift) & 255;
            int other = rgb & ~(255 << shift);
            int top = 0xFF000000 | other | (255 << shift);
            int bottom = 0xFF000000 | other;
            fillGradient(xPosition, yPosition, (xPosition + getWidth()), (yPosition + height), top, bottom);
            LegacyWidgetSprites.outline(xPosition, yPosition, getWidth(), height, 0xFF888888);
            drawCenteredString(font, LegacyText.string(new net.minecraft.util.text.TextComponentString(label)), xPosition + getWidth() / 2, yPosition - 9, 0xFFE0E0E0);
            int markerY = yPosition + Math.round((1f - channel / 255f) * Math.max(0, height - 1));
            fill(xPosition - 2, markerY - 1, (xPosition + getWidth()) + 2, markerY + 1, 0xFF000000);
            fill(xPosition - 1, markerY, (xPosition + getWidth()) + 1, markerY + 1, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isValidClickButton(button) || !isMouseOver(mouseX, mouseY)) return false;
            dragging = true;
            updateFromMouse(mouseY);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!dragging || !isValidClickButton(button)) return false;
            updateFromMouse(mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!dragging || !isValidClickButton(button)) return false;
            dragging = false;
            return true;
        }

        private void updateFromMouse(double mouseY) {
            int value = channelFromTopDownRatio(
                    (mouseY - yPosition) / Math.max(1.0, height - 1));
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            if (shift == 16) red = value;
            else if (shift == 8) green = value;
            else blue = value;
            applyRgbChannels(red, green, blue);
        }
    }

    /**
     * 把通道条上的纵向比例换算成 0~255 的通道值：0 为顶部（取最大值 255），1 为底部（取 0）。
     * 注意必须先把比例取反再乘 255，否则会算出负数并污染其余通道。
     */
    static int channelFromTopDownRatio(double ratio) {
        return (int) Math.round((1.0 - clamp01(ratio)) * 255.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
