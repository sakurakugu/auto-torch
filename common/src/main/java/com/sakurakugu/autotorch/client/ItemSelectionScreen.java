package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.client.ui.LegacyWidgetSprites;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import net.minecraft.client.Minecraft;
import com.sakurakugu.autotorch.client.Button;
import com.sakurakugu.autotorch.client.EditBox;
import com.sakurakugu.autotorch.client.Screen;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.NonNullList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

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
    private List<ItemStack> filtered = java.util.Arrays.asList();
    private String query = "";
    private String selectedId;
    private int groupIndex;
    private int page;
    private int columns;
    private int gridRows;
    private CategoryDropdown categoryDropdown;
    private Button previous;
    private Button next;
    private IconButton backButton;

    ItemSelectionScreen(Screen parent, String current, boolean blocksOnly, Consumer<String> selected) {
        super(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_title"));
        this.parent = parent;
        this.selectedId = current;
        this.blocksOnly = blocksOnly;
        this.selected = selected;
    }

    @Override
    protected void init() {
        // 返回此页面或缩放窗口时，同时清空旧控件的绘制列表和输入列表。
        buttons.clear();
        children().clear();
        itemButtons.clear();
        if (groups.isEmpty()) loadGroups();
        columns = Math.max(1, Math.min(12, (width - 24) / CELL_SIZE));
        gridRows = Math.max(1, (height - 126) / CELL_SIZE);
        int left = (width - columns * CELL_SIZE) / 2;
        int contentWidth = columns * CELL_SIZE;
        EditBox search = new EditBox(font, left, 35, contentWidth, 16,
                LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_search")));
        search.setMaxLength(128);
        search.setSuggestion(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_search").getFormattedText());
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; filterItems(); });
        addButton(search);
        addButton(new IconButton(left, 58, 20, 20, PixelGlyph.ARROW_LEFT,
                new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.previous_page"), button -> changeGroup(-1)));
        int dropdownX = left + 24;
        int dropdownWidth = Math.max(DROPDOWN_WIDTH, contentWidth - 48);
        addButton(new IconButton(left + contentWidth - 20, 58, 20, 20, PixelGlyph.ARROW_RIGHT,
                new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.next_page"), button -> changeGroup(1)));
        for (int row = 0; row < gridRows; row++) {
            for (int column = 0; column < columns; column++) {
                itemButtons.add(addButton(new ItemButton(left + column * CELL_SIZE,
                        84 + row * CELL_SIZE)));
            }
        }
        previous = addButton(new IconButton(left, height - 30, 20, 20, PixelGlyph.ARROW_LEFT,
                new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.previous_page"), button -> changePage(-1)));
        next = addButton(new IconButton(left + contentWidth - 20, height - 30, 20, 20,
                PixelGlyph.ARROW_RIGHT, new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.next_page"),
                button -> changePage(1)));
        backButton = addButton(new IconButton(8, 8, 20, 20, PixelGlyph.BACK,
                new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.back"), button -> onClose()));
        // 下拉控件最后加入，确保展开列表在所有物品与翻页控件之后绘制。
        categoryDropdown = addButton(new CategoryDropdown(dropdownX, 58, dropdownWidth, 21));
        filterItems();
        setInitialFocus(search);
    }

    private void loadGroups() {
        // 旧版创造分类由静态数组维护，直接遍历分类即可得到分类物品列表。
        List<ItemStack> all = new ArrayList<>();
        // 注册表兜底也包含没有加入创造分类的模组物品。
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR) all.add(item.getDefaultInstance());
        }
        groups.add(new Group(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_all"), available(all),
                ItemGroup.SEARCH.getIcon()));
        if (minecraft.world != null) {
            for (ItemGroup tab : ItemGroup.GROUPS) {
                // 旧版分类数组中可能夹带空位，也可能包含只在创造界面动态填充的分类。
                if (tab == null || isClientOnlyTab(tab)) continue;
                List<ItemStack> items = available(tabItems(tab));
                if (!items.isEmpty()) groups.add(new Group(new net.minecraft.util.text.TextComponentTranslation(tabTranslationKey(tab)), items, tab.getIcon()));
            }
        }
    }

    private static String tabTranslationKey(ItemGroup tab) {
        String name = tab.func_200300_c();
        if ("building_blocks".equals(name)) name = "buildingBlocks";
        return "itemGroup." + name;
    }

    /** 快捷栏、背包与搜索分类的物品由创造界面动态生成，旧版在客户端读取会抛异常或为空。 */
    private static boolean isClientOnlyTab(ItemGroup tab) {
        return tab == ItemGroup.HOTBAR
                || tab == ItemGroup.INVENTORY
                || tab == ItemGroup.SEARCH;
    }

    /** 读取旧版创造分类的展示物品；个别分类拒绝客户端读取时退化为空列表。 */
    private static List<ItemStack> tabItems(ItemGroup tab) {
        NonNullList<ItemStack> items = NonNullList.create();
        try {
            tab.fill(items);
        } catch (RuntimeException exception) {
            return java.util.Arrays.asList();
        }
        return items;
    }

    private List<ItemStack> available(Iterable<ItemStack> stacks) {
        // 配置只保存物品 ID，同一物品的药水、附魔等变体只显示一次。
        LinkedHashMap<Item, ItemStack> items = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || blocksOnly && !(stack.getItem() instanceof ItemBlock)) continue;

            items.putIfAbsent(stack.getItem(), stack);
        }
        return com.google.common.collect.ImmutableList.copyOf(items.values());
    }

    private void filterItems() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = groups.get(groupIndex).items().stream().filter(stack -> needle.isEmpty()
                || ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().contains(needle)
                || stack.getTextComponent().getFormattedText().toLowerCase(Locale.ROOT).contains(needle)).collect(java.util.stream.Collectors.toList());
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
        LegacyTooltips.set(categoryDropdown, groups.get(groupIndex).name());
        for (int i = 0; i < itemButtons.size(); i++) {
            int index = page * itemButtons.size() + i;
            itemButtons.get(i).setStack(index < filtered.size() ? filtered.get(index) : ItemStack.EMPTY);
        }
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        // 分类下拉展开时，滚轮优先滚动分类列表。
        if (categoryDropdown.handlePopupScroll(x, y, scrollY)) return true;
        if (super.mouseScrolled(x, y, scrollY)) return true;
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
        if (categoryDropdown.isOpen() && (keyCode == 256)) {
            categoryDropdown.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        renderBackground();
        super.render(mouseX, mouseY, partialTick);
        for (net.minecraft.client.gui.IGuiEventListener child : children()) {
            if (child instanceof EditBox) ((EditBox) child).render(mouseX, mouseY, partialTick);
        }
        drawCenteredString(font, LegacyText.string(title), width / 2, 12, 0xFFFFFFFF);
        drawCenteredString(font, LegacyText.string(new net.minecraft.util.text.TextComponentTranslation(blocksOnly
                ? "screen.autotorch.item_picker_blocks_hint" : "screen.autotorch.item_picker_hint")),
                width / 2, 24, 0xFFB0B0B0);
        drawCenteredString(font, LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.config_page", page + 1, pages())),
                width / 2, height - 24, 0xFFE0E0E0);
        if (filtered.isEmpty()) {
            drawCenteredString(font, LegacyText.string(new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_empty")),
                    width / 2, 92, 0xFFB0B0B0);
        }
        LegacyTooltips.render(this, font, mouseX, mouseY);
    }

    @Override public void onClose() { minecraft.displayGuiScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    /** 分类列表展开时清掉会被它盖住的控件提示，避免原版把提示画到列表上方。 */
    private void clearCoveredTooltips() {
        for (ItemButton button : itemButtons) LegacyTooltips.clear(button);
        LegacyTooltips.clear(previous);
        LegacyTooltips.clear(next);
        // 弹层向上展开时可能盖住左上角返回按钮，一并清掉它的提示。
        LegacyTooltips.clear(backButton);
    }

    /** 分类列表收起后恢复物品格子的填充与底部翻页按钮的提示。 */
    private void restoreCoveredTooltips() {
        LegacyTooltips.set(previous, new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.previous_page"));
        LegacyTooltips.set(next, new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.next_page"));
        LegacyTooltips.set(backButton, new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.back"));
        refreshPage();
    }

    private static final class Group {
        private final ITextComponent name;
        private final List<ItemStack> items;
        private final ItemStack icon;
        public Group(ITextComponent name, List<ItemStack> items, ItemStack icon) {
            this.name = name;
            this.items = items;
            this.icon = icon;
        }
        public ITextComponent name() { return name; }
        public List<ItemStack> items() { return items; }
        public ItemStack icon() { return icon; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Group)) return false;
            Group that = (Group) other;
            return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(items, that.items) && java.util.Objects.equals(icon, that.icon);
        }
        @Override public int hashCode() { return java.util.Objects.hash(name, items, icon); }
 }

    /**
     * 页面中间的分类下拉按钮：点击展开全部创造分类，点击某一项直接切换。
     * 列表展开在物品格子上方，因此由 Screen 在最后绘制并优先处理点击与滚轮。
     */
    private final class CategoryDropdown extends Button {
        private boolean open;
        /** 分类太多、列表放不下时的滚动行数。 */
        private int scroll;

        CategoryDropdown(int x, int y, int width, int height) {
            super(x, y, width, height, LegacyText.string(new net.minecraft.util.text.TextComponentString("")), button -> { });
            LegacyTooltips.set(this, new net.minecraft.util.text.TextComponentTranslation("screen.autotorch.item_picker_all_categories"));
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            // 沿用物品格的深灰底色与描边，展开时描边变亮。
            fill(x, y, (x + getWidth()) - 1, (y + height) - 1,
                    (isHovered() || isFocused()) ? 0xFF666666 : 0xFF303030);
            LegacyWidgetSprites.outline(x, y, getWidth() - 1, height - 1, open ? 0xFF70A0FF : 0xFF888888);
            ItemStack icon = groups.get(groupIndex).icon();
            Minecraft.getInstance().getItemRenderer().renderItemAndEffectIntoGUI(icon, x + 3, y + 2);
            String name = font.trimStringToWidth(groups.get(groupIndex).name().getFormattedText(), getWidth() - 30);
            font.drawString(name, x + 23, y + 6, 0xFFE0E0E0);
            font.drawString(open ? "▲" : "▼", (x + getWidth()) - 12, y + 7, 0xFFE0E0E0);
            // 控件本身最后绘制，展开列表随之处于所有物品格之上。
            renderPopup(mouseX, mouseY);
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
            for (Group group : groups) labelWidth = Math.max(labelWidth, font.getStringWidth(LegacyText.string(group.name())));
            return Math.max(getWidth(), Math.min(labelWidth + 34, ItemSelectionScreen.this.width - 16));
        }

        /** 按钮下方能完整显示的分类行数。 */
        private int rowsBelow() {
            return Math.max(0, (ItemSelectionScreen.this.height - 8 - (y + height)) / POPUP_ROW_HEIGHT);
        }

        /** 按钮上方能完整显示的分类行数。 */
        private int rowsAbove() {
            return Math.max(0, (y - 8) / POPUP_ROW_HEIGHT);
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
            return Math.max(8, Math.min((x + getWidth()) - popupWidth(),
                    ItemSelectionScreen.this.width - 8 - popupWidth()));
        }

        private int popupTop() {
            int popupHeight = visibleRows() * POPUP_ROW_HEIGHT + 2;
            return opensUpward() ? y - popupHeight : y + this.height;
        }

        /** 在屏幕最后绘制分类列表，保证它盖住物品格子。 */
        void renderPopup(int mouseX, int mouseY) {
            if (!open) return;
            int x = popupLeft();
            int y = popupTop();
            int width = popupWidth();
            int rows = visibleRows();
            int height = rows * POPUP_ROW_HEIGHT + 2;
            scroll = Math.max(0, Math.min(scroll, groups.size() - rows));
            // 物品图标本身绘制在较高的 GUI 深度，弹出层必须再抬高，否则背景会被物品格压住。
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translated(0.0, 0.0, 300.0);
            try {
                fill(x, y, x + width, y + height, 0xFF202020);
                LegacyWidgetSprites.outline(x, y, width, height, 0xFF888888);
                for (int row = 0; row < rows; row++) {
                    int rowY = y + 1 + row * POPUP_ROW_HEIGHT;
                    boolean hovered = mouseX >= x + 1 && mouseX < x + width - 1
                            && mouseY >= rowY && mouseY < rowY + POPUP_ROW_HEIGHT;
                    if (hovered) fill(x + 1, rowY, x + width - 1, rowY + POPUP_ROW_HEIGHT, 0xFF666666);
                }
                // 分类多于可见行时，在列表右侧画出滚动条。
                if (groups.size() > rows) {
                    int trackTop = y + 2;
                    int trackHeight = height - 4;
                    int thumbHeight = Math.max(8, trackHeight * rows / groups.size());
                    int travel = trackHeight - thumbHeight;
                    int thumbTop = trackTop + travel * scroll / (groups.size() - rows);
                    fill(x + width - 4, trackTop, x + width - 2, trackTop + trackHeight, 0xFF404040);
                    fill(x + width - 4, thumbTop, x + width - 2, thumbTop + thumbHeight, 0xFFB0B0B0);
                }
                // 先提交不透明背景，再绘制分类图标和文字，避免物品渲染批次覆盖背景。
                // 旧版物品渲染器使用独立的深度偏移，需要同时抬高图标。
                float oldBlitOffset = Minecraft.getInstance().getItemRenderer().zLevel;
                Minecraft.getInstance().getItemRenderer().zLevel = 400.0F;
                try {
                    for (int row = 0; row < rows; row++) {
                        int index = scroll + row;
                        if (index >= groups.size()) break;
                        int rowY = y + 1 + row * POPUP_ROW_HEIGHT;
                        int color = index == groupIndex ? 0xFF70A0FF : 0xFFE0E0E0;
                        Minecraft.getInstance().getItemRenderer().renderItemAndEffectIntoGUI(
                                groups.get(index).icon(), x + 2, rowY + 1);
                        String name = font.trimStringToWidth(groups.get(index).name().getFormattedText(), width - 28);
                        font.drawString(name, x + 21, rowY + 4, color);
                    }
                } finally {
                    Minecraft.getInstance().getItemRenderer().zLevel = oldBlitOffset;
                }
            } finally {
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
        }

        /** 处理展开列表的点击；点击按钮本身时交回给父类切换展开状态。 */
        boolean handlePopupClick(double mouseX, double mouseY, int button) {
            if (!open) return false;
            if (isMouseOver(mouseX, mouseY)) return false;
            if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1) {
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
            super(x, y, CELL_SIZE, CELL_SIZE, LegacyText.string(new net.minecraft.util.text.TextComponentString("")), button -> {
                ItemButton item = (ItemButton) button;
                if (!item.stack.isEmpty()) {
                    selectedId = ForgeRegistries.ITEMS.getKey(item.stack.getItem()).toString();
                    selected.accept(selectedId);
                }
            });
        }

        void setStack(ItemStack value) {
            stack = value;
            visible = active = !stack.isEmpty();
            if (!stack.isEmpty()) {
                String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                setMessage(LegacyText.string(stack.getTextComponent().createCopy().appendText(" (" + id + ")")));
                LegacyTooltips.set(this, stack.getTextComponent().createCopy().appendText("\n" + id));
            }
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            boolean current = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(selectedId);
            fill(x, y, (x + getWidth()) - 1, (y + height) - 1,
                    (isHovered() || isFocused()) ? 0xFF666666 : 0xFF303030);
            LegacyWidgetSprites.outline(x, y, getWidth() - 1, height - 1, current ? 0xFF70A0FF : 0xFF888888);
            Minecraft.getInstance().getItemRenderer().renderItemAndEffectIntoGUI(stack, x + 3, y + 3);
        }
    }
}

