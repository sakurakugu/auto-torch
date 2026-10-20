package com.sakurakugu.autotorch.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 校验滑块只在按住时跟随鼠标，避免 1.7.10 绘制阶段的 mouseDragged 让滑块自己滑动。 */
class AbstractSliderButtonTest {
    private static final double DELTA = 1.0E-9;

    /** 只测试数值逻辑，不触发任何渲染。 */
    private static final class TestSlider extends AbstractSliderButton {
        TestSlider() {
            super(0, 0, 108, 20, 0.5);
        }

        @Override
        protected void updateMessage() { }

        @Override
        protected void applyValue() { }
    }

    @Test
    void ignoresDrawPhaseDragWithoutPressing() {
        TestSlider slider = new TestSlider();
        double initial = slider.value;
        // 1.7.10 的 GuiButton.drawButton 会在绘制时调用 mouseDragged，按住前不能改值。
        slider.mouseDragged(100.0, 10.0, 0, 0.0, 0.0);
        assertEquals(initial, slider.value, DELTA);
    }

    @Test
    void followsMouseWhileHeldAndStopsAfterRelease() {
        TestSlider slider = new TestSlider();
        slider.mouseClicked(20.0, 10.0, 0);
        assertEquals(0.16, slider.value, DELTA);
        slider.mouseDragged(54.0, 10.0, 0, 0.0, 0.0);
        assertEquals(0.5, slider.value, DELTA);
        slider.mouseReleased(54.0, 10.0, 0);
        slider.mouseDragged(4.0, 10.0, 0, 0.0, 0.0);
        assertEquals(0.5, slider.value, DELTA);
    }
}
