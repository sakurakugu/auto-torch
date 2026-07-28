package com.sakurakugu.autotorch.client;

/** 统一旧版按钮和文本框所需的少量布局操作。 */
interface Widget {
    int y();
    void setY(int y);
    boolean isVisible();
    boolean isMouseOver(double mouseX, double mouseY);
}
