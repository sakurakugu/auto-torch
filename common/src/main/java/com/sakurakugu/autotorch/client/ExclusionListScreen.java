package com.sakurakugu.autotorch.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/** 列出照明范围和所有排除区，并提供分页、原位修改和单条删除。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int LIGHTING_TEXT_COLOR = 0xFF50FF70;
    private static final int EXCLUSION_TEXT_COLOR = 0xFFFF5050;
    private final Map<AbstractWidget, Component> tooltips = new LinkedHashMap<>();
    private int page;

    public ExclusionListScreen() {
        super(new TranslatableComponent("screen.autotorch.exclusions_title"));
    }

    private <T extends AbstractWidget> T addRenderableWidget(T widget) {
        return addButton(widget);
    }

    @Override
    protected void init() {
        tooltips.clear();
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
            Button editButton = addRenderableWidget(new Button(left + panelWidth - 108, y, 50, 20,
                    new TranslatableComponent(lightingEntry
                            ? "screen.autotorch.edit_lighting" : "screen.autotorch.edit_exclusion"), button -> {
                int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                boolean editing = lightingEntry
                        ? SelectionState.beginEditingLightingZone()
                        : SelectionState.beginEditingExclusion(exclusionIndex);
                if (editing) {
                    minecraft.setScreen(new LightingScreen());
                }
            }));
            tooltips.put(editButton, new TranslatableComponent(lightingEntry
                    ? "screen.autotorch.edit_lighting.tooltip" : "screen.autotorch.edit_exclusion.tooltip"));
            addRenderableWidget(new ColoredButton(left + panelWidth - 54, y, 54, 20,
                    new TranslatableComponent("screen.autotorch.delete_zone"), button -> {
                        if (lightingEntry) {
                            SelectionState.removeLightingZone();
                        } else {
                            int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                            SelectionState.removeExclusion(exclusionIndex);
                        }
                        rebuildWidgets();
                    }, 0xDDA52B2B, 0xEEC83C3C));
        }

        Button previous = addRenderableWidget(new Button(left, 184, 80, 20,
                new TranslatableComponent("screen.autotorch.previous_page"), button -> {
            page--;
            rebuildWidgets();
        }));
        previous.active = page > 0;

        addRenderableWidget(new Button(width / 2 - 50, 184, 100, 20,
                new TranslatableComponent("screen.autotorch.back"), button -> onClose()));

        Button next = addRenderableWidget(new Button(left + panelWidth - 80, 184, 80, 20,
                new TranslatableComponent("screen.autotorch.next_page"), button -> {
            page++;
            rebuildWidgets();
        }));
        next.active = page < maxPage;
    }

    private void rebuildWidgets() {
        buttons.clear();
        children.clear();
        init();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        drawCenteredString(poseStack, font, title, width / 2, 12, 0xFFFFFFFF);
        if (total == 0) {
            drawCenteredString(poseStack, font, new TranslatableComponent("screen.autotorch.no_zone"),
                    width / 2, 82, 0xFFA0A0A0);
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
            drawString(poseStack, font, description, left + 4, y,
                    lightingEntry ? LIGHTING_TEXT_COLOR : EXCLUSION_TEXT_COLOR);
        }
        drawCenteredString(poseStack, font, new TranslatableComponent("screen.autotorch.page_summary",
                page + 1, maxPage(total) + 1, lightingZone == null ? 0 : 1, exclusions.size()),
                width / 2, 212, 0xFFA0A0A0);
        for (Map.Entry<AbstractWidget, Component> entry : tooltips.entrySet()) {
            if (entry.getKey().visible && entry.getKey().isMouseOver(mouseX, mouseY)) {
                renderTooltip(poseStack, entry.getValue(), mouseX, mouseY);
                break;
            }
        }
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
