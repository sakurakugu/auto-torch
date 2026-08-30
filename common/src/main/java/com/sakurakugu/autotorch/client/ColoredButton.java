package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getString(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        int textureY = !active ? 46 : highlighted ? 86 : 66;
        Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        drawNineSliced(x, y, getWidth(), height, 20, 4, 200, 20, 0, textureY);
        int color = !active ? 0xCC555555 : highlighted ? hoveredColor : backgroundColor;
        fill(x + 2, y + 2, x + getWidth() - 2, y + height - 2, color);
        drawColoredBevel(color);
        drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 6, active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private static void drawNineSliced(int x, int y, int width, int height, int cw, int ch, int tw, int th, int u, int v) {
        int l=Math.min(cw,width/2),r=Math.min(cw,width-l),t=Math.min(ch,height/2),b=Math.min(ch,height-t),mw=width-l-r,mh=height-t-b,sw=Math.max(1,tw-cw*2),sh=Math.max(1,th-ch*2);
        blitPatch(x,y,l,t,u,v,l,t); blitPatch(x+width-r,y,r,t,u+tw-r,v,r,t); blitPatch(x,y+height-b,l,b,u,v+th-b,l,b); blitPatch(x+width-r,y+height-b,r,b,u+tw-r,v+th-b,r,b);
        if(mw>0){blitRepeat(x+l,y,mw,t,u+cw,v,sw,t);blitRepeat(x+l,y+height-b,mw,b,u+cw,v+th-b,sw,b);} if(mh>0){blitRepeat(x,y+t,l,mh,u,v+ch,l,sh);blitRepeat(x+width-r,y+t,r,mh,u+tw-r,v+ch,r,sh);} if(mw>0&&mh>0)blitRepeat(x+l,y+t,mw,mh,u+cw,v+ch,sw,sh);
    }
    private static void blitPatch(int x,int y,int w,int h,int u,int v,int sw,int sh){if(w>0&&h>0)GuiComponent.blit(x,y,0,(float)u,(float)v,w,h,256,256);}
    private static void blitRepeat(int x,int y,int w,int h,int u,int v,int sw,int sh){if(w<=0||h<=0)return;for(int oy=0;oy<h;oy+=sh)for(int ox=0;ox<w;ox+=sw){int tw=Math.min(sw,w-ox),th=Math.min(sh,h-oy);blitPatch(x+ox,y+oy,tw,th,u,v,tw,th);}}

    private void drawColoredBevel(int color) {
        int highlight = shade(color, 1.35f);
        int shadow = shade(color, 0.55f);
        int corner = shade(color, 0.78f);
        int left = x + 1;
        int top = y + 1;
        int right = x + getWidth() - 2;
        int bottom = y + height - 2;
        fill(left + 1, top, right, top + 1, highlight);
        fill(left, top + 1, left + 1, bottom, highlight);
        fill(left, top, left + 1, top + 1, highlight);
        fill(left + 1, bottom - 1, right, bottom + 1, shadow);
        fill(right, top + 1, right + 1, bottom, shadow);
        fill(right, bottom, right + 1, bottom + 1, shadow);
        fill(right, top, right + 1, top + 1, corner);
        fill(left, bottom, left + 1, bottom + 1, corner);
    }

    private static int shade(int color, float factor) {
        int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
