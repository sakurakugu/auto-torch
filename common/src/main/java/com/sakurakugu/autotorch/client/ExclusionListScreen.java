package com.sakurakugu.autotorch.client;

import java.util.List;

import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** 列出照明范围和所有排除区，并提供分页、原位修改、单条删除和一键清空。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int LIGHTING_TEXT_COLOR = 0xFF50FF70;
    private static final int EXCLUSION_TEXT_COLOR = 0xFFFF5050;
    private int page;
    private Component status = new TextComponent("");

    public ExclusionListScreen() {
        super(new TranslatableComponent("screen.autotorch.exclusions_title"));
    }

    @Override
    protected void init() {
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int maxPage = maxPage(total);
        page = Math.min(page, maxPage);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        for (int index = firstIndex; index < lastIndex; index++) {
            int row = index - firstIndex;
            int y = 36 + row * 24;
            int selectedIndex = index;
            boolean lightingEntry = lightingZone != null && index == 0;
            // 每行右侧依次放置修改和删除图标，与“全部设置”页的按钮列保持一致。
            IconButton edit = addButton(new IconButton(left + panelWidth - 44, y, 20, 20,
                    PixelGlyph.PENCIL,
                    new TranslatableComponent(lightingEntry
                            ? "screen.autotorch.edit_lighting" : "screen.autotorch.edit_exclusion"),
                    button -> {
                        int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                        boolean editing = lightingEntry
                                ? SelectionState.beginEditingLightingZone()
                                : SelectionState.beginEditingExclusion(exclusionIndex);
                        if (editing) {
                            minecraft.setScreen(new LightingScreen());
                        }
                    }));
            LegacyTooltips.set(edit, new TranslatableComponent(lightingEntry
                    ? "screen.autotorch.edit_lighting.tooltip" : "screen.autotorch.edit_exclusion.tooltip"));
            addButton(new IconButton(left + panelWidth - 20, y, 20, 20, PixelGlyph.TRASH,
                    new TranslatableComponent("screen.autotorch.delete_zone"), button -> {
                        if (lightingEntry) {
                            SelectionState.removeLightingZone();
                        } else {
                            int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                            SelectionState.removeExclusion(exclusionIndex);
                        }
                        rebuildWidgets();
                    }));
        }

        // 左上返回、右上清空全部照明范围和排除区。
        addButton(new IconButton(left, 8, 20, 20, PixelGlyph.BACK,
                new TranslatableComponent("screen.autotorch.back"), button -> onClose()));
        addButton(new IconButton(left + panelWidth - 20, 8, 20, 20, PixelGlyph.RESET,
                new TranslatableComponent("screen.autotorch.clear_all_zones"), button -> {
                    SelectionState.clearZones();
                    status = new TranslatableComponent("screen.autotorch.zones_cleared");
                    page = 0;
                    rebuildWidgets();
                }));

        // 底部上一页、下一页同样改用图标，页码居中显示在两者之间。
        int pageHalfWidth = (font.width(pageSummary()) + 1) / 2;
        addButton(new IconButton(width / 2 - pageHalfWidth - 28, height - 32, 20, 20,
                PixelGlyph.ARROW_LEFT, new TranslatableComponent("screen.autotorch.previous_page"), button -> {
            page--;
            rebuildWidgets();
        })).active = page > 0;
        addButton(new IconButton(width / 2 + pageHalfWidth + 8, height - 32, 20, 20,
                PixelGlyph.ARROW_RIGHT, new TranslatableComponent("screen.autotorch.next_page"), button -> {
            page++;
            rebuildWidgets();
        })).active = page < maxPage;
    }

    private void rebuildWidgets() {
        buttons.clear();
        children().clear();
        init();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        drawCenteredString(poseStack, font, title, width / 2, 14, 0xFFFFFFFF);
        if (!status.getString().isEmpty()) {
            drawCenteredString(poseStack, font, status, width / 2, height - 46, 0xFFB0B0B0);
        }
        if (total == 0) {
            drawCenteredString(poseStack, font, new TranslatableComponent("screen.autotorch.no_zone"), width / 2, 82, 0xFFA0A0A0);
        }
        for (int index = firstIndex; index < lastIndex; index++) {
            int y = 42 + (index - firstIndex) * 24;
            boolean lightingEntry = lightingZone != null && index == 0;
            int exclusionIndex = index - (lightingZone == null ? 0 : 1);
            String description = lightingEntry
                    ? describeLighting(lightingZone).getString()
                    : describeExclusion(exclusionIndex, exclusions.get(exclusionIndex)).getString();
            int availableWidth = panelWidth - 52;
            if (font.width(description) > availableWidth) {
                description = font.plainSubstrByWidth(description, Math.max(0, availableWidth - font.width("..."))) + "...";
            }
            drawString(poseStack, font, description, left + 4, y,
                    lightingEntry ? LIGHTING_TEXT_COLOR : EXCLUSION_TEXT_COLOR);
        }
        drawCenteredString(poseStack, font, pageSummary(), width / 2, height - 25, 0xFFB0B0B0);
        LegacyTooltips.render(this, poseStack, font, mouseX, mouseY);
    }

    private Component pageSummary() {
        int lightingCount = SelectionState.lightingZone() == null ? 0 : 1;
        int exclusionCount = SelectionState.exclusions().size();
        int total = lightingCount + exclusionCount;
        return new TranslatableComponent("screen.autotorch.page_summary",
                page + 1, maxPage(total) + 1, lightingCount, exclusionCount);
    }

    private static Component describeLighting(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new TranslatableComponent("screen.autotorch.lighting_sphere_row", format(zone.first()), zone.radius());
        }
        return new TranslatableComponent("screen.autotorch.lighting_box_row", format(zone.first()), format(zone.second()));
    }

    private static Component describeExclusion(int index, AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new TranslatableComponent("screen.autotorch.exclusion_sphere_row",
                    index + 1, format(zone.first()), zone.radius());
        }
        return new TranslatableComponent("screen.autotorch.exclusion_box_row",
                index + 1, format(zone.first()), format(zone.second()));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int maxPage(int size) {
        return Math.max(0, (size - 1) / PAGE_SIZE);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new LightingScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
