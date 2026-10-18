package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.List;

import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import com.sakurakugu.autotorch.client.ui.SegmentedSwitchButton;
import com.sakurakugu.autotorch.client.ui.ToggleSwitchButton;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.ServerCategory;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.ServerConfigResetPayload;
import com.sakurakugu.autotorch.network.ServerConfigResultPayload;
import com.sakurakugu.autotorch.network.ServerConfigSetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 管理员编辑服务端配置的界面。
 * 界面只发送修改请求，实际校验与写盘由服务端完成，成功后再同步最新值回来。
 */
public final class ServerConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_ICON_SIZE = 16;
    private static final int ROW_ICON_GAP = 4;
    /** 行首图标的占位宽度：图标本身加右侧间距。 */
    private static final int ROW_LABEL_INDENT = ROW_ICON_SIZE + ROW_ICON_GAP;
    /** 每行右侧控件区总宽度：滑动条或开关、单项重置依次排列。 */
    private static final int CONTROL_RESERVED = 216;
    /** 分类标签行与标题、配置行之间的垂直位置。 */
    private static final int TABS_Y = 32;
    private static final int ROWS_TOP = 58;
    /** 跨度不超过该值的整数用滑动条，跨度更大的改用输入框，避免拖动精度不足。 */
    private static final int SLIDER_MAX_SPAN = 64;
    /** 间距上下限合并为一行双端点滑动条，轨道范围取两项配置范围的并集。 */
    private static final int SPACING_TRACK_MIN = Math.min(ConfigDefinitions.LIMIT_MIN_SPACING.minValue(),
            ConfigDefinitions.LIMIT_MAX_SPACING.minValue());
    private static final int SPACING_TRACK_MAX = Math.max(ConfigDefinitions.LIMIT_MIN_SPACING.maxValue(),
            ConfigDefinitions.LIMIT_MAX_SPACING.maxValue());

    /** 当前打开的界面实例，供网络线程投递操作结果。 */
    private static volatile ServerConfigScreen currentInstance;

    private ServerCategory category;
    private int page;
    private int pageSize = 4;
    private long seenRevision;
    private final List<ConfigSlider> sliders = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    /** 当前分类整理出的界面行，间距上下限在这里合并为一行。 */
    private List<RowSpec> rowSpecs = List.of();
    private volatile ServerConfigResultPayload pendingResult;
    private Component status = Component.translatable("screen.autotorch.server_config_hint");

    public ServerConfigScreen() {
        this(ServerCategory.RULES);
    }

    public ServerConfigScreen(ServerCategory category) {
        super(Component.translatable("screen.autotorch.server_config_title"));
        this.category = category;
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        sliders.clear();
        currentInstance = this;
        seenRevision = ServerConfigState.revision();
        rowSpecs = buildRowSpecs(category.definitions());
        // 底部留给状态文字和翻页按钮，其余高度按行均分。
        pageSize = Math.max(1, (height - ROWS_TOP - 52) / ROW_HEIGHT);
        int pages = pageCount();
        page = Math.min(page, pages - 1);
        int left = (width - contentWidth()) / 2;
        int valueX = left + contentWidth() - CONTROL_RESERVED;
        int first = page * pageSize;
        int last = Math.min(rowSpecs.size(), first + pageSize);
        for (int index = first; index < last; index++) {
            RowSpec spec = rowSpecs.get(index);
            int y = ROWS_TOP + (index - first) * ROW_HEIGHT;
            if (spec.kind() == RowKind.SPACING_RANGE) {
                addSpacingRangeRow(valueX, y);
                continue;
            }
            Value definition = spec.definition();
            PixelGlyph icon = definition instanceof BooleanValue ? PixelGlyph.SWITCH : PixelGlyph.SLIDER;
            rows.add(new Row(y, icon, rowLabel(definition), definition.key()));
            if (definition instanceof BooleanValue booleanValue) {
                ToggleSwitchButton toggle = new ToggleSwitchButton(valueX + 160, y, 32, 20,
                        Component.literal(rowLabel(definition)),
                        () -> ServerConfigState.booleanValue(booleanValue),
                        button -> submitSet(booleanValue,
                                Boolean.toString(!ServerConfigState.booleanValue(booleanValue))));
                Tooltip tooltip = controlTooltip(booleanValue);
                if (tooltip != null) toggle.setTooltip(tooltip);
                addRenderableWidget(toggle);
                addRenderableWidget(resetButton(definition, valueX + 196, y));
            } else if (definition instanceof IntValue intValue) {
                if (intValue.maxValue() - intValue.minValue() <= SLIDER_MAX_SPAN) {
                    ConfigSlider slider = new ConfigSlider(valueX, y, CONTROL_RESERVED - 24, 20, intValue,
                            intValue.minValue(), intValue.maxValue());
                    Tooltip tooltip = controlTooltip(intValue);
                    if (tooltip != null) slider.setTooltip(tooltip);
                    addRenderableWidget(slider);
                    sliders.add(slider);
                } else {
                    addIntegerInputRow(valueX, y, intValue);
                }
                addRenderableWidget(resetButton(definition, valueX + 196, y));
            }
        }
        addRenderableWidget(new SegmentedSwitchButton(left, TABS_Y, contentWidth(), 20, tabLabels(),
                () -> category.ordinal(), index -> switchCategory(ServerCategory.values()[index])));
        Component pageText = Component.translatable("screen.autotorch.config_page", page + 1, pages);
        int pageHalfWidth = (font.width(pageText) + 1) / 2;
        addRenderableWidget(new IconButton(width / 2 - pageHalfWidth - 28, height - 32, 20, 20,
                PixelGlyph.ARROW_LEFT, Component.translatable("screen.autotorch.previous_page"),
                button -> changePage(-1))).active = page > 0;
        addRenderableWidget(new IconButton(width / 2 + pageHalfWidth + 8, height - 32, 20, 20,
                PixelGlyph.ARROW_RIGHT, Component.translatable("screen.autotorch.next_page"),
                button -> changePage(1))).active = page + 1 < pages;
        addRenderableWidget(new IconButton(left, 8, 20, 20, PixelGlyph.BACK,
                Component.translatable("screen.autotorch.back"), button -> onClose()));
        addRenderableWidget(new IconButton(left + contentWidth() - 20, 8, 20, 20, PixelGlyph.RESET,
                Component.translatable("screen.autotorch.config_reset_group"),
                button -> submitReset(category.name())));
    }

    /** 切换服务端配置分类，保留当前页码不越界。 */
    private void switchCategory(ServerCategory value) {
        if (category == value) return;
        category = value;
        page = 0;
        status = Component.translatable("screen.autotorch.server_config_hint");
        init();
    }

    @Override
    public void tick() {
        super.tick();
        ServerConfigResultPayload result = pendingResult;
        if (result != null) {
            pendingResult = null;
            status = Component.translatable(result.messageKey(), scopeName(result.detail()));
        }
        // 服务端广播新配置后重建本页，让双端点间距滑动条与当前值一起更新。
        if (ServerConfigState.revision() != seenRevision) {
            init();
        }
    }

    /** 网络线程收到服务端操作结果后转交给当前界面，下一帧显示。 */
    public static void receiveResult(ServerConfigResultPayload payload) {
        ServerConfigScreen screen = currentInstance;
        if (screen != null) screen.pendingResult = payload;
    }

    @Override
    public void removed() {
        super.removed();
        if (currentInstance == this) currentInstance = null;
    }

    private void submitSet(Value definition, String value) {
        status = Component.translatable("screen.autotorch.server_config_sending", definition.key());
        PlatformNetworking.sendToServer(new ServerConfigSetPayload(definition.key(), value));
    }

    /**
     * 火把间距上下限共用一行双端点滑动条：两个滑柄分别对应下限与上限，
     * 松开鼠标后按服务端逐项校验的顺序把两项一起提交。
     */
    private void addSpacingRangeRow(int valueX, int y) {
        DualRangeSlider range = new DualRangeSlider(valueX, y, CONTROL_RESERVED - 24, 20,
                SPACING_TRACK_MIN, SPACING_TRACK_MAX, SPACING_TRACK_MAX - SPACING_TRACK_MIN,
                ServerConfigState.minSpacing(), ServerConfigState.maxSpacing(),
                (lower, upper) -> Component.translatable("screen.autotorch.server_config_spacing_value",
                        lower, upper),
                (lower, upper) -> { },
                this::submitSpacingRange);
        Tooltip tooltip = controlTooltip(ConfigDefinitions.LIMIT_MIN_SPACING, ConfigDefinitions.LIMIT_MAX_SPACING);
        if (tooltip != null) range.setTooltip(tooltip);
        addRenderableWidget(range);
        IconButton reset = new IconButton(valueX + 196, y, 20, 20, PixelGlyph.RESET,
                Component.translatable("screen.autotorch.config_reset"),
                button -> submitResetBoth(ConfigDefinitions.LIMIT_MIN_SPACING,
                        ConfigDefinitions.LIMIT_MAX_SPACING));
        reset.setTooltip(Tooltip.create(Component.translatable(
                "screen.autotorch.server_config_spacing_reset_tooltip",
                Component.literal(ConfigDefinitions.LIMIT_MIN_SPACING.defaultValueText()),
                Component.literal(ConfigDefinitions.LIMIT_MAX_SPACING.defaultValueText()))));
        addRenderableWidget(reset);
        rows.add(new Row(y, PixelGlyph.DUAL_SLIDER,
                Component.translatable("screen.autotorch.server_config_spacing_range").getString(),
                ConfigDefinitions.LIMIT_MIN_SPACING.key() + " / "
                        + ConfigDefinitions.LIMIT_MAX_SPACING.key()));
    }

    /** 双端点滑动条松开后同时提交上下限，避免只改一项时另一项被服务端拒绝。 */
    private void submitSpacingRange(int lower, int upper) {
        status = Component.translatable("screen.autotorch.server_config_sending",
                ConfigDefinitions.LIMIT_MIN_SPACING.key() + " / "
                        + ConfigDefinitions.LIMIT_MAX_SPACING.key());
        for (ServerConfigSetPayload payload : spacingSetRequests(lower, upper, ServerConfigState.maxSpacing())) {
            PlatformNetworking.sendToServer(payload);
        }
    }

    /**
     * 生成间距上下限的两个修改请求，顺序满足服务端的逐项交叉校验：
     * 新下限超过当前上限（整体上调）时先提交上限，其余情况先提交下限。
     * 两项同时下调时先写下限的请求也合法，不会互相拒绝。
     */
    static List<ServerConfigSetPayload> spacingSetRequests(int lower, int upper, int currentMaxSpacing) {
        boolean upperFirst = lower > currentMaxSpacing;
        Value first = upperFirst ? ConfigDefinitions.LIMIT_MAX_SPACING : ConfigDefinitions.LIMIT_MIN_SPACING;
        Value second = upperFirst ? ConfigDefinitions.LIMIT_MIN_SPACING : ConfigDefinitions.LIMIT_MAX_SPACING;
        return List.of(
                new ServerConfigSetPayload(first.key(), Integer.toString(upperFirst ? upper : lower)),
                new ServerConfigSetPayload(second.key(), Integer.toString(upperFirst ? lower : upper)));
    }

    /** 大范围整数用输入框：手填准确值，点击保存后按当前服务端配置校验。 */
    private void addIntegerInputRow(int valueX, int y, IntValue definition) {
        EditBox input = new EditBox(font, valueX, y + 1, 168, 18,
                Component.literal(rowLabel(definition)));
        input.setMaxLength(16);
        input.setValue(Integer.toString(ServerConfigState.intValue(definition)));
        Tooltip tooltip = controlTooltip(definition);
        if (tooltip != null) input.setTooltip(tooltip);
        addRenderableWidget(input);
        addRenderableWidget(new IconButton(valueX + 172, y, 20, 20, PixelGlyph.SAVE,
                Component.translatable("screen.autotorch.config_save"),
                button -> saveInteger(definition, input)));
    }

    /** 保存输入框里的整数；范围无效时只提示，不发送请求。 */
    private void saveInteger(IntValue definition, EditBox input) {
        try {
            int parsed = Integer.parseInt(input.getValue());
            if (!definition.accepts(parsed)) {
                throw new NumberFormatException();
            }
            input.setTextColor(0xFFE0E0E0);
            submitSet(definition, Integer.toString(parsed));
        } catch (NumberFormatException exception) {
            input.setTextColor(0xFFFF5555);
            status = Component.translatable("screen.autotorch.server_config_invalid_value", definition.key());
        }
    }

    private void submitReset(String scope) {
        status = Component.translatable("screen.autotorch.server_config_sending_scope", scopeName(scope));
        PlatformNetworking.sendToServer(new ServerConfigResetPayload(scope));
    }

    /** 双端点滑动条对应两项配置，重置时连续请求恢复这两个键。 */
    private void submitResetBoth(Value first, Value second) {
        status = Component.translatable("screen.autotorch.server_config_sending_scope",
                first.key() + " / " + second.key());
        PlatformNetworking.sendToServer(new ServerConfigResetPayload(first.key()));
        PlatformNetworking.sendToServer(new ServerConfigResetPayload(second.key()));
    }

    /** 单项重置按钮：提示默认值，点击请求服务端恢复该项。 */
    private IconButton resetButton(Value definition, int x, int y) {
        IconButton button = new IconButton(x, y, 20, 20, PixelGlyph.RESET,
                Component.translatable("screen.autotorch.config_reset"),
                pressed -> submitReset(definition.key()));
        button.setTooltip(Tooltip.create(Component.translatable("screen.autotorch.config_reset_tooltip",
                Component.literal(definition.defaultValueText()))));
        return button;
    }

    private void changePage(int delta) {
        page += delta;
        status = Component.translatable("screen.autotorch.server_config_hint");
        init();
    }

    /** 与全部设置页一致：滚轮上下滚动切换上一页、下一页。 */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        if (super.mouseScrolled(x, y, scrollY)) return true;
        if (scrollY == 0) return false;
        int pages = pageCount();
        int target = Math.max(0, Math.min(pages - 1, page + (scrollY > 0 ? -1 : 1)));
        if (target == page) return true;
        changePage(target - page);
        return true;
    }

    private int pageCount() {
        return Math.max(1, (rowSpecs.size() + pageSize - 1) / pageSize);
    }

    private int contentWidth() { return Math.min(460, width - 20); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        if (!status.getString().isEmpty()) {
            graphics.drawCenteredString(font, status, width / 2, height - 46, 0xFFB0B0B0);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.autotorch.config_page", page + 1, pageCount()),
                width / 2, height - 25, 0xFFB0B0B0);
        int left = (width - contentWidth()) / 2;
        int labelWidth = contentWidth() - CONTROL_RESERVED - ROW_LABEL_INDENT - 4;
        for (Row row : rows) {
            row.icon().drawAt(graphics, left, row.y() + (20 - ROW_ICON_SIZE) / 2, 0xFFB0B0B0);
            String label = font.plainSubstrByWidth(row.label(), labelWidth);
            graphics.drawString(font, label, left + ROW_LABEL_INDENT, row.y() + 6, 0xFFE0E0E0);
            // 行标签已经显示了说明，悬停时提示配置键，方便对照服务端 TOML。
            if (mouseY >= row.y() && mouseY < row.y() + 28
                    && mouseX >= left && mouseX < left + ROW_LABEL_INDENT + font.width(label)) {
                graphics.renderTooltip(font, Component.literal(row.key()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 分类标签文字。 */
    private static List<Component> tabLabels() {
        List<Component> labels = new ArrayList<>();
        for (ServerCategory value : ServerCategory.values()) {
            labels.add(Component.translatable(value.titleKey()));
        }
        return labels;
    }

    /** 行标签取语言文件里的首行，避免长说明挤占右侧控件区。 */
    private static String rowLabel(Value definition) {
        String text = Component.translatable(definition.translationKey()).getString();
        int lineEnd = text.indexOf('\n');
        return lineEnd < 0 ? text : text.substring(0, lineEnd);
    }

    /** 说明的首行已经作为左侧行标签，提示框里只保留后面的细节。 */
    private static String detailComment(Value definition) {
        String text = Component.translatable(definition.translationKey()).getString();
        int lineEnd = text.indexOf('\n');
        return lineEnd < 0 ? "" : text.substring(lineEnd + 1).stripLeading();
    }

    /**
     * 控件提示：说明细节与取值范围。一行控件可能对应多项配置（例如间距上下限合并的双端点滑动条），
     * 按传入顺序拼接各项细节，取值范围完全相同时只提示一次。
     */
    private static Tooltip controlTooltip(Value... definitions) {
        List<String> lines = new ArrayList<>();
        for (Value definition : definitions) {
            String detail = detailComment(definition);
            if (!detail.isEmpty()) lines.add(detail);
        }
        for (Value definition : definitions) {
            if (!(definition instanceof IntValue intValue)) continue;
            String range = Component.translatable("screen.autotorch.config_range_step",
                    intValue.minValue(), intValue.maxValue(), intValue.step()).getString();
            if (!lines.contains(range)) lines.add(range);
        }
        if (lines.isEmpty()) return null;
        return Tooltip.create(Component.literal(String.join("\n", lines)));
    }

    /** 重置范围显示名：空范围是全部，分组还原为分组标题，其余直接显示配置键。 */
    private static Component scopeName(String scope) {
        if (scope == null || scope.isEmpty()) {
            return Component.translatable("screen.autotorch.server_config_scope_all");
        }
        ServerCategory category = ServerCategory.byName(scope);
        if (category != null) return Component.translatable(category.titleKey());
        return Component.literal(scope);
    }

    /**
     * 把服务端配置整理成界面行：火把间距上下限共用一个双端点滑动条，其余配置各占一行。
     * 分页按整理后的行数计算，避免合并后最后一页出现空行。
     */
    static List<RowSpec> buildRowSpecs(List<Value> definitions) {
        List<RowSpec> specs = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            Value definition = definitions.get(index);
            if (definition == ConfigDefinitions.LIMIT_MIN_SPACING && index + 1 < definitions.size()
                    && definitions.get(index + 1) == ConfigDefinitions.LIMIT_MAX_SPACING) {
                specs.add(new RowSpec(RowKind.SPACING_RANGE, definition));
                index++;
            } else {
                specs.add(new RowSpec(RowKind.NORMAL, definition));
            }
        }
        return specs;
    }

    /** 界面行的类型：普通行对应一项配置，间距范围行把上下限合并成一个双端点滑动条。 */
    enum RowKind {
        NORMAL,
        SPACING_RANGE
    }

    /** 界面行规格：分页与重建以行为单位，而不是配置项数量。 */
    record RowSpec(RowKind kind, Value definition) { }

    /** 界面行：每个控件占一行，控件类型由配置值类型或合并方式决定。 */
    private record Row(int y, PixelGlyph icon, String label, String key) { }

    /** 整数设置的滑动条：拖动时只显示数值，松开鼠标或按键调整后才提交给服务端。 */
    private static final class ConfigSlider extends AbstractSliderButton {
        private final IntValue definition;
        private final int minimum;
        private final int maximum;

        ConfigSlider(int x, int y, int width, int height, IntValue definition, int minimum, int maximum) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.definition = definition;
            this.minimum = Math.min(minimum, maximum);
            this.maximum = Math.max(minimum, maximum);
            refresh();
        }

        /** 服务端同步或页面重建后，按当前生效值重新摆放滑柄。 */
        void refresh() {
            value = fraction(ServerConfigState.intValue(definition));
            updateMessage();
        }

        private double fraction(int current) {
            if (maximum <= minimum) return 0.0;
            int bounded = Math.max(minimum, Math.min(maximum, current));
            return (double) (bounded - minimum) / (maximum - minimum);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(currentValue())));
        }

        @Override
        protected void applyValue() {
            // 拖动过程中只刷新显示，避免每一帧都发送修改请求。
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            persist();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled) persist();
            return handled;
        }

        /** 把滑柄位置换算成对齐步进的值，并交给界面提交给服务端。 */
        private void persist() {
            ServerConfigScreen screen = currentInstance;
            if (screen != null) screen.submitSet(definition, Integer.toString(currentValue()));
        }

        private int currentValue() {
            int raw = maximum <= minimum ? minimum
                    : minimum + (int) Math.round(value * (maximum - minimum));
            // 按配置自身的最小值对齐步进，确保服务端不会因为非步进值拒绝。
            int aligned = definition.minValue()
                    + Math.round((raw - definition.minValue()) / (float) definition.step()) * definition.step();
            return Math.max(minimum, Math.min(maximum, aligned));
        }
    }
}
