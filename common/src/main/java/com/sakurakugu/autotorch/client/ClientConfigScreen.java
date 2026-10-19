package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import com.sakurakugu.autotorch.client.ui.SegmentedSwitchButton;
import com.sakurakugu.autotorch.client.ui.ToggleSwitchButton;
import com.sakurakugu.autotorch.client.ui.ValuePickerButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 按功能分页编辑客户端配置；值格式与客户端命令保持一致。 */
public final class ClientConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_ICON_SIZE = 16;
    private static final int ROW_ICON_GAP = 4;
    /** 行首图标的占位宽度：图标本身加右侧间距。 */
    private static final int ROW_LABEL_INDENT = ROW_ICON_SIZE + ROW_ICON_GAP;
    /** 每行右侧控件区总宽度：滑动条或输入框、保存、重置依次排列。 */
    private static final int CONTROL_RESERVED = 216;
    /** 光照显示的向下、向上范围共用主界面的双端点滑动条：向下为负、向上为正。 */
    private static final int VERTICAL_RANGE_MIN = -64;
    private static final int VERTICAL_RANGE_MAX = 64;
    private static final int VERTICAL_RANGE_SPAN = 64;

    private int pageSize = 7;
    private final Screen parent;
    private final ConfigDefinitions.ClientCategory category;
    private final List<RowSpec> rowSpecs;
    private int page;
    private final List<Row> rows = new ArrayList<>();
    /** 子窗口返回和窗口缩放时保留尚未保存的输入。 */
    private final Map<ConfigDefinitions.Value, String> drafts = new HashMap<>();
    private Component status = new net.minecraft.network.chat.TextComponent("");

    public ClientConfigScreen(Screen parent, ConfigDefinitions.ClientCategory category) {
        super(new net.minecraft.network.chat.TranslatableComponent(category.titleKey()));
        this.parent = parent;
        this.category = category;
        this.rowSpecs = buildRowSpecs(category.definitions());
    }

    @Override
    protected void init() {
        // 1.16.5 的绘制列表独立于输入列表，重建页面时必须同时清空，避免旧控件叠加。
        buttons.clear();
        children().clear();
        rows.clear();
        pageSize = Math.max(1, (height - 102) / ROW_HEIGHT);
        page = Math.min(page, (rowSpecs.size() - 1) / pageSize);
        int left = (width - contentWidth()) / 2;
        // 每行右侧依次放置输入/开关/滑动条和单项重置按钮；数值设置不保留保存按钮，松手即保存。
        int valueX = left + contentWidth() - CONTROL_RESERVED;
        int first = page * pageSize;
        int last = Math.min(rowSpecs.size(), first + pageSize);
        for (int index = first; index < last; index++) {
            RowSpec spec = rowSpecs.get(index);
            int y = 38 + (index - first) * ROW_HEIGHT;
            if (spec.kind() == RowKind.DUAL) {
                addVerticalRangeRow(valueX, y);
                continue;
            }
            if (spec.kind() == RowKind.SELECTION_DISPLAY) {
                addSelectionDisplayRow(valueX, y);
                continue;
            }
            if (spec.kind() == RowKind.LIGHT_MODE) {
                addLightOverlayModeRow(valueX, y);
                continue;
            }
            ConfigDefinitions.Value definition = spec.definition();
            Component tooltip = controlTooltip(definition);
            PixelGlyph icon = rowIcon(definition);
            rows.add(new Row(y, icon, rowLabel(definition), definition.key(), definition));
            if (definition instanceof ConfigDefinitions.BooleanValue) {
                ToggleSwitchButton toggle = new ToggleSwitchButton(valueX + 160, y, 32, 20,
                        new net.minecraft.network.chat.TextComponent(rowLabel(definition)),
                        () -> (Boolean) ClientConfig.get(definition), button -> {
                    ClientConfig.set(definition, Boolean.toString(!(Boolean) ClientConfig.get(definition)));
                    status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_saved", definition.key());
                });
                if (tooltip != null) LegacyTooltips.set(toggle, tooltip);
                addButton(toggle);
                addButton(resetButton(definition, valueX + 196, y));
                continue;
            }
            if (icon == PixelGlyph.SLIDER) {
                // 滑动条占满输入框加保存按钮的宽度，取消保存按钮后松手即保存。
                ValueSlider slider = new ValueSlider(valueX, y, CONTROL_RESERVED - 24, 20, definition,
                        this::showSaved);
                if (tooltip != null) LegacyTooltips.set(slider, tooltip);
                addButton(slider);
                addButton(resetButton(definition, valueX + 196, y, slider::refresh));
                continue;
            }
            boolean itemPicker = definition == ConfigDefinitions.NEARBY_AUTO_TORCH_ITEM
                    || definition == ConfigDefinitions.SELECTION_TOOL_ITEM
                    || definition == ConfigDefinitions.TASK_DEFAULT_PLACEMENT_ITEM;
            boolean colorPicker = definition instanceof ConfigDefinitions.StringValue && definition.key().endsWith("Color");
            EditBox value = new EditBox(font, valueX, y + 2, itemPicker || colorPicker ? 144 : 168, 16,
                    LegacyText.string(new net.minecraft.network.chat.TextComponent(rowLabel(definition))));
            value.setMaxLength(128);
            value.setValue(drafts.getOrDefault(definition, String.valueOf(ClientConfig.get(definition))));
            value.setResponder(text -> drafts.put(definition, text));
            if (tooltip != null) LegacyTooltips.set(value, tooltip);
            addButton(value);
            if (itemPicker || colorPicker) {
                addButton(new ValuePickerButton(valueX + 148, y, colorPicker, value::getValue, button -> {
                    Consumer<String> selected = text -> {
                        drafts.put(definition, text);
                        minecraft.setScreen(this);
                    };
                    if (colorPicker) {
                        minecraft.setScreen(new ColorPickerScreen(this, value.getValue(),
                                String.valueOf(ClientConfig.get(definition)), selected));
                    } else {
                        minecraft.setScreen(new ItemSelectionScreen(this, value.getValue(),
                                definition != ConfigDefinitions.SELECTION_TOOL_ITEM, selected));
                    }
                }));
            }
            addButton(new IconButton(valueX + 172, y, 20, 20, PixelGlyph.SAVE,
                    new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_save"), button -> {
                try {
                    ClientConfig.set(definition, value.getValue());
                    value.setValue(String.valueOf(ClientConfig.get(definition)));
                    status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_saved", definition.key());
                    value.setTextColor(0xFFE0E0E0);
                } catch (IllegalArgumentException exception) {
                    status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_invalid");
                    value.setTextColor(0xFFFF5555);
                }
            }));
            addButton(resetButton(definition, valueX + 196, y, () -> {
                value.setValue(String.valueOf(ClientConfig.get(definition)));
                value.setTextColor(0xFFE0E0E0);
            }));
        }
        int pages = (rowSpecs.size() + pageSize - 1) / pageSize;
        int pageHalfWidth = (font.width(LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_page", page + 1, pages))) + 1) / 2;
        addButton(new IconButton(width / 2 - pageHalfWidth - 28, height - 32, 20, 20, PixelGlyph.ARROW_LEFT,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.previous_page"), button -> changePage(-1))).active = page > 0;
        addButton(new IconButton(width / 2 + pageHalfWidth + 8, height - 32, 20, 20, PixelGlyph.ARROW_RIGHT,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.next_page"), button -> changePage(1))).active = page + 1 < pages;
        // 返回贴近内容区最左侧；重置与下方每行的重置按钮同列。
        addButton(new IconButton(left, 8, 20, 20, PixelGlyph.BACK,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.back"), button -> onClose()));
        addButton(new IconButton(left + contentWidth() - 20, 8, 20, 20, PixelGlyph.RESET,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset_group"), button -> {
            ClientConfig.resetDefaults(category);
            drafts.clear();
            status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_group_reset");
            page = 0;
            init();
        }));
    }

    /**
     * 把配置列表整理成界面行；光照显示的向下、向上范围在主界面共用双端点滑动条，
     * 这里也合并为一行，其余配置各占一行。本版本未实现溺尸遮挡可见性检测，
     * 因此隐藏对应的无效设置项（配置定义保留，命令仍可读写）。
     */
    private static List<RowSpec> buildRowSpecs(List<ConfigDefinitions.Value> definitions) {
        List<ConfigDefinitions.Value> visible = definitions.stream()
                .filter(definition -> definition != ConfigDefinitions.LIGHT_OVERLAY_DROWNED_BUDGET)
                .collect(java.util.stream.Collectors.toList());
        List<RowSpec> specs = new ArrayList<>();
        for (int index = 0; index < visible.size(); index++) {
            ConfigDefinitions.Value definition = visible.get(index);
            if (definition == ConfigDefinitions.SELECTION_OVERLAY_ENABLED && index + 1 < visible.size()
                    && visible.get(index + 1) == ConfigDefinitions.SELECTION_OVERLAY_LINES_ONLY) {
                // 主界面的选区显示是面/线/关三态，这里合并为一行分段开关。
                specs.add(new RowSpec(RowKind.SELECTION_DISPLAY, definition, visible.get(index + 1)));
                index++;
            } else if (definition == ConfigDefinitions.LIGHT_OVERLAY_MODE) {
                // 光照显示样式是三态，用分段开关编辑。
                specs.add(new RowSpec(RowKind.LIGHT_MODE, definition, null));
            } else if (definition == ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE && index + 1 < visible.size()
                    && visible.get(index + 1) == ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE) {
                specs.add(new RowSpec(RowKind.DUAL, definition, visible.get(index + 1)));
                index++;
            } else {
                specs.add(new RowSpec(RowKind.NORMAL, definition, null));
            }
        }
        return specs;
    }

    /** 数值设置用滑动条图标，文本设置用输入框图标，开关用开关图标。 */
    private static PixelGlyph rowIcon(ConfigDefinitions.Value definition) {
        if (definition instanceof ConfigDefinitions.BooleanValue) return PixelGlyph.SWITCH;
        if (definition instanceof ConfigDefinitions.IntValue || definition instanceof ConfigDefinitions.DoubleValue) {
            return PixelGlyph.SLIDER;
        }
        return PixelGlyph.TEXT_INPUT;
    }

    /** 行标签取语言文件里的首行，避免长说明挤占右侧控件区。 */
    private static String rowLabel(ConfigDefinitions.Value definition) {
        return firstLine(translatedComment(definition));
    }

    /** 按当前语言读取配置说明；首行是标签，其余行是悬停提示。 */
    private static String translatedComment(ConfigDefinitions.Value definition) {
        return new net.minecraft.network.chat.TranslatableComponent(definition.translationKey()).getString();
    }

    private static String firstLine(String text) {
        int lineEnd = text.indexOf('\n');
        return lineEnd < 0 ? text : text.substring(0, lineEnd);
    }

    /**
     * 受服务端限制的客户端配置在“全部设置”里显示本次任务的实际生效值，
     * 避免界面上是客户端默认值、主界面与提交时却是服务端裁剪后的值。
     */
    private Component serverEffectHint(ConfigDefinitions.Value definition) {
        if (definition == ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES) {
            int effective = ClientStatusJson.effectiveDefaultMaxTorches();
            if (effective == 0) {
                return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_effective_unlimited",
                        ServerConfigState.maxTorchesPerTask());
            }
            if (ClientConfig.defaultMaxTorches() == 0) {
                return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_effective_forced", effective);
            }
            return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_effective_limit",
                    effective, ServerConfigState.maxTorchesPerTask());
        }
        if (definition == ConfigDefinitions.TASK_DEFAULT_MIN_SPACING) {
            return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_effective_spacing",
                    ClientStatusJson.effectiveDefaultMinSpacing(),
                    ServerConfigState.minSpacing(), ServerConfigState.maxSpacing());
        }
        if (definition == ConfigDefinitions.TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES) {
            if (minecraft != null && minecraft.hasSingleplayerServer()) {
                return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_survival_client");
            }
            return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_server_survival_server",
                    new net.minecraft.network.chat.TranslatableComponent(ServerConfigState.survivalConsumesTorches()
                            ? "command.autotorch.on" : "command.autotorch.off"));
        }
        return null;
    }

    /** 向下、向上范围共用的双端点滑动条，拖动结束后把两个配置一起写回。 */
    private void addVerticalRangeRow(int valueX, int y) {
        DualRangeSlider range = new DualRangeSlider(valueX, y, CONTROL_RESERVED - 24, 20,
                VERTICAL_RANGE_MIN, VERTICAL_RANGE_MAX, VERTICAL_RANGE_SPAN,
                -ClientConfig.intValue(ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE),
                ClientConfig.intValue(ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE),
                (lower, upper) -> new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_height_value", lower, upper),
                (lower, upper) -> { },
                this::saveVerticalRange);
        Component tooltip = controlTooltip(ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE,
                ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE);
        if (tooltip != null) LegacyTooltips.set(range, tooltip);
        addButton(range);
        IconButton reset = new IconButton(valueX + 196, y, 20, 20, PixelGlyph.RESET,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset"), button -> resetVerticalRange(range));
        LegacyTooltips.set(reset, resetTooltip(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_dual_default_value",
                ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE.defaultValueText(),
                ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE.defaultValueText())));
        addButton(reset);
        rows.add(new Row(y, PixelGlyph.DUAL_SLIDER,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_vertical_range").getString(),
                ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE.key() + " / "
                        + ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE.key(), null));
    }

    /** 选区显示三态：面/线/关，合并 enabled 与 linesOnly 两个配置。 */
    private void addSelectionDisplayRow(int valueX, int y) {
        SegmentedSwitchButton switchButton = new SegmentedSwitchButton(valueX, y, CONTROL_RESERVED - 24, 20,
                java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_faces"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_lines"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_off")),
                ClientConfigScreen::selectionDisplayIndex, ClientConfigScreen::selectSelectionDisplay);
        Component tooltip = controlTooltip(ConfigDefinitions.SELECTION_OVERLAY_ENABLED,
                ConfigDefinitions.SELECTION_OVERLAY_LINES_ONLY);
        if (tooltip != null) LegacyTooltips.set(switchButton, tooltip);
        addButton(switchButton);
        addButton(new IconButton(valueX + CONTROL_RESERVED - 20, y, 20, 20, PixelGlyph.RESET,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset"), button -> {
            ClientConfig.resetDefaults(ConfigDefinitions.SELECTION_OVERLAY_ENABLED);
            ClientConfig.resetDefaults(ConfigDefinitions.SELECTION_OVERLAY_LINES_ONLY);
            SelectionState.reloadConfig();
            status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_value_reset",
                    ConfigDefinitions.SELECTION_OVERLAY_ENABLED.key() + " / "
                            + ConfigDefinitions.SELECTION_OVERLAY_LINES_ONLY.key());
        }));
        rows.add(new Row(y, PixelGlyph.PANEL,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_selection_display").getString(),
                ConfigDefinitions.SELECTION_OVERLAY_ENABLED.key() + " / "
                        + ConfigDefinitions.SELECTION_OVERLAY_LINES_ONLY.key(), null));
    }

    /** 光照显示样式三态：X 标记/数字/方框数字。 */
    private void addLightOverlayModeRow(int valueX, int y) {
        SegmentedSwitchButton switchButton = new SegmentedSwitchButton(valueX, y, CONTROL_RESERVED - 24, 20,
                java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_crosses"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_numbers"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_boxed_numbers")),
                () -> LightOverlayState.displayMode().ordinal(),
                index -> LightOverlayState.setDisplayMode(LightOverlayState.DisplayMode.values()[index]));
        Component tooltip = controlTooltip(ConfigDefinitions.LIGHT_OVERLAY_MODE);
        if (tooltip != null) LegacyTooltips.set(switchButton, tooltip);
        addButton(switchButton);
        addButton(new IconButton(valueX + CONTROL_RESERVED - 20, y, 20, 20, PixelGlyph.RESET,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset"), button -> {
            ClientConfig.resetDefaults(ConfigDefinitions.LIGHT_OVERLAY_MODE);
            LightOverlayState.reloadConfig();
            status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_value_reset",
                    ConfigDefinitions.LIGHT_OVERLAY_MODE.key());
        }));
        rows.add(new Row(y, PixelGlyph.PANEL, rowLabel(ConfigDefinitions.LIGHT_OVERLAY_MODE),
                ConfigDefinitions.LIGHT_OVERLAY_MODE.key(), null));
    }

    /** 档位顺序与主界面一致：0=面，1=线，2=关。 */
    private static int selectionDisplayIndex() {
        if (!SelectionState.isOverlayEnabled()) return 2;
        return SelectionState.displayMode() == SelectionState.DisplayMode.FACES ? 0 : 1;
    }

    private static void selectSelectionDisplay(int index) {
        if (index == 2) {
            if (SelectionState.isOverlayEnabled()) SelectionState.toggleOverlay();
            return;
        }
        SelectionState.setDisplayMode(index == 0
                ? SelectionState.DisplayMode.FACES : SelectionState.DisplayMode.LINES);
        if (!SelectionState.isOverlayEnabled()) SelectionState.toggleOverlay();
    }

    /** 双端点滑动条松开鼠标后写回向下、向上两个配置。 */
    private void saveVerticalRange(int lower, int upper) {
        try {
            ClientConfig.set(ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE, Integer.toString(-lower));
            ClientConfig.set(ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE, Integer.toString(upper));
            status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_saved",
                    ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE.key() + " / "
                            + ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE.key());
        } catch (IllegalArgumentException exception) {
            // 写回失败时按已保存值重建本页，避免滑块与配置不一致。
            init();
        }
    }

    private void resetVerticalRange(DualRangeSlider range) {
        ClientConfig.resetDefaults(ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE);
        ClientConfig.resetDefaults(ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE);
        range.setValues(-ClientConfig.intValue(ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE),
                ClientConfig.intValue(ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE));
        status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_value_reset",
                ConfigDefinitions.LIGHT_OVERLAY_DOWN_RANGE.key() + " / "
                        + ConfigDefinitions.LIGHT_OVERLAY_UP_RANGE.key());
    }

    private void showSaved(ConfigDefinitions.Value definition) {
        status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_saved", definition.key());
    }

    private IconButton resetButton(ConfigDefinitions.Value definition, int x, int y) {
        return resetButton(definition, x, y, null);
    }

    private IconButton resetButton(ConfigDefinitions.Value definition, int x, int y, Runnable refresh) {
        IconButton button = new IconButton(x, y, 20, 20, PixelGlyph.RESET,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset"), pressed -> {
            ClientConfig.resetDefaults(definition);
            if (refresh != null) refresh.run();
            status = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_value_reset", definition.key());
        });
        LegacyTooltips.set(button, resetTooltip(new net.minecraft.network.chat.TextComponent(definition.defaultValueText())));
        return button;
    }

    /**
     * 控件提示：只保留行标签没显示的说明细节与取值范围。行标签已经显示了说明的首行，
     * 这里不再重复；配置键放到行标签的提示里。没有可显示的内容时返回 null。
     */
    private static Component controlTooltip(ConfigDefinitions.Value... definitions) {
        List<String> lines = new ArrayList<>();
        for (ConfigDefinitions.Value definition : definitions) {
            String detail = detailComment(definition);
            if (!detail.isEmpty()) lines.add(detail);
        }
        for (ConfigDefinitions.Value definition : definitions) {
            lines.addAll(rangeLines(definition));
        }
        if (lines.isEmpty()) return null;
        return LegacyTooltips.wrap(new net.minecraft.network.chat.TextComponent(String.join("\n", lines)));
    }

    /** 说明的首行已经作为左侧行标签，提示框里只保留后面的细节。 */
    private static String detailComment(ConfigDefinitions.Value definition) {
        String text = translatedComment(definition);
        int lineEnd = text.indexOf('\n');
        return lineEnd < 0 ? "" : text.substring(lineEnd + 1).replaceFirst("^\\s+", "");
    }

    /** 取值范围提示跟随当前语言；配置文件注释仍用中文说明。 */
    private static List<String> rangeLines(ConfigDefinitions.Value definition) {
        if (definition instanceof ConfigDefinitions.IntValue) {
            return java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_range_step",
                    ((ConfigDefinitions.IntValue) definition).minValue(), ((ConfigDefinitions.IntValue) definition).maxValue(), ((ConfigDefinitions.IntValue) definition).step()).getString());
        }
        if (definition instanceof ConfigDefinitions.DoubleValue) {
            return java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_range",
                    ((ConfigDefinitions.DoubleValue) definition).minValue(), ((ConfigDefinitions.DoubleValue) definition).maxValue()).getString());
        }
        return java.util.Arrays.asList();
    }

    /** 重置按钮的提示：第一行说明会重置此项，第二行给出恢复到的默认值。 */
    private static Component resetTooltip(Component defaultText) {
        return LegacyTooltips.wrap(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_reset_tooltip", defaultText));
    }

    private void changePage(int delta) {
        page += delta;
        status = new net.minecraft.network.chat.TextComponent("");
        init();
    }

    /** 与物品选择页面一致：滚轮上下滚动切换上一页、下一页。 */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        if (super.mouseScrolled(x, y, scrollY)) return true;
        if (scrollY == 0) return false;
        int pages = (rowSpecs.size() + pageSize - 1) / pageSize;
        int target = Math.max(0, Math.min(pages - 1, page + (scrollY > 0 ? -1 : 1)));
        if (target == page) return true;
        changePage(target - page);
        return true;
    }

    private int contentWidth() { return Math.min(460, width - 20); }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        renderBackground();
        super.render(mouseX, mouseY, partialTick);
        drawCenteredString(font, LegacyText.string(title), width / 2, 14, 0xFFFFFFFF);
        if (!status.getString().isEmpty()) {
            drawCenteredString(font, LegacyText.string(status), width / 2, height - 46, 0xFFB0B0B0);
        }
        drawCenteredString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.config_page", page + 1,
                (rowSpecs.size() + pageSize - 1) / pageSize)), width / 2, height - 25, 0xFFB0B0B0);
        int left = (width - contentWidth()) / 2;
        // 标签右移到图标之后，并留出与右侧控件之间的空隙。
        int labelWidth = contentWidth() - CONTROL_RESERVED - ROW_LABEL_INDENT - 4;
        for (Row row : rows) {
            // 图标与 20 像素高的控件垂直居中对齐。
            row.icon().drawAt(left, row.y() + (20 - ROW_ICON_SIZE) / 2, 0xFFB0B0B0);
            String label = font.substrByWidth(row.label(), labelWidth);
            drawString(font, LegacyText.string(label), left + ROW_LABEL_INDENT, row.y() + 6, 0xFFE0E0E0);
            // 受服务端限制的配置在标签下方显示实际生效值，说明为什么配置值和主界面可能不同。
            Component hint = row.definition() == null ? null : serverEffectHint(row.definition());
            if (hint != null) {
                String hintText = font.substrByWidth(hint.getString(), labelWidth);
                drawString(font, LegacyText.string(hintText), left + ROW_LABEL_INDENT, row.y() + 17, 0xFF909090);
            }
            // 行标签已经显示了说明，悬停时改为提示配置键与实际生效值，方便对照配置文件。
            if (mouseY >= row.y() && mouseY < row.y() + 28
                    && mouseX >= left && mouseX < left + ROW_LABEL_INDENT + font.width(LegacyText.string(label))) {
                Component tooltip = hint == null ? new net.minecraft.network.chat.TextComponent(row.key())
                        : new net.minecraft.network.chat.TextComponent(row.key() + "\n").append(hint);
                renderTooltip(TooltipRenderer.split(font, tooltip, width, mouseX), mouseX, mouseY);
            }
        }
        LegacyTooltips.render(this, font, mouseX, mouseY);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    /** 界面行：普通行对应一个配置，其余类型把相关配置合并成一行控件。 */
    private static final class RowSpec {
        private final RowKind kind;
        private final ConfigDefinitions.Value definition;
        private final ConfigDefinitions.Value second;
        public RowSpec(RowKind kind, ConfigDefinitions.Value definition, ConfigDefinitions.Value second) {
            this.kind = kind;
            this.definition = definition;
            this.second = second;
        }
        public RowKind kind() { return kind; }
        public ConfigDefinitions.Value definition() { return definition; }
        public ConfigDefinitions.Value second() { return second; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RowSpec)) return false;
            RowSpec that = (RowSpec) other;
            return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(definition, that.definition) && java.util.Objects.equals(second, that.second);
        }
        @Override public int hashCode() { return java.util.Objects.hash(kind, definition, second); }
 }

    private enum RowKind {
        NORMAL,
        DUAL,
        SELECTION_DISPLAY,
        LIGHT_MODE
    }

    private static final class Row {
        private final int y;
        private final PixelGlyph icon;
        private final String label;
        private final String key;
        private final ConfigDefinitions.Value definition;
        public Row(int y, PixelGlyph icon, String label, String key, ConfigDefinitions.Value definition) {
            this.y = y;
            this.icon = icon;
            this.label = label;
            this.key = key;
            this.definition = definition;
        }
        public int y() { return y; }
        public PixelGlyph icon() { return icon; }
        public String label() { return label; }
        public String key() { return key; }
        public ConfigDefinitions.Value definition() { return definition; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Row)) return false;
            Row that = (Row) other;
            return java.util.Objects.equals(y, that.y) && java.util.Objects.equals(icon, that.icon) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(key, that.key) && java.util.Objects.equals(definition, that.definition);
        }
        @Override public int hashCode() { return java.util.Objects.hash(y, icon, label, key, definition); }
 }

    /** 数值设置的滑动条：拖动时实时显示数值，松开鼠标或按键调整后才写回配置。 */
    private static final class ValueSlider extends ScrollingSliderButton {
        private final ConfigDefinitions.Value definition;
        private final Consumer<ConfigDefinitions.Value> onSaved;

        ValueSlider(int x, int y, int width, int height, ConfigDefinitions.Value definition,
                    Consumer<ConfigDefinitions.Value> onSaved) {
            super(x, y, width, height, 0.0);
            this.definition = definition;
            this.onSaved = onSaved;
            refresh();
        }

        /** 单项重置或外部改动后，按当前配置值重新摆放滑块。 */
        void refresh() {
            Object saved = ClientConfig.get(definition);
            double current = saved instanceof Integer ? ((Integer) saved).doubleValue() : (Double) saved;
            value = ConfigValueFormat.fraction(definition, ConfigValueFormat.snap(definition, current));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(LegacyText.string(new net.minecraft.network.chat.TextComponent(ConfigValueFormat.format(definition, currentValue()))));
        }

        @Override
        protected void applyValue() {
            // 拖动过程中只刷新显示，避免每一帧都写配置文件。
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

        /** 把滑块当前值写回客户端配置，成功后由界面显示保存提示。 */
        private void persist() {
            try {
                ClientConfig.set(definition, ConfigValueFormat.format(definition, currentValue()));
                onSaved.accept(definition);
            } catch (IllegalArgumentException exception) {
                refresh();
            }
        }

        private double currentValue() {
            return ConfigValueFormat.valueAt(definition, value);
        }
    }
}
