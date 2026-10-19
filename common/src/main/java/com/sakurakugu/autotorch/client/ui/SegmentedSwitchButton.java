package com.sakurakugu.autotorch.client.ui;

import com.sakurakugu.autotorch.client.LegacyText;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import com.sakurakugu.autotorch.client.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

/** 多档开关：未选档铺滑动条内部底色，选中档保持按钮原色，悬停档显示白框。 */
public final class SegmentedSwitchButton extends Button {
    private static final ResourceLocation BUTTON = new ResourceLocation("minecraft", "widget/button");
    private static final ResourceLocation BUTTON_DISABLED = new ResourceLocation("minecraft", "widget/button_disabled");
    private static final ResourceLocation SLIDER = new ResourceLocation("minecraft", "widget/slider");
    // 悬停档位的白色描边。
    private static final int HOVERED_OUTLINE = 0xFFFFFFFF;
    // 档位之间沿用之前的分隔线。
    private static final int SEPARATOR = 0x50000000;

    private final List<ITextComponent> labels;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;
    private boolean dragging;

    public SegmentedSwitchButton(int x, int y, int width, int height, List<ITextComponent> labels,
                                 IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, width, height, LegacyText.string(joinLabels(labels)), ignored -> { });
        if (labels.size() < 2) throw new IllegalArgumentException("档位至少需要两个");
        this.labels = com.google.common.collect.ImmutableList.copyOf(labels);
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    /** 把所有档位文字拼成读屏描述。 */
    private static ITextComponent joinLabels(List<ITextComponent> labels) {
        net.minecraft.util.text.TextComponentString joined = new net.minecraft.util.text.TextComponentString("");
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) joined.appendText(" / ");
            joined.appendSibling(labels.get(index));
        }
        return joined;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        // 滑动条背景向外扩展铺满整个控件，外框只保留滑动条自身的一层细边框，不再和按钮外框叠加。
        LegacyWidgetSprites.blit(SLIDER,
                x, y, getWidth(), height);

        int selected = selectedIndex.getAsInt();

        // 分隔线上下填满控件内部，不留 1px 空隙；选中档两侧由按钮边框自然分隔。
        for (int index = 1; index < labels.size(); index++) {
            if (index == selected || index - 1 == selected) {
                continue;
            }
            int separatorX = segment(index).left();
            fill(separatorX, y + 1, separatorX + 1, (y + height) - 1, SEPARATOR);
        }

        // 选中档整块重绘原版按钮贴图，始终保持完整按钮样式。
        Segment selectedSegment = segment(selected);
        LegacyWidgetSprites.blit(active ? BUTTON : BUTTON_DISABLED,
                selectedSegment.left(), y, selectedSegment.width(), height);

        // 只在鼠标悬停时给指针所在档画白框。
        int hovered = hoveredIndex(mouseX, mouseY);
        if (hovered >= 0) {
            Segment hoveredSegment = segment(hovered);
            LegacyWidgetSprites.outline(hoveredSegment.left(), y,
                    hoveredSegment.width(), height, HOVERED_OUTLINE);
        }

        net.minecraft.client.gui.FontRenderer font = Minecraft.getInstance().fontRenderer;
        for (int index = 0; index < labels.size(); index++) {
            Segment segment = segment(index);
            ITextComponent label = labels.get(index);
            int textX = segment.left() + (segment.width() - font.getStringWidth(LegacyText.string(label))) / 2;
            drawString(font, LegacyText.string(label), textX, y + 6, active ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || button != 0
                || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        updateSelection(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) return false;
        updateSelection(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    /** 第 index 档的水平范围，各档按数量均分。 */
    private Segment segment(int index) {
        int left = x + getWidth() * index / labels.size();
        int right = x + getWidth() * (index + 1) / labels.size();
        return new Segment(left, right);
    }

    /** 指针所在的档位下标，未悬停时为 -1。 */
    private int hoveredIndex(int mouseX, int mouseY) {
        return isMouseOver(mouseX, mouseY) ? indexAt(mouseX) : -1;
    }

    /** 按各档左边界定位指针档位，越过左端归入首档、越过右端归入末档。 */
    private int indexAt(double mouseX) {
        for (int index = labels.size() - 1; index > 0; index--) {
            if (mouseX >= segment(index).left()) return index;
        }
        return 0;
    }

    /** 拖动时按指针所在档位直接选档，重复选同一档不触发回调。 */
    private void updateSelection(double mouseX) {
        int index = indexAt(mouseX);
        if (index != selectedIndex.getAsInt()) onSelect.accept(index);
    }

    private static final class Segment {
        private final int left;
        private final int right;
        public Segment(int left, int right) {
            this.left = left;
            this.right = right;
        }
        public int left() { return left; }
        public int right() { return right; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Segment)) return false;
            Segment that = (Segment) other;
            return java.util.Objects.equals(left, that.left) && java.util.Objects.equals(right, that.right);
        }
        @Override public int hashCode() { return java.util.Objects.hash(left, right); }

        private int width() {
            return right - left;
        }
    }
}
