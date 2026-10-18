package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 只读的创造物品选择器：复用创造分类，点击后仅回填 ID，不改变玩家背包。 */
final class ItemSelectionScreen extends Screen {
    private static final int CELL_SIZE = 22;
    /** 中间分类下拉按钮的最小宽度。 */
    private static final int DROPDOWN_WIDTH = 92;
    /** 展开后每个分类选项的行高。 */
    private static final int POPUP_ROW_HEIGHT = 16;
    private final Screen parent;
    private final boolean blocksOnly;
    private final Consumer<String> selected;
    private final List<Group> groups = new ArrayList<>();
    private final List<ItemButton> itemButtons = new ArrayList<>();
    private List<ItemStack> filtered = List.of();
    private String query = "";
    private String selectedId;
    private int groupIndex;
    private int page;
    private int columns;
    private int gridRows;
    private CategoryDropdown categoryDropdown;
    private Button previous;
    private Button next;

    ItemSelectionScreen(Screen parent, String current, boolean blocksOnly, Consumer<String> selected) {
        super(Component.translatable("screen.autotorch.item_picker_title"));
        this.parent = parent;
        this.selectedId = current;
        this.blocksOnly = blocksOnly;
        this.selected = selected;
    }

    @Override
    protected void init() {
        clearWidgets();
        itemButtons.clear();
        if (groups.isEmpty()) loadGroups();
        columns = Math.max(1, Math.min(12, (width - 24) / CELL_SIZE));
        gridRows = Math.max(1, (height - 126) / CELL_SIZE);
        int left = (width - columns * CELL_SIZE) / 2;
        int contentWidth = columns * CELL_SIZE;
        EditBox search = new EditBox(font, left, 34, contentWidth, 18,
                Component.translatable("screen.autotorch.item_picker_search"));
        search.setMaxLength(128);
        search.setHint(Component.translatable("screen.autotorch.item_picker_search"));
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; filterItems(); });
        addRenderableWidget(search);
        addRenderableWidget(new IconButton(left, 58, 20, 20, PixelGlyph.ARROW_LEFT,
                Component.translatable("screen.autotorch.previous_page"), button -> changeGroup(-1)));
        int dropdownX = left + 24;
        int dropdownWidth = Math.max(DROPDOWN_WIDTH, contentWidth - 48);
        addRenderableWidget(new IconButton(left + contentWidth - 20, 58, 20, 20, PixelGlyph.ARROW_RIGHT,
                Component.translatable("screen.autotorch.next_page"), button -> changeGroup(1)));
        for (int row = 0; row < gridRows; row++) {
            for (int column = 0; column < columns; column++) {
                itemButtons.add(addRenderableWidget(new ItemButton(left + column * CELL_SIZE,
                        84 + row * CELL_SIZE)));
            }
        }
        previous = addRenderableWidget(new IconButton(left, height - 30, 20, 20, PixelGlyph.ARROW_LEFT,
                Component.translatable("screen.autotorch.previous_page"), button -> changePage(-1)));
        next = addRenderableWidget(new IconButton(left + contentWidth - 20, height - 30, 20, 20,
                PixelGlyph.ARROW_RIGHT, Component.translatable("screen.autotorch.next_page"),
                button -> changePage(1)));
        addRenderableWidget(new IconButton(8, 8, 20, 20, PixelGlyph.BACK,
                Component.translatable("screen.autotorch.back"), button -> onClose()));
        // 下拉控件最后加入，确保展开列表在所有物品与翻页控件之后绘制。
        categoryDropdown = addRenderableWidget(new CategoryDropdown(dropdownX, 58, dropdownWidth, 21));
        filterItems();
        setInitialFocus(search);
    }

    private void loadGroups() {
        // 使用当前世界的功能开关和注册表，兼容模组增加的创造分类。
        if (minecraft.level != null && minecraft.getConnection() != null) {
            CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(),
                    minecraft.player != null && minecraft.player.canUseGameMasterBlocks(),
                    minecraft.getConnection().registryAccess());
        }
        List<ItemStack> all = new ArrayList<>();
        if (minecraft.level != null) all.addAll(CreativeModeTabs.searchTab().getDisplayItems());
        // 注册表兜底也包含没有加入创造分类的模组物品。
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) all.add(item.getDefaultInstance());
        }
        groups.add(new Group(Component.translatable("screen.autotorch.item_picker_all"), available(all),
                CreativeModeTabs.searchTab().getIconItem()));
        if (minecraft.level != null) {
            for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.shouldDisplay()) {
                    List<ItemStack> items = available(tab.getDisplayItems());
                    if (!items.isEmpty()) groups.add(new Group(tab.getDisplayName(), items, tab.getIconItem()));
                }
            }
        }
    }

    private List<ItemStack> available(Iterable<ItemStack> stacks) {
        // 配置只保存物品 ID，同一物品的药水、附魔等变体只显示一次。
        LinkedHashMap<Item, ItemStack> items = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || blocksOnly && !(stack.getItem() instanceof BlockItem)) continue;
            if (minecraft.level != null && !stack.isItemEnabled(minecraft.level.enabledFeatures())) continue;
            items.putIfAbsent(stack.getItem(), stack);
        }
        return List.copyOf(items.values());
    }

    private void filterItems() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = groups.get(groupIndex).items().stream().filter(stack -> needle.isEmpty()
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(needle)
                || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        refreshPage();
    }

    private void changeGroup(int delta) {
        groupIndex = Math.floorMod(groupIndex + delta, groups.size());
        page = 0;
        filterItems();
    }

    /** 从右侧分类下拉列表直接切到指定分类。 */
    private void selectGroup(int index) {
        groupIndex = Math.floorMod(index, groups.size());
        page = 0;
        filterItems();
    }

    private int pages() { return Math.max(1, (filtered.size() + itemButtons.size() - 1) / itemButtons.size()); }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        refreshPage();
    }

    private void refreshPage() {
        page = Math.min(page, pages() - 1);
        categoryDropdown.setTooltip(Tooltip.create(groups.get(groupIndex).name()));
        for (int i = 0; i < itemButtons.size(); i++) {
            int index = page * itemButtons.size() + i;
            itemButtons.get(i).setStack(index < filtered.size() ? filtered.get(index) : ItemStack.EMPTY);
        }
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // 分类下拉展开时，滚轮优先滚动分类列表。
        if (categoryDropdown.handlePopupScroll(x, y, scrollY)) return true;
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;
        if (scrollY == 0) return false;
        changePage(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 下拉列表盖在物品格子上，必须优先处理它的点击，否则会被下面的物品按钮抢走。
        if (categoryDropdown.handlePopupClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 展开分类列表时，Esc 只收起列表，不退出页面。
        if (categoryDropdown.isOpen() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            categoryDropdown.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(blocksOnly
                ? "screen.autotorch.item_picker_blocks_hint" : "screen.autotorch.item_picker_hint"),
                width / 2, 24, 0xFFB0B0B0);
        graphics.drawCenteredString(font, Component.translatable("screen.autotorch.config_page", page + 1, pages()),
                width / 2, height - 24, 0xFFE0E0E0);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.autotorch.item_picker_empty"),
                    width / 2, 92, 0xFFB0B0B0);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    /** 分类列表展开时清掉会被它盖住的控件提示，避免原版把提示画到列表上方。 */
    private void clearCoveredTooltips() {
        for (ItemButton button : itemButtons) button.setTooltip(null);
        previous.setTooltip(null);
        next.setTooltip(null);
    }

    /** 分类列表收起后恢复物品格子的填充与底部翻页按钮的提示。 */
    private void restoreCoveredTooltips() {
        previous.setTooltip(Tooltip.create(Component.translatable("screen.autotorch.previous_page")));
        next.setTooltip(Tooltip.create(Component.translatable("screen.autotorch.next_page")));
        refreshPage();
    }

    private record Group(Component name, List<ItemStack> items, ItemStack icon) { }

    /**
     * 页面中间的分类下拉按钮：点击展开全部创造分类，点击某一项直接切换。
     * 列表展开在物品格子上方，因此由 Screen 在最后绘制并优先处理点击与滚轮。
     */
    private final class CategoryDropdown extends Button {
        private boolean open;
        /** 分类太多、列表放不下时的滚动行数。 */
        private int scroll;

        CategoryDropdown(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), button -> { }, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable("screen.autotorch.item_picker_all_categories")));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // 沿用物品格的深灰底色与描边，展开时描边变亮。
            graphics.fill(getX(), getY(), getRight() - 1, getBottom() - 1,
                    isHoveredOrFocused() ? 0xFF666666 : 0xFF303030);
            graphics.renderOutline(getX(), getY(), getWidth() - 1, getHeight() - 1, open ? 0xFF70A0FF : 0xFF888888);
            ItemStack icon = groups.get(groupIndex).icon();
            graphics.renderFakeItem(icon, getX() + 3, getY() + 2);
            String name = font.plainSubstrByWidth(groups.get(groupIndex).name().getString(), getWidth() - 30);
            graphics.drawString(font, name, getX() + 23, getY() + 6, 0xFFE0E0E0, false);
            graphics.drawString(font, open ? "▲" : "▼", getRight() - 12, getY() + 7, 0xFFE0E0E0, false);
            // 控件本身最后绘制，展开列表随之处于所有物品格之上。
            renderPopup(graphics, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isValidClickButton(button) || !isMouseOver(mouseX, mouseY)) return false;
            open = !open;
            scroll = 0;
            if (open) clearCoveredTooltips();
            else restoreCoveredTooltips();
            return true;
        }

        boolean isOpen() {
            return open;
        }

        void close() {
            if (!open) return;
            open = false;
            restoreCoveredTooltips();
        }

        /** 展开列表的宽度：至少与按钮同宽，长分类名时加宽，但不超出窗口。 */
        private int popupWidth() {
            int labelWidth = 0;
            for (Group group : groups) labelWidth = Math.max(labelWidth, font.width(group.name()));
            return Math.max(getWidth(), Math.min(labelWidth + 34, ItemSelectionScreen.this.width - 16));
        }

        /** 按钮下方能完整显示的分类行数。 */
        private int rowsBelow() {
            return Math.max(0, (ItemSelectionScreen.this.height - 8 - getBottom()) / POPUP_ROW_HEIGHT);
        }

        /** 按钮上方能完整显示的分类行数。 */
        private int rowsAbove() {
            return Math.max(0, (getY() - 8) / POPUP_ROW_HEIGHT);
        }

        /** 下方放不下且上方空间更大时向上展开。 */
        private boolean opensUpward() {
            return rowsBelow() < groups.size() && rowsAbove() > rowsBelow();
        }

        /** 当前列表实际显示的分类行数，放不下时用滚轮查看其余分类。 */
        private int visibleRows() {
            int fit = opensUpward() ? rowsAbove() : rowsBelow();
            return Math.max(1, Math.min(groups.size(), fit));
        }

        private int popupLeft() {
            return Math.max(8, Math.min(getRight() - popupWidth(),
                    ItemSelectionScreen.this.width - 8 - popupWidth()));
        }

        private int popupTop() {
            int height = visibleRows() * POPUP_ROW_HEIGHT + 2;
            return opensUpward() ? getY() - height : getBottom();
        }

        /** 在屏幕最后绘制分类列表，保证它盖住物品格子。 */
        void renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!open) return;
            int x = popupLeft();
            int y = popupTop();
            int width = popupWidth();
            int rows = visibleRows();
            int height = rows * POPUP_ROW_HEIGHT + 2;
            scroll = Math.max(0, Math.min(scroll, groups.size() - rows));
            // 物品图标本身绘制在较高的 GUI 深度，弹出层必须再抬高，否则背景会被物品格压住。
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0, 0.0, 300.0);
            try {
                graphics.fill(x, y, x + width, y + height, 0xFF202020);
                graphics.renderOutline(x, y, width, height, 0xFF888888);
                for (int row = 0; row < rows; row++) {
                    int rowY = y + 1 + row * POPUP_ROW_HEIGHT;
                    boolean hovered = mouseX >= x + 1 && mouseX < x + width - 1
                            && mouseY >= rowY && mouseY < rowY + POPUP_ROW_HEIGHT;
                    if (hovered) graphics.fill(x + 1, rowY, x + width - 1, rowY + POPUP_ROW_HEIGHT, 0xFF666666);
                }
                // 分类多于可见行时，在列表右侧画出滚动条。
                if (groups.size() > rows) {
                    int trackTop = y + 2;
                    int trackHeight = height - 4;
                    int thumbHeight = Math.max(8, trackHeight * rows / groups.size());
                    int travel = trackHeight - thumbHeight;
                    int thumbTop = trackTop + travel * scroll / (groups.size() - rows);
                    graphics.fill(x + width - 4, trackTop, x + width - 2, trackTop + trackHeight, 0xFF404040);
                    graphics.fill(x + width - 4, thumbTop, x + width - 2, thumbTop + thumbHeight, 0xFFB0B0B0);
                }
                // 先提交不透明背景，再绘制分类图标和文字，避免物品渲染批次覆盖背景。
                graphics.flush();
                for (int row = 0; row < rows; row++) {
                    int index = scroll + row;
                    if (index >= groups.size()) break;
                    int rowY = y + 1 + row * POPUP_ROW_HEIGHT;
                    int color = index == groupIndex ? 0xFF70A0FF : 0xFFE0E0E0;
                    graphics.renderFakeItem(groups.get(index).icon(), x + 2, rowY + 1);
                    String name = font.plainSubstrByWidth(groups.get(index).name().getString(), width - 28);
                    graphics.drawString(font, name, x + 21, rowY + 4, color, false);
                }
            } finally {
                graphics.pose().popPose();
            }
        }

        /** 处理展开列表的点击；点击按钮本身时交回给父类切换展开状态。 */
        boolean handlePopupClick(double mouseX, double mouseY, int button) {
            if (!open) return false;
            if (isMouseOver(mouseX, mouseY)) return false;
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                int x = popupLeft();
                int y = popupTop();
                int width = popupWidth();
                int height = visibleRows() * POPUP_ROW_HEIGHT + 2;
                if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                    // 最右侧的滚动条区域只用于观察位置，点击它不选择分类，也不关闭列表。
                    if (mouseX >= x + width - 5) return true;
                    int row = (int) Math.floor((mouseY - (y + 1)) / (double) POPUP_ROW_HEIGHT);
                    int index = scroll + row;
                    if (row >= 0 && index < groups.size()) selectGroup(index);
                }
            }
            open = false;
            restoreCoveredTooltips();
            return true;
        }

        /** 展开列表内的滚轮滚动；返回 true 时不再触发下方物品翻页。 */
        boolean handlePopupScroll(double mouseX, double mouseY, double scrollY) {
            if (!open) return false;
            int x = popupLeft();
            int y = popupTop();
            int width = popupWidth();
            int height = visibleRows() * POPUP_ROW_HEIGHT + 2;
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
            int rows = visibleRows();
            if (groups.size() > rows && scrollY != 0) {
                scroll = Math.max(0, Math.min(scroll + (scrollY > 0 ? -1 : 1), groups.size() - rows));
            }
            return true;
        }
    }

    private final class ItemButton extends Button {
        private ItemStack stack = ItemStack.EMPTY;

        ItemButton(int x, int y) {
            super(x, y, CELL_SIZE, CELL_SIZE, Component.empty(), button -> {
                ItemButton item = (ItemButton) button;
                if (!item.stack.isEmpty()) {
                    selectedId = BuiltInRegistries.ITEM.getKey(item.stack.getItem()).toString();
                    selected.accept(selectedId);
                }
            }, DEFAULT_NARRATION);
        }

        void setStack(ItemStack value) {
            stack = value;
            visible = active = !stack.isEmpty();
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                setMessage(stack.getHoverName().copy().append(" (" + id + ")"));
                setTooltip(Tooltip.create(stack.getHoverName().copy().append("\n" + id)));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean current = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(selectedId);
            graphics.fill(getX(), getY(), getRight() - 1, getBottom() - 1,
                    isHoveredOrFocused() ? 0xFF666666 : 0xFF303030);
            graphics.renderOutline(getX(), getY(), getWidth() - 1, getHeight() - 1, current ? 0xFF70A0FF : 0xFF888888);
            graphics.renderFakeItem(stack, getX() + 3, getY() + 3);
        }
    }
}
