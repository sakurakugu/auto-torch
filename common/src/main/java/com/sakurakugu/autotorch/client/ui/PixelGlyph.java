package com.sakurakugu.autotorch.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import static net.minecraft.client.gui.GuiComponent.fill;

/** 沿用 ArchWeaver 的像素图标（MIT，sakurakugu）。 */
public enum PixelGlyph {
    SAVE(12, 12) { // 保存图标：实心软盘，快门和标签镂空，宽高均 12。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x, y, x + 10, y + 1, color);
            fill(poseStack, x, y + 1, x + 1, y + 12, color);
            fill(poseStack, x + 2, y + 1, x + 3, y + 5, color);
            fill(poseStack, x + 8, y + 1, x + 9, y + 5, color);
            fill(poseStack, x + 10, y + 1, x + 11, y + 2, color);
            fill(poseStack, x + 6, y + 2, x + 7, y + 5, color);
            fill(poseStack, x + 11, y + 2, x + 12, y + 12, color);
            fill(poseStack, x + 3, y + 4, x + 6, y + 5, color);
            fill(poseStack, x + 7, y + 4, x + 8, y + 5, color);
            fill(poseStack, x + 2, y + 6, x + 10, y + 7, color);
            fill(poseStack, x + 2, y + 7, x + 3, y + 12, color);
            fill(poseStack, x + 9, y + 7, x + 10, y + 12, color);
            fill(poseStack, x + 1, y + 11, x + 2, y + 12, color);
            fill(poseStack, x + 3, y + 11, x + 9, y + 12, color);
            fill(poseStack, x + 10, y + 11, x + 11, y + 12, color);
        }
    },
    RESET(12, 12) { // 重置图标：圆角方框轮廓，右侧留出缺口，宽高均 12。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 2, y + 1, x + 10, y + 3, color);
            fill(poseStack, x + 1, y + 2, x + 2, y + 10, color);
            fill(poseStack, x + 10, y + 2, x + 11, y + 6, color);
            fill(poseStack, x + 2, y + 3, x + 3, y + 11, color);
            fill(poseStack, x + 9, y + 3, x + 10, y + 6, color);
            fill(poseStack, x + 8, y + 4, x + 9, y + 5, color);
            fill(poseStack, x + 11, y + 4, x + 12, y + 5, color);
            fill(poseStack, x + 9, y + 8, x + 11, y + 10, color);
            fill(poseStack, x + 3, y + 9, x + 9, y + 11, color);
            fill(poseStack, x + 9, y + 10, x + 10, y + 11, color);
        }
    },
    BACK(12, 12) { // 返回图标：返回上一级页面，宽高均 12。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 6, y + 1, x + 8, y + 3, color);
            fill(poseStack, x + 5, y + 2, x + 6, y + 5, color);
            fill(poseStack, x + 4, y + 3, x + 5, y + 9, color);
            fill(poseStack, x + 6, y + 3, x + 7, y + 4, color);
            fill(poseStack, x + 3, y + 4, x + 4, y + 8, color);
            fill(poseStack, x + 2, y + 5, x + 3, y + 7, color);
            fill(poseStack, x + 5, y + 7, x + 6, y + 10, color);
            fill(poseStack, x + 6, y + 8, x + 7, y + 11, color);
            fill(poseStack, x + 7, y + 9, x + 8, y + 11, color);
        }
    },
    PENCIL(16, 16) { // 铅笔图标：斜向笔身带笔尖与笔尾，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 12, y + 2, x + 13, y + 3, color);
            fill(poseStack, x + 3, y + 3, x + 8, y + 4, color);
            fill(poseStack, x + 11, y + 3, x + 12, y + 4, color);
            fill(poseStack, x + 13, y + 3, x + 14, y + 4, color);
            fill(poseStack, x + 3, y + 4, x + 4, y + 13, color);
            fill(poseStack, x + 10, y + 4, x + 11, y + 5, color);
            fill(poseStack, x + 12, y + 4, x + 13, y + 5, color);
            fill(poseStack, x + 9, y + 5, x + 10, y + 6, color);
            fill(poseStack, x + 11, y + 5, x + 12, y + 6, color);
            fill(poseStack, x + 8, y + 6, x + 9, y + 7, color);
            fill(poseStack, x + 10, y + 6, x + 11, y + 7, color);
            fill(poseStack, x + 7, y + 7, x + 8, y + 8, color);
            fill(poseStack, x + 9, y + 7, x + 10, y + 8, color);
            fill(poseStack, x + 6, y + 8, x + 7, y + 9, color);
            fill(poseStack, x + 8, y + 8, x + 9, y + 9, color);
            fill(poseStack, x + 12, y + 8, x + 13, y + 13, color);
            fill(poseStack, x + 5, y + 9, x + 6, y + 11, color);
            fill(poseStack, x + 7, y + 9, x + 8, y + 10, color);
            fill(poseStack, x + 6, y + 10, x + 7, y + 11, color);
            fill(poseStack, x + 4, y + 12, x + 12, y + 13, color);
        }
    },
    TRASH(16, 16) { // 垃圾桶图标：提手、盖子与带内纹的桶身，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 5, y + 2, x + 11, y + 3, color);
            fill(poseStack, x + 5, y + 3, x + 6, y + 5, color);
            fill(poseStack, x + 10, y + 3, x + 11, y + 5, color);
            fill(poseStack, x + 3, y + 4, x + 5, y + 5, color);
            fill(poseStack, x + 6, y + 4, x + 10, y + 5, color);
            fill(poseStack, x + 11, y + 4, x + 13, y + 5, color);
            fill(poseStack, x + 4, y + 5, x + 5, y + 13, color);
            fill(poseStack, x + 11, y + 5, x + 12, y + 13, color);
            fill(poseStack, x + 6, y + 6, x + 7, y + 11, color);
            fill(poseStack, x + 9, y + 6, x + 10, y + 11, color);
            fill(poseStack, x + 5, y + 12, x + 11, y + 13, color);
        }
    },
    SLIDER(16, 16) { // 滑动条图标：轨道两端带挡块、左侧带滑柄，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 1, y + 4, x + 15, y + 5, color);
            fill(poseStack, x + 1, y + 5, x + 2, y + 13, color);
            fill(poseStack, x + 14, y + 5, x + 15, y + 13, color);
            fill(poseStack, x + 4, y + 6, x + 7, y + 7, color);
            fill(poseStack, x + 4, y + 7, x + 5, y + 11, color);
            fill(poseStack, x + 6, y + 7, x + 7, y + 11, color);
            fill(poseStack, x + 3, y + 8, x + 4, y + 9, color);
            fill(poseStack, x + 7, y + 8, x + 13, y + 9, color);
            fill(poseStack, x + 5, y + 10, x + 6, y + 11, color);
            fill(poseStack, x + 2, y + 12, x + 14, y + 13, color);
        }
    },
    TEXT_INPUT(16, 16) { // 输入框图标：方框内带一个文本光标，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 1, y + 4, x + 15, y + 5, color);
            fill(poseStack, x + 1, y + 5, x + 2, y + 13, color);
            fill(poseStack, x + 14, y + 5, x + 15, y + 13, color);
            fill(poseStack, x + 3, y + 6, x + 6, y + 7, color);
            fill(poseStack, x + 4, y + 7, x + 5, y + 11, color);
            fill(poseStack, x + 3, y + 10, x + 4, y + 11, color);
            fill(poseStack, x + 5, y + 10, x + 6, y + 11, color);
            fill(poseStack, x + 2, y + 12, x + 14, y + 13, color);
        }
    },
    SWITCH(16, 16) { // 开关图标：方框内左侧带一个滑块，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 1, y + 4, x + 15, y + 5, color);
            fill(poseStack, x + 1, y + 5, x + 2, y + 13, color);
            fill(poseStack, x + 14, y + 5, x + 15, y + 13, color);
            fill(poseStack, x + 3, y + 6, x + 6, y + 11, color);
            fill(poseStack, x + 2, y + 12, x + 14, y + 13, color);
        }
    },
    SETTING(12, 12) { // 设置图标：由像素点组成，宽高均 12。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 5, y + 1, x + 7, y + 4, color);
            fill(poseStack, x + 2, y + 2, x + 4, y + 4, color);
            fill(poseStack, x + 8, y + 2, x + 10, y + 4, color);
            fill(poseStack, x + 4, y + 3, x + 5, y + 5, color);
            fill(poseStack, x + 7, y + 3, x + 8, y + 5, color);
            fill(poseStack, x + 3, y + 4, x + 4, y + 10, color);
            fill(poseStack, x + 8, y + 4, x + 9, y + 10, color);
            fill(poseStack, x + 1, y + 5, x + 3, y + 7, color);
            fill(poseStack, x + 9, y + 5, x + 11, y + 7, color);
            fill(poseStack, x + 4, y + 7, x + 5, y + 9, color);
            fill(poseStack, x + 7, y + 7, x + 8, y + 9, color);
            fill(poseStack, x + 2, y + 8, x + 3, y + 10, color);
            fill(poseStack, x + 5, y + 8, x + 7, y + 11, color);
            fill(poseStack, x + 9, y + 8, x + 10, y + 10, color);
        }
    },
    PANEL(16, 16) { // 面板图标：三栏布局，用于全部设置入口，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 1, y + 4, x + 15, y + 5, color);
            fill(poseStack, x + 1, y + 5, x + 2, y + 13, color);
            fill(poseStack, x + 6, y + 5, x + 7, y + 13, color);
            fill(poseStack, x + 10, y + 5, x + 11, y + 13, color);
            fill(poseStack, x + 14, y + 5, x + 15, y + 13, color);
            fill(poseStack, x + 3, y + 6, x + 5, y + 11, color);
            fill(poseStack, x + 2, y + 12, x + 6, y + 13, color);
            fill(poseStack, x + 7, y + 12, x + 10, y + 13, color);
            fill(poseStack, x + 11, y + 12, x + 14, y + 13, color);
        }
    },
    LIST(16, 16) { // 列表图标：三条带左侧标记的条目，用于管理入口，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 2, y + 3, x + 4, y + 5, color);
            fill(poseStack, x + 6, y + 3, x + 14, y + 5, color);
            fill(poseStack, x + 2, y + 7, x + 4, y + 9, color);
            fill(poseStack, x + 6, y + 7, x + 14, y + 9, color);
            fill(poseStack, x + 2, y + 11, x + 4, y + 13, color);
            fill(poseStack, x + 6, y + 11, x + 14, y + 13, color);
        }
    },
    DUAL_SLIDER(16, 16) { // 双端点滑动条图标：轨道上带左右两个滑柄，宽高均 16。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 1, y + 4, x + 15, y + 5, color);
            fill(poseStack, x + 1, y + 5, x + 2, y + 13, color);
            fill(poseStack, x + 14, y + 5, x + 15, y + 13, color);
            fill(poseStack, x + 4, y + 6, x + 7, y + 7, color);
            fill(poseStack, x + 8, y + 6, x + 11, y + 7, color);
            fill(poseStack, x + 4, y + 7, x + 5, y + 11, color);
            fill(poseStack, x + 6, y + 7, x + 9, y + 8, color);
            fill(poseStack, x + 10, y + 7, x + 11, y + 11, color);
            fill(poseStack, x + 3, y + 8, x + 4, y + 9, color);
            fill(poseStack, x + 6, y + 8, x + 7, y + 11, color);
            fill(poseStack, x + 8, y + 8, x + 9, y + 11, color);
            fill(poseStack, x + 11, y + 8, x + 13, y + 9, color);
            fill(poseStack, x + 7, y + 9, x + 8, y + 10, color);
            fill(poseStack, x + 5, y + 10, x + 6, y + 11, color);
            fill(poseStack, x + 9, y + 10, x + 10, y + 11, color);
            fill(poseStack, x + 2, y + 12, x + 14, y + 13, color);
        }
    },
    ARROW_LEFT(10, 10) { // 左箭头图标：尖角指向左侧，用于翻页，宽高均 10。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 5, y, x + 7, y + 2, color);
            fill(poseStack, x + 4, y + 1, x + 5, y + 4, color);
            fill(poseStack, x + 3, y + 2, x + 4, y + 8, color);
            fill(poseStack, x + 5, y + 2, x + 6, y + 3, color);
            fill(poseStack, x + 2, y + 3, x + 3, y + 7, color);
            fill(poseStack, x + 1, y + 4, x + 2, y + 6, color);
            fill(poseStack, x + 4, y + 6, x + 5, y + 9, color);
            fill(poseStack, x + 5, y + 7, x + 6, y + 10, color);
            fill(poseStack, x + 6, y + 8, x + 7, y + 10, color);
        }
    },
    ARROW_RIGHT(10, 10) { // 右箭头图标：尖角指向右侧，用于翻页，宽高均 10。
        @Override
        protected void draw(PoseStack poseStack, int x, int y, int color) {
            fill(poseStack, x + 3, y, x + 5, y + 2, color);
            fill(poseStack, x + 5, y + 1, x + 6, y + 4, color);
            fill(poseStack, x + 4, y + 2, x + 5, y + 3, color);
            fill(poseStack, x + 6, y + 2, x + 7, y + 8, color);
            fill(poseStack, x + 7, y + 3, x + 8, y + 7, color);
            fill(poseStack, x + 8, y + 4, x + 9, y + 6, color);
            fill(poseStack, x + 5, y + 6, x + 6, y + 9, color);
            fill(poseStack, x + 4, y + 7, x + 5, y + 10, color);
            fill(poseStack, x + 3, y + 8, x + 4, y + 10, color);
        }
    };

    private final int width; // 字形的像素宽度。
    private final int height; // 字形的像素高度。

    PixelGlyph(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void drawCentered(PoseStack poseStack, int x, int y, int width, int height, int color) {
        draw(poseStack, x + (width - this.width) / 2, y + (height - this.height) / 2, color);
    }

    /** 在指定坐标直接绘制图标，供列表行左侧的控件类型标记使用。 */
    public void drawAt(PoseStack poseStack, int x, int y, int color) {
        draw(poseStack, x, y, color);
    }

    protected abstract void draw(PoseStack poseStack, int x, int y, int color);
}
