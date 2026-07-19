package dev.elric.autotorch.client;

import dev.elric.autotorch.network.AreaShape;
import dev.elric.autotorch.network.AreaZone;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 列出所有排除区，并提供分页、原位修改和单条删除。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private int page;

    public ExclusionListScreen() {
        super(Component.translatable("screen.autotorch.exclusions_title"));
    }

    @Override
    protected void init() {
        List<AreaZone> exclusions = SelectionState.exclusions();
        int maxPage = maxPage(exclusions.size());
        page = Math.min(page, maxPage);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, exclusions.size());

        for (int index = firstIndex; index < lastIndex; index++) {
            int row = index - firstIndex;
            int y = 34 + row * 24;
            int selectedIndex = index;
            addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.edit_exclusion"), button -> {
                if (SelectionState.beginEditingExclusion(selectedIndex)) {
                    minecraft.setScreen(new LightingScreen());
                }
            }).tooltip(Tooltip.create(Component.translatable("screen.autotorch.edit_exclusion.tooltip")))
                    .bounds(left + panelWidth - 108, y, 50, 20).build());
            addRenderableWidget(new ColoredButton(left + panelWidth - 54, y, 54, 20,
                    Component.translatable("screen.autotorch.delete_exclusion"), button -> {
                        SelectionState.removeExclusion(selectedIndex);
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
        List<AreaZone> exclusions = SelectionState.exclusions();
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, exclusions.size());

        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (exclusions.isEmpty()) {
            graphics.centeredText(font, Component.translatable("screen.autotorch.no_exclusion"), width / 2, 82, 0xFFA0A0A0);
        }
        for (int index = firstIndex; index < lastIndex; index++) {
            int y = 40 + (index - firstIndex) * 24;
            String description = describe(index, exclusions.get(index)).getString();
            int availableWidth = panelWidth - 116;
            if (font.width(description) > availableWidth) {
                description = font.plainSubstrByWidth(description, Math.max(0, availableWidth - font.width("..."))) + "...";
            }
            graphics.text(font, description, left + 4, y, 0xFFFFFFFF);
        }
        graphics.centeredText(font, Component.translatable("screen.autotorch.page_summary",
                page + 1, maxPage(exclusions.size()) + 1, exclusions.size()), width / 2, 212, 0xFFA0A0A0);
    }

    private static Component describe(int index, AreaZone zone) {
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
