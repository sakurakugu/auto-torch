package com.sakurakugu.autotorch.client.ui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 多档开关：未选档铺滑动条内部底色，选中档保持按钮原色，悬停档显示白框。 */
public final class SegmentedSwitchButton extends Button {
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation SLIDER = ResourceLocation.withDefaultNamespace("widget/slider");
    // 悬停档位的白色描边。
    private static final int HOVERED_OUTLINE = 0xFFFFFFFF;
    // 档位之间沿用之前的分隔线。
    private static final int SEPARATOR = 0x50000000;

    private final List<Component> labels;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;
    private boolean dragging;

    public SegmentedSwitchButton(int x, int y, int width, int height, List<Component> labels,
                                 IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, width, height, joinLabels(labels), ignored -> { }, DEFAULT_NARRATION);
        if (labels.size() < 2) throw new IllegalArgumentException("档位至少需要两个");
        this.labels = List.copyOf(labels);
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    /** 把所有档位文字拼成读屏描述。 */
    private static Component joinLabels(List<Component> labels) {
        var joined = Component.empty();
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) joined.append(" / ");
            joined.append(labels.get(index));
        }
        return joined;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 滑动条背景向外扩展铺满整个控件，外框只保留滑动条自身的一层细边框，不再和按钮外框叠加。
        graphics.blitSprite(SLIDER,
                getX(), getY(), getWidth(), getHeight());

        int selected = selectedIndex.getAsInt();

        // 分隔线上下填满控件内部，不留 1px 空隙；选中档两侧由按钮边框自然分隔。
        for (int index = 1; index < labels.size(); index++) {
            if (index == selected || index - 1 == selected) {
                continue;
            }
            int separatorX = segment(index).left();
            graphics.fill(separatorX, getY() + 1, separatorX + 1, getBottom() - 1, SEPARATOR);
        }

        // 选中档整块重绘原版按钮贴图，始终保持完整按钮样式。
        Segment selectedSegment = segment(selected);
        graphics.blitSprite(active ? BUTTON : BUTTON_DISABLED,
                selectedSegment.left(), getY(), selectedSegment.width(), getHeight());

        // 只在鼠标悬停时给指针所在档画白框。
        int hovered = hoveredIndex(mouseX, mouseY);
        if (hovered >= 0) {
            Segment hoveredSegment = segment(hovered);
            graphics.renderOutline(hoveredSegment.left(), getY(),
                    hoveredSegment.width(), getHeight(), HOVERED_OUTLINE);
        }

        var font = Minecraft.getInstance().font;
        for (int index = 0; index < labels.size(); index++) {
            Segment segment = segment(index);
            Component label = labels.get(index);
            int textX = segment.left() + (segment.width() - font.width(label)) / 2;
            graphics.drawString(font, label, textX, getY() + 6, active ? 0xFFFFFFFF : 0xFFA0A0A0);
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
        int left = getX() + getWidth() * index / labels.size();
        int right = getX() + getWidth() * (index + 1) / labels.size();
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

    private record Segment(int left, int right) {
        private int width() {
            return right - left;
        }
    }
}
