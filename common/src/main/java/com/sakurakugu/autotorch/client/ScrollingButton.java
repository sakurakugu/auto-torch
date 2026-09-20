package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 保留旧版按钮行为，并为超出可用宽度的文字补充横向滚动。 */
final class ScrollingButton extends Button {
    ScrollingButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        int textureY = getYImage(isHovered() || isFocused());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        blit(poseStack, x, y, 0, 46 + textureY * 20, width / 2, height);
        blit(poseStack, x + width / 2, y, 200 - width / 2,
                46 + textureY * 20, width / 2, height);
        renderBg(poseStack, minecraft, mouseX, mouseY);
        int color = (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255.0F) << 24;
        ScrollingText.render(poseStack, font, getMessage(), x + 2, y, x + width - 2, y + height, color);
        if (isHovered() || isFocused()) {
            renderToolTip(poseStack, mouseX, mouseY);
        }
    }
}
