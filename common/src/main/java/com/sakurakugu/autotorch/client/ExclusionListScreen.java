package com.sakurakugu.autotorch.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** 列出照明范围和所有排除区，并提供分页、原位修改和单条删除。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int LIGHTING_TEXT_COLOR = 0xFF50FF70;
    private static final int EXCLUSION_TEXT_COLOR = 0xFFFF5050;
    private final Map<Button, ITextComponent> tooltips = new LinkedHashMap<>();
    private int page;

    public ExclusionListScreen() {
        super(new TextComponentTranslation("screen.autotorch.exclusions_title"));
    }

    private <T extends Button> T addRenderableWidget(T widget) {
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
                    new TextComponentTranslation(lightingEntry
                            ? "screen.autotorch.edit_lighting" : "screen.autotorch.edit_exclusion").getString(), button -> {
                int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                boolean editing = lightingEntry
                        ? SelectionState.beginEditingLightingZone()
                        : SelectionState.beginEditingExclusion(exclusionIndex);
                if (editing) {
                    minecraft.displayGuiScreen(new LightingScreen());
                }
            }));
            tooltips.put(editButton, new TextComponentTranslation(lightingEntry
                    ? "screen.autotorch.edit_lighting.tooltip" : "screen.autotorch.edit_exclusion.tooltip"));
            addRenderableWidget(new ColoredButton(left + panelWidth - 54, y, 54, 20,
                    new TextComponentTranslation("screen.autotorch.delete_zone"), button -> {
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
                new TextComponentTranslation("screen.autotorch.previous_page").getString(), button -> {
            page--;
            rebuildWidgets();
        }));
        previous.active = page > 0;

        addRenderableWidget(new Button(width / 2 - 50, 184, 100, 20,
                new TextComponentTranslation("screen.autotorch.back").getString(), button -> onClose()));

        Button next = addRenderableWidget(new Button(left + panelWidth - 80, 184, 80, 20,
                new TextComponentTranslation("screen.autotorch.next_page").getString(), button -> {
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
    public void render(int mouseX, int mouseY, float partialTick) {
        super.render(mouseX, mouseY, partialTick);
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        drawCenteredString(font, title.getString(), width / 2, 12, 0xFFFFFFFF);
        if (total == 0) {
            drawCenteredString(font, new TextComponentTranslation("screen.autotorch.no_zone").getString(),
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
            if (font.getStringWidth(description) > availableWidth) {
                description = font.trimStringToWidth(description,
                        Math.max(0, availableWidth - font.getStringWidth("..."))) + "...";
            }
            drawString(font, description, left + 4, y,
                    lightingEntry ? LIGHTING_TEXT_COLOR : EXCLUSION_TEXT_COLOR);
        }
        drawCenteredString(font, new TextComponentTranslation("screen.autotorch.page_summary",
                page + 1, maxPage(total) + 1, lightingZone == null ? 0 : 1, exclusions.size()).getString(),
                width / 2, 212, 0xFFA0A0A0);
        for (Map.Entry<Button, ITextComponent> entry : tooltips.entrySet()) {
            if (entry.getKey().visible && entry.getKey().isMouseOver(mouseX, mouseY)) {
                renderTooltip(entry.getValue().getString(), mouseX, mouseY);
                break;
            }
        }
    }

    private static ITextComponent describeLighting(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new TextComponentTranslation("screen.autotorch.lighting_sphere_row", format(zone.first()), zone.radius());
        }
        return new TextComponentTranslation("screen.autotorch.lighting_box_row", format(zone.first()), format(zone.second()));
    }

    private static ITextComponent describeExclusion(int index, AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new TextComponentTranslation("screen.autotorch.exclusion_sphere_row",
                    index + 1, format(zone.first()), zone.radius());
        }
        return new TextComponentTranslation("screen.autotorch.exclusion_box_row",
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
        minecraft.displayGuiScreen(new LightingScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
