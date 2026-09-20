package com.sakurakugu.autotorch.client;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** 光照显示中不常用选项的独立设置界面。 */
public final class LightOverlaySettingsScreen extends Screen {
    private final Screen parent;
    private Button numberRotationButton;

    public LightOverlaySettingsScreen(Screen parent) {
        super(new TextComponentTranslation("screen.autotorch.light_overlay_more_settings_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        numberRotationButton = addRenderableWidget(button(width / 2 - 100, 42, 200, 20,
                numberRotationMessage(), button -> {
            ClientConfig.setRotatesLightOverlayNumbers(!ClientConfig.rotatesLightOverlayNumbers());
            numberRotationButton.setMessage(numberRotationMessage().getFormattedText());
        }));

        addRenderableWidget(button(width / 2 - 50, 72, 100, 20,
                new TextComponentTranslation("screen.autotorch.back"), button -> onClose()));
    }

    private <T extends Button> T addRenderableWidget(T widget) {
        buttonList.add(widget);
        return widget;
    }

    private static Button button(int x, int y, int width, int height,
            ITextComponent message, Button.OnPress onPress) {
        return new ScrollingButton(x, y, width, height, message.getFormattedText(), onPress);
    }

    private static ITextComponent numberRotationMessage() {
        return new TextComponentTranslation(ClientConfig.rotatesLightOverlayNumbers()
                ? "screen.autotorch.light_overlay_number_rotation_on"
                : "screen.autotorch.light_overlay_number_rotation_off");
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        super.render(mouseX, mouseY, partialTick);
        drawCenteredString(font, title.getFormattedText(), width / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.displayGuiScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
