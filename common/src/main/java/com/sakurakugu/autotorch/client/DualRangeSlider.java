package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

/** 可复用的双端点范围滑动条。 */
public class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = new ResourceLocation("autotorch", "textures/gui/slider.png");
    private final int minValue;
    private final int maxValue;
    private final int maxSpan;
    private final BiFunction<Integer, Integer, Component> messageFactory;
    private final BiConsumer<Integer, Integer> changeListener;
    private int lowerValue;
    private int upperValue;
    private int draggingThumb;

    public DualRangeSlider(int x, int y, int width, int height, int minValue, int maxValue,
            int maxSpan, int lowerValue, int upperValue,
            BiFunction<Integer, Integer, Component> messageFactory,
            BiConsumer<Integer, Integer> changeListener) {
        super(x, y, width, height, TextComponent.EMPTY, button -> { });
        if (minValue >= maxValue || maxSpan < 0) throw new IllegalArgumentException("Invalid slider range");
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.maxSpan = maxSpan;
        this.messageFactory = messageFactory;
        this.changeListener = changeListener;
        setValues(lowerValue, upperValue, false);
    }

    public int lowerValue() { return lowerValue; }
    public int upperValue() { return upperValue; }

    public void setValues(int lowerValue, int upperValue) {
        setValues(lowerValue, upperValue, true);
    }

    private void setValues(int lowerValue, int upperValue, boolean notify) {
        this.lowerValue = clamp(lowerValue);
        this.upperValue = clamp(upperValue);
        if (this.lowerValue > this.upperValue) this.lowerValue = this.upperValue;
        if (this.upperValue - this.lowerValue > maxSpan) this.upperValue = clamp(this.lowerValue + maxSpan);
        setMessage(messageFactory.apply(this.lowerValue, this.upperValue));
        if (notify) changeListener.accept(this.lowerValue, this.upperValue);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        Minecraft.getInstance().getTextureManager().bind(SLIDER_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
        drawNineSliced(poseStack, x, y, getWidth(), getHeight(), 20, 4, 200, 20, 0, isFocused() ? 20 : 0);
        fill(poseStack, lowX, y + 1, highX, y + getHeight() - 1, 0xFF3A5F8A);
        drawThumb(poseStack, lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(poseStack, highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 5, 0xFFFFFFFF);
    }

    private void drawThumb(PoseStack poseStack, int thumbX, boolean highlighted) {
        drawNineSliced(poseStack, thumbX - 4, y, 8, getHeight(), 20, 4, 200, 20, 0, highlighted ? 60 : 40);
    }

    private static void drawNineSliced(PoseStack p, int x, int y, int w, int h, int cw, int ch, int tw, int th, int u, int v) {
        int l=Math.min(cw,w/2),r=Math.min(cw,w-l),t=Math.min(ch,h/2),b=Math.min(ch,h-t),mw=w-l-r,mh=h-t-b,sw=Math.max(1,tw-cw*2),sh=Math.max(1,th-ch*2);
        blitPatch(p,x,y,l,t,u,v,l,t); blitPatch(p,x+w-r,y,r,t,u+tw-r,v,r,t); blitPatch(p,x,y+h-b,l,b,u,v+th-b,l,b); blitPatch(p,x+w-r,y+h-b,r,b,u+tw-r,v+th-b,r,b);
        if(mw>0){blitRepeatingPatch(p,x+l,y,mw,t,u+cw,v,sw,t);blitRepeatingPatch(p,x+l,y+h-b,mw,b,u+cw,v+th-b,sw,b);} if(mh>0){blitRepeatingPatch(p,x,y+t,l,mh,u,v+ch,l,sh);blitRepeatingPatch(p,x+w-r,y+t,r,mh,u+tw-r,v+ch,r,sh);} if(mw>0&&mh>0)blitRepeatingPatch(p,x+l,y+t,mw,mh,u+cw,v+ch,sw,sh);
    }
    private static void blitPatch(PoseStack p,int x,int y,int w,int h,int u,int v,int sw,int sh){if(w>0&&h>0)blit(p,x,y,w,h,u,v,sw,sh,256,256);}
    private static void blitRepeatingPatch(PoseStack p,int x,int y,int w,int h,int u,int v,int sw,int sh){if(w<=0||h<=0||sw<=0||sh<=0)return;for(int oy=0;oy<h;oy+=sh)for(int ox=0;ox<w;ox+=sw){int tw=Math.min(sw,w-ox),th=Math.min(sh,h-oy);blitPatch(p,x+ox,y+oy,tw,th,u,v,tw,th);}}

    private boolean isThumbHovered(int mouseX, int mouseY, int x) {
        return mouseY >= y && mouseY < y + getHeight() && Math.abs(mouseX - x) <= 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        draggingThumb = Math.abs(mouseX - position(lowerValue)) <= Math.abs(mouseX - position(upperValue)) ? 1 : 2;
        update(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingThumb == 0) return false;
        update(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = 0;
        return true;
    }

    private void update(double mouseX) {
        int value = (int) Math.round((Math.max(x + 4, Math.min(x + getWidth() - 4, mouseX))
                - (x + 4)) * (maxValue - minValue) / (double) (getWidth() - 8)) + minValue;
        if (draggingThumb == 1) {
            int lower = Math.min(value, upperValue);
            int upper = upperValue;
            if (upper - lower > maxSpan) upper = lower + maxSpan;
            setValues(lower, upper);
        } else {
            int lower = lowerValue;
            int upper = Math.max(value, lower);
            if (upper - lower > maxSpan) lower = upper - maxSpan;
            setValues(lower, upper);
        }
    }

    private int position(int value) {
        return x + 4 + (value - minValue) * (getWidth() - 8) / (maxValue - minValue);
    }

    private int clamp(int value) { return Math.max(minValue, Math.min(maxValue, value)); }
}
