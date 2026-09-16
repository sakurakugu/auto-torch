package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 光照显示中不常用选项的独立设置界面。 */
public final class LightOverlaySettingsScreen extends Screen {
    private final Screen parent;
    private Button numberRotationButton;

    public LightOverlaySettingsScreen(Screen parent) {
        super(Component.translatable("screen.autotorch.light_overlay_more_settings_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        numberRotationButton = addRenderableWidget(Button.builder(numberRotationMessage(), button -> {
            ClientConfig.setRotatesLightOverlayNumbers(!ClientConfig.rotatesLightOverlayNumbers());
            numberRotationButton.setMessage(numberRotationMessage());
        }).bounds(width / 2 - 100, 42, 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.back"), button -> onClose())
                .bounds(width / 2 - 50, 72, 100, 20).build());
    }

    private Component numberRotationMessage() {
        return Component.translatable(ClientConfig.rotatesLightOverlayNumbers()
                ? "screen.autotorch.light_overlay_number_rotation_on"
                : "screen.autotorch.light_overlay_number_rotation_off");
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, font, title, width / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
