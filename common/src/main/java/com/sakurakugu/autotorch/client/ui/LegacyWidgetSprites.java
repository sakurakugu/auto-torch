package com.sakurakugu.autotorch.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import static net.minecraft.client.gui.GuiComponent.blitNineSliced;

/** 将新版控件贴图名称映射到 1.19.4 的原版整张贴图。 */
public final class LegacyWidgetSprites {
    private static final ResourceLocation WIDGETS = ResourceLocation.tryBuild("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation SLIDER = ResourceLocation.tryBuild("minecraft", "textures/gui/slider.png");

    private LegacyWidgetSprites() { }

    public static void blit(PoseStack poseStack, ResourceLocation sprite, int x, int y, int width, int height) {
        String name = sprite.getPath();
        boolean highlighted = name.endsWith("_highlighted");
        boolean slider = name.startsWith("widget/slider");
        int textureY = slider
                ? (name.contains("handle") ? 40 : 0) + (highlighted ? 20 : 0)
                : name.endsWith("_disabled") ? 46 : highlighted ? 86 : 66;
        // 1.19.4 的 blitNineSliced 不接收贴图路径，需要先手动绑定着色器与纹理。
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, slider ? SLIDER : WIDGETS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        blitNineSliced(poseStack, x, y, width, height, 20, 4, 200, 20, 0, textureY);
    }
}
