package com.sakurakugu.autotorch.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.FontRenderer;
import com.sakurakugu.autotorch.client.Widget;
import com.sakurakugu.autotorch.client.Screen;
import net.minecraft.util.IChatComponent;

/**
 * 旧版没有 Tooltip API：集中登记控件提示，由界面在 render 末尾统一绘制。
 * 新版本界面用 setTooltip(Tooltip.create(...)) 登记提示，这里提供等价的旧版实现。
 */
public final class LegacyTooltips {
    private static final Map<Widget, IChatComponent> TOOLTIPS = new WeakHashMap<>();

    private LegacyTooltips() {
    }

    /** 保留 Tooltip.create(...) 的调用写法，实际只返回提示组件。 */
    public static IChatComponent wrap(IChatComponent tooltip) {
        return tooltip;
    }

    /** 登记或清除某个控件的悬停提示。 */
    public static void set(Widget widget, IChatComponent tooltip) {
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
    public static void clear(Widget widget) {
        if (widget != null) {
            TOOLTIPS.remove(widget);
        }
    }

    /** 在界面 render 末尾绘制当前悬停控件的提示。 */
    public static void render(Screen screen, FontRenderer font, int mouseX, int mouseY) {
        // 只处理当前界面的控件，避免重建界面后旧控件偶尔参与命中判定。
        Set<Widget> currentWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object child : screen.children()) {
            if (child instanceof Widget) {
                currentWidgets.add(((Widget) child));
            }
        }
        for (Map.Entry<Widget, IChatComponent> entry : TOOLTIPS.entrySet()) {
            Widget widget = entry.getKey();
            if (widget == null || !currentWidgets.contains(widget) || !widget.isVisible()) {
                continue;
            }
            if (widget.isMouseOver(mouseX, mouseY)) {
                screen.renderTooltip(
                        TooltipRenderer.split(font, entry.getValue(), screen.width, mouseX), mouseX, mouseY);
                return;
            }
        }
    }
}
