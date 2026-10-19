package com.sakurakugu.autotorch.client.ui;

import com.sakurakugu.autotorch.client.LegacyText;
import com.sakurakugu.autotorch.client.LegacyTooltips;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 输入框后的选择按钮：直接显示当前物品或颜色，保留原版按钮的焦点反馈。 */
public final class ValuePickerButton extends Button {
    private final boolean color;
    private final Supplier<String> value;

    public ValuePickerButton(int x, int y, boolean color, Supplier<String> value, OnPress onPress) {
        super(x, y, 20, 20, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent(color
                ? "screen.autotorch.pick_color" : "screen.autotorch.pick_item")), onPress);
        this.color = color;
        this.value = value;
        LegacyTooltips.set(this, new net.minecraft.network.chat.TextComponent(getMessage()));
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        String sprite = !active ? "widget/button_disabled"
                : (isHovered() || isFocused()) ? "widget/button_highlighted" : "widget/button";
        LegacyWidgetSprites.blit(new ResourceLocation("minecraft", sprite),
                x, y, getWidth(), height);
        String current = value.get();
        if (color && current.matches("(?i)[0-9a-f]{6}")) {
            fill(x + 4, y + 4, x + 16, y + 16,
                    0xFF000000 | Integer.parseInt(current, 16));
        } else if (!color) {
            ResourceLocation id = ResourceLocation.tryParse(current);
            ItemStack stack = id == null ? ItemStack.EMPTY
                    : Registry.ITEM.getOptional(id).map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x + 2, y + 2);
                return;
            }
            PixelGlyph.PENCIL.drawCentered(x, y, getWidth(), height, 0xFFFFFFFF);
        } else {
            PixelGlyph.PENCIL.drawCentered(x, y, getWidth(), height, 0xFFFFFFFF);
        }
    }
}
