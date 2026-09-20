package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;

/** 为旧版原版样式滑块补充长文本横向滚动。 */
abstract class ScrollingSliderButton extends AbstractSliderButton {
    protected ScrollingSliderButton(int x, int y, int width, int height, double value) {
        super(x, y, width, height, value);
    }

    @Override
    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        // 借用父类的滑块外观，仅把居中文字换成可滚动文字。
        String messageBeforeRender = getMessage();
        setMessage("");
        super.renderButton(mouseX, mouseY, partialTicks);
        setMessage(messageBeforeRender);
        ScrollingText.render(Minecraft.getMinecraft().fontRenderer, messageBeforeRender,
                x + 2, y, x + width - 2, y + height, 0xFFFFFFFF);
    }
}
