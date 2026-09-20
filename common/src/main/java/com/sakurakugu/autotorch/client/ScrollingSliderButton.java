package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 为旧版原版样式滑块补充长文本横向滚动。 */
abstract class ScrollingSliderButton extends AbstractSliderButton {
    protected ScrollingSliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
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
    }
}
