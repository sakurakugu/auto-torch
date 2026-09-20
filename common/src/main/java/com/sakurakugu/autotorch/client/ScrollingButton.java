package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;

/** 保留旧版按钮行为，并为超出可用宽度的文字补充横向滚动。 */
final class ScrollingButton extends Button {
    ScrollingButton(int x, int y, int width, int height, String message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        // 先按原版外观绘制按钮底图，再叠加可滚动文字。
        String messageBeforeRender = getMessage();
        setMessage("");
        super.render(mouseX, mouseY, partialTicks);
        setMessage(messageBeforeRender);
        ScrollingText.render(Minecraft.getMinecraft().fontRenderer, messageBeforeRender,
                xPosition + 2, yPosition, xPosition + width - 2, yPosition + height,
                enabled ? 0xFFFFFF : 0xA0A0A0);
    }
}
