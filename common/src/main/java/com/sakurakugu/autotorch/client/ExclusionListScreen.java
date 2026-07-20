package com.sakurakugu.autotorch.client;

import java.util.List;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 列出照明范围和所有排除区，并提供分页、原位修改和单条删除。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int LIGHTING_TEXT_COLOR = 0xFF50FF70;
    private static final int EXCLUSION_TEXT_COLOR = 0xFFFF5050;
    private int page;

    public ExclusionListScreen() {
        super(Component.translatable("screen.autotorch.exclusions_title"));
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
            int y = 34 + row * 24;
            int selectedIndex = index;
            boolean lightingEntry = lightingZone != null && index == 0;
            addRenderableWidget(Button.builder(Component.translatable(lightingEntry
                    ? "screen.autotorch.edit_lighting" : "screen.autotorch.edit_exclusion"), button -> {
                int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                boolean editing = lightingEntry
                        ? SelectionState.beginEditingLightingZone()
                        : SelectionState.beginEditingExclusion(exclusionIndex);
                if (editing) {
                    minecraft.setScreen(new LightingScreen());
                }
            }).tooltip(Tooltip.create(Component.translatable(lightingEntry
                    ? "screen.autotorch.edit_lighting.tooltip" : "screen.autotorch.edit_exclusion.tooltip")))
                    .bounds(left + panelWidth - 108, y, 50, 20).build());
            addRenderableWidget(new ColoredButton(left + panelWidth - 54, y, 54, 20,
                    Component.translatable("screen.autotorch.delete_zone"), button -> {
                        if (lightingEntry) {
                            SelectionState.removeLightingZone();
                        } else {
                            int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                            SelectionState.removeExclusion(exclusionIndex);
                        }
                        rebuildWidgets();
                    }, 0xDDA52B2B, 0xEEC83C3C));
        }

        Button previous = addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.previous_page"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(left, 184, 80, 20).build());
        previous.active = page > 0;

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.back"), button -> onClose())
                .bounds(width / 2 - 50, 184, 100, 20).build());

        Button next = addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.next_page"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(left + panelWidth - 80, 184, 80, 20).build());
        next.active = page < maxPage;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (total == 0) {
            graphics.centeredText(font, Component.translatable("screen.autotorch.no_zone"), width / 2, 82, 0xFFA0A0A0);
        }
        for (int index = firstIndex; index < lastIndex; index++) {
            int y = 40 + (index - firstIndex) * 24;
            boolean lightingEntry = lightingZone != null && index == 0;
            int exclusionIndex = index - (lightingZone == null ? 0 : 1);
            String description = lightingEntry
                    ? describeLighting(lightingZone).getString()
                    : describeExclusion(exclusionIndex, exclusions.get(exclusionIndex)).getString();
            int availableWidth = panelWidth - 116;
            if (font.width(description) > availableWidth) {
                description = font.plainSubstrByWidth(description, Math.max(0, availableWidth - font.width("..."))) + "...";
            }
            graphics.text(font, description, left + 4, y,
                    lightingEntry ? LIGHTING_TEXT_COLOR : EXCLUSION_TEXT_COLOR);
        }
        graphics.centeredText(font, Component.translatable("screen.autotorch.page_summary",
                page + 1, maxPage(total) + 1, lightingZone == null ? 0 : 1, exclusions.size()),
                width / 2, 212, 0xFFA0A0A0);
    }

    private static Component describeLighting(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return Component.translatable("screen.autotorch.lighting_sphere_row", format(zone.first()), zone.radius());
        }
        return Component.translatable("screen.autotorch.lighting_box_row", format(zone.first()), format(zone.second()));
    }

    private static Component describeExclusion(int index, AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return Component.translatable("screen.autotorch.exclusion_sphere_row",
                    index + 1, format(zone.first()), zone.radius());
        }
        return Component.translatable("screen.autotorch.exclusion_box_row",
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
