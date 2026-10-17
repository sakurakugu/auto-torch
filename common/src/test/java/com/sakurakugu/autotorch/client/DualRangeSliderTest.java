package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualRangeSliderTest {
    @Test
    void dragsBothThumbsThroughContainer() {
        int[] observed = new int[2];
        DualRangeSlider slider = slider(observed);
        AbstractContainerEventHandler container = new AbstractContainerEventHandler() {
            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(slider);
            }
        };

        assertTrue(container.mouseClicked(left(36), false));
        assertTrue(container.isDragging());
        assertTrue(container.mouseDragged(left(52), 16, 0));
        assertEquals(-16, slider.lowerValue());
        assertEquals(-16, observed[0]);
        assertTrue(container.mouseReleased(left(52)));
        assertFalse(container.mouseDragged(left(60), 8, 0));

        assertTrue(container.mouseClicked(left(100), false));
        assertTrue(container.mouseDragged(left(84), -16, 0));
        assertEquals(16, slider.upperValue());
        assertEquals(16, observed[1]);
        assertTrue(container.mouseReleased(left(84)));
    }

    @Test
    void ignoresOtherButtonsWithoutEndingLeftDrag() {
        DualRangeSlider slider = slider(new int[2]);
        assertFalse(slider.mouseClicked(mouse(36, InputConstants.MOUSE_BUTTON_RIGHT), false));
        assertFalse(slider.mouseClicked(mouse(36, InputConstants.MOUSE_BUTTON_MIDDLE), false));
        assertTrue(slider.mouseClicked(left(36), false));
        assertFalse(slider.mouseDragged(mouse(52, InputConstants.MOUSE_BUTTON_RIGHT), 16, 0));
        assertFalse(slider.mouseReleased(mouse(52, InputConstants.MOUSE_BUTTON_RIGHT)));
        assertEquals(-32, slider.lowerValue());
        assertTrue(slider.mouseDragged(left(52), 16, 0));
        assertEquals(-16, slider.lowerValue());
        assertTrue(slider.mouseReleased(left(52)));
        assertFalse(slider.mouseDragged(left(60), 8, 0));
    }

    @Test
    void clampsDragOutsideTrackAndPreservesMaximumSpan() {
        DualRangeSlider slider = slider(new int[2]);
        assertTrue(slider.mouseClicked(left(36), false));
        assertTrue(slider.mouseDragged(left(-100), -136, 0));
        assertEquals(-64, slider.lowerValue());
        assertEquals(0, slider.upperValue());
        assertTrue(slider.mouseReleased(left(-100)));

        assertTrue(slider.mouseClicked(left(68), false));
        assertTrue(slider.mouseDragged(left(300), 232, 0));
        assertEquals(0, slider.lowerValue());
        assertEquals(64, slider.upperValue());
    }

    @Test
    void commitsCurrentValuesOnlyWhenTheDragEnds() {
        List<String> commits = new ArrayList<>();
        DualRangeSlider slider = new DualRangeSlider(0, 0, 136, 20, -64, 64, 64, -32, 32,
                (lower, upper) -> Component.empty(),
                (lower, upper) -> { },
                (lower, upper) -> commits.add(lower + "," + upper));

        assertTrue(slider.mouseClicked(left(36), false));
        assertTrue(slider.mouseDragged(left(52), 16, 0));
        assertEquals(-16, slider.lowerValue());
        assertTrue(commits.isEmpty());
        assertTrue(slider.mouseReleased(left(52)));
        assertEquals(List.of("-16,32"), commits);

        // 外部同步数值（重置等）不应再次触发提交。
        slider.setValues(-8, 8);
        assertEquals(List.of("-16,32"), commits);
    }

    private static DualRangeSlider slider(int[] observed) {
        return new DualRangeSlider(0, 0, 136, 20, -64, 64, 64, -32, 32,
                (lower, upper) -> Component.empty(),
                (lower, upper) -> {
                    observed[0] = lower;
                    observed[1] = upper;
                });
    }

    private static MouseButtonEvent left(double x) {
        return mouse(x, InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static MouseButtonEvent mouse(double x, int button) {
        return new MouseButtonEvent(x, 10, new MouseButtonInfo(button, 0));
    }
}
