package com.sakurakugu.autotorch.client;

import java.util.List;

import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.client.Screen;
import net.minecraft.util.BlockPos;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

/** 列出照明范围和所有排除区，并提供分页、原位修改、单条删除和一键清空。 */
public final class ExclusionListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int LIGHTING_TEXT_COLOR = 0xFF50FF70;
    private static final int EXCLUSION_TEXT_COLOR = 0xFFFF5050;
    private int page;
    private IChatComponent status = new ChatComponentText("");

    public ExclusionListScreen() {
        super(new ChatComponentTranslation("screen.autotorch.exclusions_title"));
    }

    @Override
    protected void init() {
        // 1.8.9 的按钮输入列表是 buttonList，绘制/文本框列表是 children()。
        buttonList.clear();
        children().clear();
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
                    new ChatComponentTranslation(lightingEntry
                            ? "screen.autotorch.edit_lighting" : "screen.autotorch.edit_exclusion"),
                    button -> {
                        int exclusionIndex = selectedIndex - (SelectionState.lightingZone() == null ? 0 : 1);
                        boolean editing = lightingEntry
                                ? SelectionState.beginEditingLightingZone()
                                : SelectionState.beginEditingExclusion(exclusionIndex);
                        if (editing) {
                            minecraft.displayGuiScreen(new LightingScreen());
                        }
                    }));
            LegacyTooltips.set(edit, new ChatComponentTranslation(lightingEntry
                    ? "screen.autotorch.edit_lighting.tooltip" : "screen.autotorch.edit_exclusion.tooltip"));
            addButton(new IconButton(left + panelWidth - 20, y, 20, 20, PixelGlyph.TRASH,
                    new ChatComponentTranslation("screen.autotorch.delete_zone"), button -> {
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
                new ChatComponentTranslation("screen.autotorch.back"), button -> onClose()));
        addButton(new IconButton(left + panelWidth - 20, 8, 20, 20, PixelGlyph.RESET,
                new ChatComponentTranslation("screen.autotorch.clear_all_zones"), button -> {
                    SelectionState.clearZones();
                    status = new ChatComponentTranslation("screen.autotorch.zones_cleared");
                    page = 0;
                    rebuildWidgets();
                }));

        // 底部上一页、下一页同样改用图标，页码居中显示在两者之间。
        int pageHalfWidth = (font.getStringWidth(LegacyText.string(pageSummary())) + 1) / 2;
        addButton(new IconButton(width / 2 - pageHalfWidth - 28, height - 32, 20, 20,
                PixelGlyph.ARROW_LEFT, new ChatComponentTranslation("screen.autotorch.previous_page"), button -> {
            page--;
            rebuildWidgets();
        })).active = page > 0;
        addButton(new IconButton(width / 2 + pageHalfWidth + 8, height - 32, 20, 20,
                PixelGlyph.ARROW_RIGHT, new ChatComponentTranslation("screen.autotorch.next_page"), button -> {
            page++;
            rebuildWidgets();
        })).active = page < maxPage;
    }

    private void rebuildWidgets() {
        // 直接调用 init() 重建页面时，需要同时清空旧按钮与绘制列表。
        buttonList.clear();
        children().clear();
        init();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        renderBackground();
        super.render(mouseX, mouseY, partialTick);
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        int total = exclusions.size() + (lightingZone == null ? 0 : 1);
        int panelWidth = Math.min(420, width - 20);
        int left = (width - panelWidth) / 2;
        int firstIndex = page * PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + PAGE_SIZE, total);

        drawCenteredString(font, LegacyText.string(title), width / 2, 14, 0xFFFFFFFF);
        if (!status.getFormattedText().isEmpty()) {
            drawCenteredString(font, LegacyText.string(status), width / 2, height - 46, 0xFFB0B0B0);
        }
        if (total == 0) {
            drawCenteredString(font, LegacyText.string(new ChatComponentTranslation("screen.autotorch.no_zone")), width / 2, 82, 0xFFA0A0A0);
        }
        for (int index = firstIndex; index < lastIndex; index++) {
            int y = 42 + (index - firstIndex) * 24;
            boolean lightingEntry = lightingZone != null && index == 0;
            int exclusionIndex = index - (lightingZone == null ? 0 : 1);
            String description = lightingEntry
                    ? describeLighting(lightingZone).getFormattedText()
                    : describeExclusion(exclusionIndex, exclusions.get(exclusionIndex)).getFormattedText();
            int availableWidth = panelWidth - 52;
            if (font.getStringWidth(description) > availableWidth) {
                description = font.trimStringToWidth(description, Math.max(0, availableWidth - font.getStringWidth("..."))) + "...";
            }
            drawString(font, description, left + 4, y,
                    lightingEntry ? LIGHTING_TEXT_COLOR : EXCLUSION_TEXT_COLOR);
        }
        drawCenteredString(font, LegacyText.string(pageSummary()), width / 2, height - 25, 0xFFB0B0B0);
        LegacyTooltips.render(this, font, mouseX, mouseY);
    }

    private IChatComponent pageSummary() {
        int lightingCount = SelectionState.lightingZone() == null ? 0 : 1;
        int exclusionCount = SelectionState.exclusions().size();
        int total = lightingCount + exclusionCount;
        return new ChatComponentTranslation("screen.autotorch.page_summary",
                page + 1, maxPage(total) + 1, lightingCount, exclusionCount);
    }

    private static IChatComponent describeLighting(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new ChatComponentTranslation("screen.autotorch.lighting_sphere_row", format(zone.first()), zone.radius());
        }
        return new ChatComponentTranslation("screen.autotorch.lighting_box_row", format(zone.first()), format(zone.second()));
    }

    private static IChatComponent describeExclusion(int index, AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            return new ChatComponentTranslation("screen.autotorch.exclusion_sphere_row",
                    index + 1, format(zone.first()), zone.radius());
        }
        return new ChatComponentTranslation("screen.autotorch.exclusion_box_row",
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
