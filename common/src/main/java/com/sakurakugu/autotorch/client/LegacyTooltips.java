package com.sakurakugu.autotorch.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 旧版没有 Tooltip API：集中登记控件提示，由界面在 render 末尾统一绘制。
 * 新版本界面用 setTooltip(Tooltip.create(...)) 登记提示，这里提供等价的旧版实现。
 */
public final class LegacyTooltips {
    private static final Map<AbstractWidget, Component> TOOLTIPS = new WeakHashMap<>();

    private LegacyTooltips() {
    }

    /** 保留 Tooltip.create(...) 的调用写法，实际只返回提示组件。 */
    public static Component wrap(Component tooltip) {
        return tooltip;
    }

    /** 登记或清除某个控件的悬停提示。 */
    public static void set(AbstractWidget widget, Component tooltip) {
        if (widget == null) {
            return;
        }
        if (tooltip == null) {
            TOOLTIPS.remove(widget);
        } else {
            TOOLTIPS.put(widget, tooltip);
        }
    }

    /** 清除某个控件的悬停提示。 */
    public static void clear(AbstractWidget widget) {
        if (widget != null) {
            TOOLTIPS.remove(widget);
        }
    }

    /** 在界面 render 末尾绘制当前悬停控件的提示。 */
    public static void render(Screen screen, Font font, int mouseX, int mouseY) {
        // 只处理当前界面的控件，避免重建界面后旧控件偶尔参与命中判定。
        Set<AbstractWidget> currentWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget) {
                currentWidgets.add(((AbstractWidget) child));
            }
        }
        for (Map.Entry<AbstractWidget, Component> entry : TOOLTIPS.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (widget == null || !currentWidgets.contains(widget) || !widget.visible) {
                continue;
            }
            if (widget.isMouseOver(mouseX, mouseY)) {
                screen.renderTooltip(
                        TooltipRenderer.split(font, entry.getValue(), screen.width, mouseX), mouseX, mouseY);
                restoreGuiRenderState();
                return;
            }
        }
    }

    /**
     * 1.14.4 的 Forge 提示框绘制结束时会重新开启标准物品光照与深度测试，
     * 而全部设置界面会在提示框之后继续画文字和图标，必须恢复 2D GUI 状态，
     * 否则后续文字会被光照压暗（鼠标悬停时更明显）。
     */
    public static void restoreGuiRenderState() {
        Lighting.turnOff();
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.enableTexture();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
