package com.sakurakugu.autotorch.client.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 输入框后的选择按钮：直接显示当前物品或颜色，保留原版按钮的焦点反馈。 */
public final class ValuePickerButton extends Button {
    private final boolean color;
    private final Supplier<String> value;

    public ValuePickerButton(int x, int y, boolean color, Supplier<String> value, OnPress onPress) {
        super(x, y, 20, 20, Component.translatable(color
                ? "screen.autotorch.pick_color" : "screen.autotorch.pick_item"), onPress, DEFAULT_NARRATION);
        this.color = color;
        this.value = value;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String sprite = !active ? "widget/button_disabled"
                : isHoveredOrFocused() ? "widget/button_highlighted" : "widget/button";
        graphics.blitSprite(ResourceLocation.withDefaultNamespace(sprite),
                getX(), getY(), getWidth(), getHeight());
        String current = value.get();
        if (color && current.matches("(?i)[0-9a-f]{6}")) {
            graphics.fill(getX() + 4, getY() + 4, getX() + 16, getY() + 16,
                    0xFF000000 | Integer.parseInt(current, 16));
        } else if (!color) {
            ResourceLocation id = ResourceLocation.tryParse(current);
            ItemStack stack = id == null ? ItemStack.EMPTY
                    : BuiltInRegistries.ITEM.getOptional(id).map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                graphics.renderFakeItem(stack, getX() + 2, getY() + 2);
                return;
            }
            PixelGlyph.PENCIL.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
        } else {
            PixelGlyph.PENCIL.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
        }
    }
}
