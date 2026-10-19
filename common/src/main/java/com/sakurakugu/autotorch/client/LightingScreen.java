package com.sakurakugu.autotorch.client;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.sakurakugu.autotorch.AutoTorchRules;
import com.sakurakugu.autotorch.client.ui.IconButton;
import com.sakurakugu.autotorch.client.ui.LegacyWidgetSprites;
import com.sakurakugu.autotorch.client.ui.PixelGlyph;
import com.sakurakugu.autotorch.client.ui.SegmentedSwitchButton;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.sakurakugu.autotorch.network.PlatformNetworking;

/** 自动照明的参数界面，负责选区管理、客户端校验和任务提交。 */
public final class LightingScreen extends Screen {
    private static final int CONTENT_HEIGHT = 350;
    private static final int VIEWPORT_MARGIN = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_SCROLLBAR_HEIGHT = 20;
    private static final int SCROLL_RATE = 20;
    private final EditBox[] first = new EditBox[3];
    private final EditBox[] second = new EditBox[3];
    private final EditBox[] dimensions = new EditBox[3];
    private Button shapeButton;
    private Button convertShapeButton;
    private Button swapPointsButton;
    private Button sphereDisplayButton;
    private Button allSettingsButton;
    private Button useCurrentFirstButton;
    private Button useCurrentSecondButton;
    private Button setLightingButton;
    private Button exclusionButton;
    private Button includeSkyLightButton;
    private Button lightOverlayButton;
    private Button lightOverlayRenderThroughButton;
    private Button swampSlimeDetectionButton;
    private Button drownedDetectionButton;
    private Button nearbyAutoTorchButton;
    private Button nearbyAutoTorchSkyLightButton;
    private SelectionToolButton selectionToolButton;
    private Button startTaskButton;
    private Button cancelTaskButton;
    private Button resetButton;
    private boolean includeSkyLight;
    private boolean syncingInputs;
    private String selectionToolItemId;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private boolean selectionInRange = true;
    private Component error = new net.minecraft.network.chat.TextComponent("");
    private Component rangeMessage = new net.minecraft.network.chat.TextComponent("");

    public LightingScreen() {
        this(0);
    }

    private LightingScreen(int initialScrollOffset) {
        super(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.title"));
        scrollOffset = initialScrollOffset;
        includeSkyLight = ClientConfig.isDefaultIncludeSkyLight();
    }

    @Override
    protected void init() {
        // 从全部设置返回时重新读取任务参数，避免提交旧值。
        includeSkyLight = ClientConfig.isDefaultIncludeSkyLight();
        BlockPos playerPos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.getCommandSenderBlockPosition();
        int left = panelLeft();

        Component resetMessage = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.reset");
        resetButton = addButton(new ResetButton(resetButtonX(resetMessage), 2,
                font.width(LegacyText.string(resetMessage)), 16, resetMessage, panelLeft(), panelLeft() + 310,
                button -> resetLightingTaskSettings()));

        shapeButton = addButton(ButtonBuilder.builder(shapeMessage(), button -> {
            SelectionState.setShape(SelectionState.shape() == AreaShape.BOX ? AreaShape.SPHERE : AreaShape.BOX);
            shapeButton.setMessage(LegacyText.string(shapeMessage()));
            updateShapeDependentMessages();
            refreshDimensionInputs();
        }).bounds(left, 20, 126, 20).build());
        sphereDisplayButton = addButton(ButtonBuilder.builder(sphereDisplayMessage(), button -> {
            SelectionState.SphereDisplayMode next =
                    SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                            ? SelectionState.SphereDisplayMode.SMOOTH : SelectionState.SphereDisplayMode.BLOCKY;
            SelectionState.setSphereDisplayMode(next);
            sphereDisplayButton.setMessage(LegacyText.string(sphereDisplayMessage()));
        }).bounds(left + 216, 88, 94, 20).build());
        addButton(new SegmentedSwitchButton(left + 130, 20, 156, 20,
                java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_faces"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_lines"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.display_off")),
                LightingScreen::selectionDisplayIndex, LightingScreen::selectSelectionDisplay));
        allSettingsButton = addButton(new IconButton(left + 290, 20, 20, 20, PixelGlyph.SETTING,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.all_settings"), button -> {
                    saveSelection();
                    minecraft.setScreen(new ClientConfigScreen(this, ConfigDefinitions.ClientCategory.LIGHTING_TASK));
                }));

        createCoordinateRow(first, left, 44, SelectionState.first(playerPos));
        useCurrentFirstButton = addButton(ButtonBuilder.builder(firstPointMessage(), button -> {
            setCoordinatePosition(first, currentPosition());
        }).bounds(left + 190, 44, 120, 20).build());

        createCoordinateRow(second, left, 66, SelectionState.second(playerPos));
        useCurrentSecondButton = addButton(ButtonBuilder.builder(secondPointMessage(), button -> {
            setCoordinatePosition(second, currentPosition());
        }).bounds(left + 190, 66, 120, 20).build());

        int[] dimensionOffsets = {18, 120, 222};
        int[] dimensionWidths = {82, 82, 88};
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = sizeBox(left + dimensionOffsets[i], 88, dimensionWidths[i]);
            dimensions[i].setResponder(value -> onDimensionChanged());
        }
        for (EditBox box : first) {
            box.setResponder(value -> onCoordinatesChanged());
        }
        for (EditBox box : second) {
            box.setResponder(value -> onCoordinatesChanged());
        }
        refreshDimensionInputs();

        // 转换和互换按钮按配置显示文字或符号，符号模式下用悬停提示补充说明。
        convertShapeButton = addButton(ButtonBuilder.builder(convertShapeMessage(), button -> convertSelectionShape())
                .bounds(left, 112, 126, 20).build());
        swapPointsButton = addButton(ButtonBuilder.builder(swapPointsMessage(), button -> swapPoints())
                .bounds(left + 130, 112, 82, 20).build());
        updateActionTooltips();
        selectionToolButton = addButton(new SelectionToolButton(left + 216, 112, 94, 20,
                selectionToolMessage(), button -> {
            boolean enabled = !ClientConfig.isSelectionToolEnabled();
            ClientConfig.setSelectionToolEnabled(enabled);
            PlatformNetworking.sendToServer(new SetSelectionToolPayload(enabled,
                    ClientConfig.stringValue(ConfigDefinitions.SELECTION_TOOL_ITEM)));
            selectionToolButton.setMessage(LegacyText.string(selectionToolMessage()));
        }));
        LegacyTooltips.set(selectionToolButton,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.selection_tool.tooltip"));
        selectionToolButton.setToolStack(ClientConfig.selectionToolItem().getDefaultInstance());
        selectionToolItemId = ClientConfig.stringValue(ConfigDefinitions.SELECTION_TOOL_ITEM);

        setLightingButton = addButton(new ColoredButton(left, 136, 141, 20,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.set_lighting"), button -> setLightingZone(),
                0xDD176B35, 0xEE218A47));
        exclusionButton = addButton(new ColoredButton(left + 145, 136, 141, 20,
                exclusionMessage(), button -> addExclusion(), 0xDDA52B2B, 0xEEC83C3C));
        updateZoneButtonAvailability();
        addButton(new IconButton(left + 290, 136, 20, 20, PixelGlyph.LIST,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.manage_exclusions"), button -> {
                    saveSelection();
                    minecraft.setScreen(new ExclusionListScreen());
                }));

        includeSkyLightButton = addButton(ButtonBuilder.builder(includeSkyLightMessage(), button -> {
            includeSkyLight = !includeSkyLight;
            ClientConfig.setDefaultIncludeSkyLight(includeSkyLight);
            includeSkyLightButton.setMessage(LegacyText.string(includeSkyLightMessage()));
        }).bounds(left, 160, 116, 20).build());
        addButton(new MinSpacingSlider(left + 120, 160, 100, 20));
        addButton(new AreaLightThresholdSlider(left + 224, 160, 86, 20));

        startTaskButton = addButton(ButtonBuilder.builder(
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.start"), button -> startTask())
                .bounds(left, 184, 153, 20).build());
        cancelTaskButton = addButton(ButtonBuilder.builder(
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.cancel_task"), button -> {
            PlatformNetworking.sendToServer(new CancelLightingPayload());
            onClose();
        }).bounds(left + 157, 184, 153, 20).build());
        updateTaskButtonAvailability();

        lightOverlayButton = addButton(ButtonBuilder.builder(lightOverlayMessage(), button -> {
            LightOverlayState.toggle();
            lightOverlayButton.setMessage(LegacyText.string(lightOverlayMessage()));
        }).bounds(left, 234, 108, 20).build());
        Component lightOverlayResetMessage = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.reset");
        addButton(new ResetButton(resetButtonX(lightOverlayResetMessage), 218,
                font.width(LegacyText.string(lightOverlayResetMessage)), 16, lightOverlayResetMessage, panelLeft(), panelLeft() + 310,
                button -> resetLightOverlaySettings()));
        addButton(new SegmentedSwitchButton(left + 112, 234, 174, 20,
                java.util.Arrays.asList(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_crosses"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_numbers"),
                        new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_mode_boxed_numbers")),
                LightingScreen::lightOverlayDisplayModeIndex, LightingScreen::selectLightOverlayDisplayMode));
        addButton(new IconButton(left + 290, 234, 20, 20, PixelGlyph.SETTING,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.all_settings"), button -> {
            saveSelection();
            minecraft.setScreen(new ClientConfigScreen(this, ConfigDefinitions.ClientCategory.LIGHT_OVERLAY));
        }));
        lightOverlayRenderThroughButton = addButton(ButtonBuilder.builder(lightOverlayRenderThroughMessage(), button -> {
            ClientConfig.setLightOverlayRenderThrough(!ClientConfig.isLightOverlayRenderThrough());
            lightOverlayRenderThroughButton.setMessage(LegacyText.string(lightOverlayRenderThroughMessage()));
        }).bounds(left + 213, 282, 97, 20).build());
        // 宽度与高度滑块同一行；宽度滑块保持原有 108 像素宽度。
        addButton(new LightRangeSlider(left, 258, 108, 20));
        addButton(new DualRangeSlider(left + 112, 258, 198, 20, -64, 64, 64,
                -LightOverlayState.downRange(), LightOverlayState.upRange(),
                (lower, upper) -> new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_height_value", lower, upper),
                (lower, upper) -> {
                    LightOverlayState.setDownRange(-lower);
                    LightOverlayState.setUpRange(upper);
                }));
        swampSlimeDetectionButton = addButton(ButtonBuilder.builder(swampSlimeDetectionMessage(), button -> {
            LightOverlayState.toggleSwampSlimeDetection();
            swampSlimeDetectionButton.setMessage(LegacyText.string(swampSlimeDetectionMessage()));
        }).bounds(left, 282, 108, 20)
                .tooltip(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.swamp_slime_detection.tooltip"))
                .build());
        drownedDetectionButton = addButton(ButtonBuilder.builder(drownedDetectionMessage(), button -> {
            LightOverlayState.toggleDrownedDetection();
            drownedDetectionButton.setMessage(LegacyText.string(drownedDetectionMessage()));
        }).bounds(left + 112, 282, 97, 20)
                .tooltip(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.drowned_detection.tooltip"))
                .build());

        nearbyAutoTorchButton = addButton(ButtonBuilder.builder(nearbyAutoTorchMessage(), button -> {
            ClientConfig.setNearbyAutoTorchEnabled(!ClientConfig.isNearbyAutoTorchEnabled());
            nearbyAutoTorchButton.setMessage(LegacyText.string(nearbyAutoTorchMessage()));
        }).bounds(left, 326, 90, 20)
                .tooltip(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.nearby_auto_torch.tooltip"))
                .build());
        Component nearbyResetMessage = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.reset");
        addButton(new ResetButton(resetButtonX(nearbyResetMessage), 310,
                font.width(LegacyText.string(nearbyResetMessage)), 16, nearbyResetMessage, panelLeft(), panelLeft() + 310,
                button -> resetNearbyAutoTorchSettings()));
        addButton(new NearbyAutoTorchThresholdSlider(left + 94, 326, 94, 20));
        nearbyAutoTorchSkyLightButton = addButton(ButtonBuilder.builder(nearbyAutoTorchSkyLightMessage(), button -> {
            ClientConfig.setIncludesSkyLight(!ClientConfig.includesSkyLight());
            nearbyAutoTorchSkyLightButton.setMessage(LegacyText.string(nearbyAutoTorchSkyLightMessage()));
        }).bounds(left + 192, 326, 94, 20).build());
        addButton(new IconButton(left + 290, 326, 20, 20, PixelGlyph.SETTING,
                new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.all_settings"), button -> {
            saveSelection();
            minecraft.setScreen(new ClientConfigScreen(this, ConfigDefinitions.ClientCategory.NEARBY_AUTO_TORCH));
        }));

        scrollOffset = Math.min(scrollOffset, maxScrollOffset());
        moveWidgets(-scrollOffset);
    }

    @Override
    public void tick() {
        super.tick();
        updateTaskButtonAvailability();
        refreshSelectionToolIcon();
    }

    /** 在配置界面改过工具物品后，返回本界面时刷新按钮上的物品图标。 */
    private void refreshSelectionToolIcon() {
        String item = ClientConfig.stringValue(ConfigDefinitions.SELECTION_TOOL_ITEM);
        if (selectionToolButton != null && !item.equals(selectionToolItemId)) {
            selectionToolItemId = item;
            selectionToolButton.setToolStack(ClientConfig.selectionToolItem().getDefaultInstance());
        }
    }

    private void updateTaskButtonAvailability() {
        boolean enabled = ServerConfigState.lightingTaskEnabled();
        if (startTaskButton != null) startTaskButton.active = enabled;
        if (cancelTaskButton != null) cancelTaskButton.active = enabled;
    }

    private void resetLightingTaskSettings() {
        ClientConfig.resetLightingTaskDefaults();
        SelectionState.reloadConfig();
        PlatformNetworking.sendToServer(new SetSelectionToolPayload(
                ClientConfig.isSelectionToolEnabled(),
                ClientConfig.stringValue(ConfigDefinitions.SELECTION_TOOL_ITEM)));
        reopenAtCurrentScrollOffset();
    }

    private void resetLightOverlaySettings() {
        ClientConfig.resetLightOverlayDefaults();
        LightOverlayState.reloadConfig();
        reopenAtCurrentScrollOffset();
    }

    private void resetNearbyAutoTorchSettings() {
        ClientConfig.resetNearbyAutoTorchDefaults();
        reopenAtCurrentScrollOffset();
    }

    private void reopenAtCurrentScrollOffset() {
        minecraft.setScreen(new LightingScreen(scrollOffset));
    }

    private void createCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + 18 + i * 57, y, 53, Integer.toString(values[i]));
        }
    }

    /** 输入框上下各内缩 2 像素，与同行的 20 像素高按钮保持视觉居中。 */
    private EditBox integerBox(int x, int y, int boxWidth, String value) {
        EditBox box = new EditBox(font, x, y + 2, boxWidth, 16, LegacyText.string(new net.minecraft.network.chat.TextComponent("")));
        box.setMaxLength(9);
        box.setValue(value);
        return addButton(box);
    }

    private EditBox sizeBox(int x, int y, int boxWidth) {
        EditBox box = new EditBox(font, x, y + 2, boxWidth, 16, LegacyText.string(new net.minecraft.network.chat.TextComponent("")));
        box.setMaxLength(9);
        return addButton(box);
    }

    private void onCoordinatesChanged() {
        if (syncingInputs) {
            return;
        }
        try {
            BlockPos firstPos = readPosition(first);
            BlockPos secondPos = readPosition(second);
            SelectionState.setFirst(firstPos);
            SelectionState.setSecond(secondPos);
            refreshDimensionInputs(firstPos, secondPos);
            error = new net.minecraft.network.chat.TextComponent("");
        } catch (IllegalArgumentException ignored) {
            // 坐标输入尚不完整时，等待用户继续输入。
        }
    }

    private void onDimensionChanged() {
        if (syncingInputs) {
            return;
        }
        try {
            BlockPos anchor = readPosition(first);
            BlockPos currentSecond = readPosition(second);
            BlockPos updatedSecond;
            boolean dimensionClamped = false;
            if (SelectionState.shape() == AreaShape.SPHERE) {
                int radius = Integer.parseInt(dimensions[0].getValue());
                int maxRadius = ServerConfigState.maxSphereRadius();
                if (radius > maxRadius) {
                    radius = maxRadius;
                    syncingInputs = true;
                    try {
                        dimensions[0].setValue(Integer.toString(maxRadius));
                    } finally {
                        syncingInputs = false;
                    }
                    dimensionClamped = true;
                } else if (radius < 0) {
                    throw new IllegalArgumentException("Out of range");
                }
                updatedSecond = offsetChecked(anchor, radius, 0, 0);
            } else {
                int maxAxisLength = ServerConfigState.maxBoxAxisLength();
                int[] sizesByInput = new int[dimensions.length];
                for (int i = 0; i < dimensions.length; i++) {
                    int size = Integer.parseInt(dimensions[i].getValue());
                    if (size > maxAxisLength) {
                        size = maxAxisLength;
                        syncingInputs = true;
                        try {
                            dimensions[i].setValue(Integer.toString(maxAxisLength));
                        } finally {
                            syncingInputs = false;
                        }
                        dimensionClamped = true;
                    } else if (size < 1) {
                        throw new IllegalArgumentException("Out of range");
                    }
                    sizesByInput[i] = size;
                }
                int[] anchorValues = {anchor.getX(), anchor.getY(), anchor.getZ()};
                int[] secondValues = {currentSecond.getX(), currentSecond.getY(), currentSecond.getZ()};
                int[] sizes = {sizesByInput[0], sizesByInput[2], sizesByInput[1]};
                int[] offsets = new int[3];
                for (int i = 0; i < offsets.length; i++) {
                    int direction = Integer.compare(secondValues[i], anchorValues[i]);
                    if (direction == 0) {
                        direction = 1;
                    }
                    offsets[i] = direction * (sizes[i] - 1);
                }
                updatedSecond = offsetChecked(anchor, offsets[0], offsets[1], offsets[2]);
            }
            setCoordinatePosition(second, updatedSecond);
            error = new net.minecraft.network.chat.TextComponent("");
            if (dimensionClamped) {
                rangeMessage = new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                                ? "screen.autotorch.sphere_radius_clamped"
                                : "screen.autotorch.box_axis_clamped",
                        SelectionState.shape() == AreaShape.SPHERE
                                ? ServerConfigState.maxSphereRadius()
                                : ServerConfigState.maxBoxAxisLength());
            }
        } catch (IllegalArgumentException ignored) {
            // 尺寸输入尚不完整或超出范围时，不改变坐标。
        }
    }

    private void refreshDimensionInputs() {
        try {
            refreshDimensionInputs(readPosition(first), readPosition(second));
        } catch (IllegalArgumentException ignored) {
            // 坐标输入尚不完整时保留上一次显示。
        }
    }

    private void refreshDimensionInputs(BlockPos firstPos, BlockPos secondPos) {
        syncingInputs = true;
        try {
            boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
            dimensions[0].x = panelLeft() + 18;
            dimensions[0].setValue(Integer.toString(sphere
                    ? new AreaZone(AreaShape.SPHERE, firstPos, secondPos).radius()
                    : Math.abs(secondPos.getX() - firstPos.getX()) + 1));
            dimensions[1].visible = !sphere;
            dimensions[2].visible = !sphere;
            sphereDisplayButton.visible = sphere;
            if (!sphere) {
                dimensions[1].setValue(Integer.toString(Math.abs(secondPos.getZ() - firstPos.getZ()) + 1));
                dimensions[2].setValue(Integer.toString(Math.abs(secondPos.getY() - firstPos.getY()) + 1));
            }
        } finally {
            syncingInputs = false;
        }
        updateSelectionRangeMessage(firstPos, secondPos);
    }

    private void updateSelectionRangeMessage(BlockPos firstPos, BlockPos secondPos) {
        if (SelectionState.shape() == AreaShape.SPHERE) {
            long maxRadiusSquared = (long) ServerConfigState.maxSphereRadius()
                    * ServerConfigState.maxSphereRadius();
            AreaZone zone = new AreaZone(AreaShape.SPHERE, firstPos, secondPos);
            selectionInRange = zone.radiusSquared() <= maxRadiusSquared;
            rangeMessage = selectionInRange ? new net.minecraft.network.chat.TextComponent("")
                    : new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.sphere_radius_too_large",
                    ServerConfigState.maxSphereRadius());
        } else {
            long sizeX = Math.abs((long) secondPos.getX() - firstPos.getX()) + 1L;
            long sizeY = Math.abs((long) secondPos.getY() - firstPos.getY()) + 1L;
            long sizeZ = Math.abs((long) secondPos.getZ() - firstPos.getZ()) + 1L;
            selectionInRange = sizeX <= ServerConfigState.maxBoxAxisLength()
                    && sizeY <= ServerConfigState.maxBoxAxisLength()
                    && sizeZ <= ServerConfigState.maxBoxAxisLength();
            rangeMessage = selectionInRange ? new net.minecraft.network.chat.TextComponent("")
                    : new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.box_axis_too_large",
                    ServerConfigState.maxBoxAxisLength());
        }
        updateZoneButtonAvailability();
    }

    private void updateZoneButtonAvailability() {
        if (setLightingButton != null) {
            setLightingButton.active = selectionInRange;
        }
        if (exclusionButton != null) {
            exclusionButton.active = selectionInRange;
        }
    }

    private void swapPoints() {
        try {
            BlockPos firstPos = readPosition(first);
            BlockPos secondPos = readPosition(second);
            syncingInputs = true;
            try {
                setPosition(first, secondPos);
                setPosition(second, firstPos);
            } finally {
                syncingInputs = false;
            }
            SelectionState.setFirst(secondPos);
            SelectionState.setSecond(firstPos);
            refreshDimensionInputs(secondPos, firstPos);
        } catch (IllegalArgumentException ignored) {
            error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.invalid_value");
        }
    }

    private void convertSelectionShape() {
        try {
            BlockPos firstPos = readPosition(first);
            BlockPos secondPos = readPosition(second);
            AreaShape convertedShape;
            BlockPos convertedFirst;
            BlockPos convertedSecond;

            if (SelectionState.shape() == AreaShape.BOX) {
                AreaZone box = new AreaZone(AreaShape.BOX, firstPos, secondPos);
                BlockPos min = box.min();
                BlockPos max = box.max();
                long shortestExtent = Math.min(
                        Math.min((long) max.getX() - min.getX(), (long) max.getY() - min.getY()),
                        (long) max.getZ() - min.getZ());
                int radius = (int) (shortestExtent / 2L);
                if (radius > ServerConfigState.maxSphereRadius()) {
                    error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.convert_sphere_too_large");
                    return;
                }
                convertedFirst = midpoint(min, max);
                convertedSecond = offsetChecked(convertedFirst, radius, 0, 0);
                convertedShape = AreaShape.SPHERE;
            } else {
                AreaZone sphere = new AreaZone(AreaShape.SPHERE, firstPos, secondPos);
                validateZone(sphere);
                int radius = sphere.radius();
                if (radius * 2L + 1L > ServerConfigState.maxBoxAxisLength()) {
                    error = new net.minecraft.network.chat.TranslatableComponent(
                            "screen.autotorch.convert_box_too_large",
                            ServerConfigState.maxBoxAxisLength());
                    return;
                }
                convertedFirst = offsetChecked(firstPos, -radius, -radius, -radius);
                convertedSecond = offsetChecked(firstPos, radius, radius, radius);
                convertedShape = AreaShape.BOX;
            }

            syncingInputs = true;
            try {
                setPosition(first, convertedFirst);
                setPosition(second, convertedSecond);
            } finally {
                syncingInputs = false;
            }
            SelectionState.setFirst(convertedFirst);
            SelectionState.setSecond(convertedSecond);
            SelectionState.setShape(convertedShape);
            shapeButton.setMessage(LegacyText.string(shapeMessage()));
            updateShapeDependentMessages();
            refreshDimensionInputs(convertedFirst, convertedSecond);
            error = new net.minecraft.network.chat.TextComponent("");
        } catch (IllegalArgumentException exception) {
            error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.invalid_value");
        }
    }

    private static BlockPos midpoint(BlockPos min, BlockPos max) {
        return new BlockPos(
                min.getX() + (int) (((long) max.getX() - min.getX()) / 2L),
                min.getY() + (int) (((long) max.getY() - min.getY()) / 2L),
                min.getZ() + (int) (((long) max.getZ() - min.getZ()) / 2L)
        );
    }

    private void setCoordinatePosition(EditBox[] boxes, BlockPos pos) {
        syncingInputs = true;
        try {
            setPosition(boxes, pos);
        } finally {
            syncingInputs = false;
        }
        onCoordinatesChanged();
    }

    private static BlockPos offsetChecked(BlockPos anchor, int x, int y, int z) {
        long targetX = (long) anchor.getX() + x;
        long targetY = (long) anchor.getY() + y;
        long targetZ = (long) anchor.getZ() + z;
        if (targetX < Integer.MIN_VALUE || targetX > Integer.MAX_VALUE
                || targetY < Integer.MIN_VALUE || targetY > Integer.MAX_VALUE
                || targetZ < Integer.MIN_VALUE || targetZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Coordinate overflow");
        }
        return new BlockPos((int) targetX, (int) targetY, (int) targetZ);
    }

    private void setLightingZone() {
        try {
            AreaZone zone = readDraftZone();
            validateZone(zone);
            validateLightingZone(zone);
            SelectionState.setLightingZone(zone);
            exclusionButton.setMessage(LegacyText.string(exclusionMessage()));
            error = new net.minecraft.network.chat.TextComponent("");
        } catch (IllegalArgumentException exception) {
            error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.invalid_value");
        }
    }

    private void addExclusion() {
        try {
            AreaZone zone = readDraftZone();
            validateZone(zone);
            if (!SelectionState.addExclusion(zone)) {
                error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.too_many_exclusions");
            } else {
                exclusionButton.setMessage(LegacyText.string(exclusionMessage()));
                error = new net.minecraft.network.chat.TextComponent("");
            }
        } catch (IllegalArgumentException exception) {
            error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.invalid_value");
        }
    }

    private AreaZone readDraftZone() {
        BlockPos firstPos = readPosition(first);
        BlockPos secondPos = readPosition(second);
        SelectionState.setFirst(firstPos);
        SelectionState.setSecond(secondPos);
        return new AreaZone(SelectionState.shape(), firstPos, secondPos);
    }

    private static void validateZone(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            long maxRadiusSquared = (long) ServerConfigState.maxSphereRadius()
                    * ServerConfigState.maxSphereRadius();
            if (zone.radiusSquared() > maxRadiusSquared) {
                throw new IllegalArgumentException("Sphere radius out of range");
            }
            return;
        }
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        if ((long) max.getX() - min.getX() >= ServerConfigState.maxBoxAxisLength()
                || (long) max.getY() - min.getY() >= ServerConfigState.maxBoxAxisLength()
                || (long) max.getZ() - min.getZ() >= ServerConfigState.maxBoxAxisLength()) {
            throw new IllegalArgumentException("Box axis out of range");
        }
    }

    private static void validateLightingZone(AreaZone zone) {
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        long sizeX = (long) max.getX() - min.getX() + 1L;
        long sizeY = (long) max.getY() - min.getY() + 1L;
        long sizeZ = (long) max.getZ() - min.getZ() + 1L;
        int maxAxis = Math.max(ServerConfigState.maxBoxAxisLength(),
                ServerConfigState.maxSphereRadius() * 2 + 1);
        long maxVolume = (long) maxAxis * maxAxis * maxAxis;
        if (sizeX > maxAxis || sizeY > maxAxis || sizeZ > maxAxis
                || sizeX * sizeY * sizeZ > maxVolume) {
            throw new IllegalArgumentException("Lighting area too large");
        }
    }

    private void startTask() {
        try {
            AreaZone selection = SelectionState.lightingZone();
            if (selection == null) {
                error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.no_lighting_zone");
                return;
            }
            if (SelectionState.drafting()
                    || !readPosition(first).equals(SelectionState.first(selection.first()))
                    || !readPosition(second).equals(SelectionState.second(selection.second()))) {
                error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.confirm_draft");
                return;
            }
            // 火把上限和间距只在“全部设置”里修改；这里只计算本次任务实际生效值，
            // 不回写客户端配置，避免把当前服务器的限制永久写进玩家自己的设置。
            int effectiveMax = ClientStatusJson.effectiveDefaultMaxTorches();
            int spacing = ClientStatusJson.effectiveDefaultMinSpacing();
            int lightThreshold = ClientConfig.defaultTaskLightThreshold();
            PlatformNetworking.sendToServer(new StartLightingPayload(
                    selection, effectiveMax, spacing, lightThreshold, initialConsumeTorches(),
                    // 服务端载荷沿用 undergroundOnly 语义：true 表示跳过有天空光的位置。
                    !includeSkyLight,
                    ClientConfig.stringValue(ConfigDefinitions.TASK_DEFAULT_PLACEMENT_ITEM),
                    ClientConfig.intValue(ConfigDefinitions.TASK_PROGRESS_INTERVAL),
                    SelectionState.exclusions()
            ));
            onClose();
        } catch (IllegalArgumentException exception) {
            error = new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.invalid_value");
        }
    }

    private static BlockPos readPosition(EditBox[] boxes) {
        return new BlockPos(
                Integer.parseInt(boxes[0].getValue()),
                Integer.parseInt(boxes[1].getValue()),
                Integer.parseInt(boxes[2].getValue())
        );
    }

    private static int effectiveSecondPassSpacing() {
        return AutoTorchRules.secondPassSpacing(ClientStatusJson.effectiveDefaultMinSpacing(),
                ClientConfig.defaultTaskLightThreshold(), ServerConfigState.minSpacing());
    }

    private static boolean isCreativePlayer() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }

    private static boolean isSingleplayerOwner() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    private static boolean initialConsumeTorches() {
        if (isCreativePlayer()) {
            return ClientConfig.creativeConsumesTorches();
        }
        return isSingleplayerOwner()
                ? ClientConfig.survivalConsumesTorches()
                : ServerConfigState.survivalConsumesTorches();
    }

    private void saveSelection() {
        try {
            BlockPos firstPos = readPosition(first);
            BlockPos secondPos = readPosition(second);
            if (!firstPos.equals(SelectionState.first(firstPos))) {
                SelectionState.setFirst(firstPos);
            }
            if (!secondPos.equals(SelectionState.second(secondPos))) {
                SelectionState.setSecond(secondPos);
            }
        } catch (IllegalArgumentException ignored) {
            // 输入尚不完整时保留上一次有效草稿。
        }
    }

    private BlockPos currentPosition() {
        return minecraft.player == null ? BlockPos.ZERO : minecraft.player.getCommandSenderBlockPosition();
    }

    private static void setPosition(EditBox[] boxes, BlockPos pos) {
        boxes[0].setValue(Integer.toString(pos.getX()));
        boxes[1].setValue(Integer.toString(pos.getY()));
        boxes[2].setValue(Integer.toString(pos.getZ()));
    }

    private Component shapeMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.shape_sphere" : "screen.autotorch.shape_box");
    }

    /** 转换按钮默认显示文字；开启符号模式后只显示转换方向。 */
    private Component convertShapeMessage() {
        if (ClientConfig.usesSymbolButtons()) {
            return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                    ? "screen.autotorch.convert_to_circumscribed_box_symbol"
                    : "screen.autotorch.convert_to_inscribed_sphere_symbol");
        }
        return convertShapeText();
    }

    /** 转换按钮的文字说明：随当前形状给出转换后的目标。 */
    private Component convertShapeText() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.convert_to_circumscribed_box"
                : "screen.autotorch.convert_to_inscribed_sphere");
    }

    /** 互换按钮默认显示文字；开启符号模式后区间用 A/B、球形用 C/R。 */
    private Component swapPointsMessage() {
        if (ClientConfig.usesSymbolButtons()) {
            return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                    ? "screen.autotorch.swap_center_radius_symbol"
                    : "screen.autotorch.swap_points_symbol");
        }
        return new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.swap_points");
    }

    /** 符号模式下按钮含义不直观，悬停时补充说明；文字模式本身已说明操作。 */
    private void updateActionTooltips() {
        boolean symbols = ClientConfig.usesSymbolButtons();
        if (symbols) {
            LegacyTooltips.set(convertShapeButton, convertShapeText());
            LegacyTooltips.set(swapPointsButton, new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.swap_points"));
        } else {
            LegacyTooltips.clear(convertShapeButton);
            LegacyTooltips.clear(swapPointsButton);
        }
    }

    private Component firstPointMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_center" : "screen.autotorch.use_current_a");
    }

    private Component secondPointMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_radius" : "screen.autotorch.use_current_b");
    }

    private Component exclusionMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.isEditingExclusion()
                ? "screen.autotorch.save_exclusion" : "screen.autotorch.add_exclusion");
    }

    /** 形状变化后刷新转换方向、互换对象和坐标按钮的文字与提示。 */
    private void updateShapeDependentMessages() {
        convertShapeButton.setMessage(LegacyText.string(convertShapeMessage()));
        swapPointsButton.setMessage(LegacyText.string(swapPointsMessage()));
        updateActionTooltips();
        useCurrentFirstButton.setMessage(LegacyText.string(firstPointMessage()));
        useCurrentSecondButton.setMessage(LegacyText.string(secondPointMessage()));
    }

    /** 档位顺序与语言标签一致：0=面，1=线，2=关。 */
    private static int selectionDisplayIndex() {
        if (!SelectionState.isOverlayEnabled()) {
            return 2;
        }
        return SelectionState.displayMode() == SelectionState.DisplayMode.FACES ? 0 : 1;
    }

    /** 直接切换到对应档位；选择面或线时顺带打开选区显示。 */
    private static void selectSelectionDisplay(int index) {
        if (index == 2) {
            if (SelectionState.isOverlayEnabled()) {
                SelectionState.toggleOverlay();
            }
            return;
        }
        SelectionState.setDisplayMode(index == 0
                ? SelectionState.DisplayMode.FACES : SelectionState.DisplayMode.LINES);
        if (!SelectionState.isOverlayEnabled()) {
            SelectionState.toggleOverlay();
        }
    }

    private Component sphereDisplayMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                ? "screen.autotorch.sphere_display_blocky" : "screen.autotorch.sphere_display_smooth");
    }

    /** 界面直接显示“包含天空光”；服务端载荷和任务内部仍使用相反的“仅无天空光”语义。 */
    private Component includeSkyLightMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(includeSkyLight
                ? "screen.autotorch.task_sky_light_on" : "screen.autotorch.task_sky_light_off");
    }

    private Component lightOverlayMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(LightOverlayState.isEnabled()
                ? "screen.autotorch.light_overlay_on" : "screen.autotorch.light_overlay_off");
    }

    /** 光照显示样式的档位顺序与 DisplayMode 枚举一致。 */
    private static int lightOverlayDisplayModeIndex() {
        return LightOverlayState.displayMode().ordinal();
    }

    private static void selectLightOverlayDisplayMode(int index) {
        LightOverlayState.setDisplayMode(LightOverlayState.DisplayMode.values()[index]);
    }

    private Component lightOverlayRenderThroughMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(ClientConfig.isLightOverlayRenderThrough()
                ? "screen.autotorch.light_overlay_render_through_on"
                : "screen.autotorch.light_overlay_render_through_off");
    }

    private Component swampSlimeDetectionMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(LightOverlayState.isSwampSlimeDetectionEnabled()
                ? "screen.autotorch.swamp_slime_detection_on"
                : "screen.autotorch.swamp_slime_detection_off");
    }

    private Component drownedDetectionMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(LightOverlayState.isDrownedDetectionEnabled()
                ? "screen.autotorch.drowned_detection_on"
                : "screen.autotorch.drowned_detection_off");
    }

    private Component nearbyAutoTorchMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(ClientConfig.isNearbyAutoTorchEnabled()
                ? "screen.autotorch.nearby_auto_torch_on"
                : "screen.autotorch.nearby_auto_torch_off");
    }

    private Component nearbyAutoTorchSkyLightMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(ClientConfig.includesSkyLight()
                ? "screen.autotorch.nearby_auto_torch_sky_light_on"
                : "screen.autotorch.nearby_auto_torch_sky_light_off");
    }

    private Component selectionToolMessage() {
        return new net.minecraft.network.chat.TranslatableComponent(ClientConfig.isSelectionToolEnabled()
                ? "screen.autotorch.selection_tool_on"
                : "screen.autotorch.selection_tool_off");
    }

    private int panelLeft() {
        return width / 2 - 155;
    }

    private int maxScrollOffset() {
        return Math.max(0, CONTENT_HEIGHT - (height - VIEWPORT_MARGIN * 2));
    }

    private int scrollbarX() {
        return Math.min(width - SCROLLBAR_WIDTH - 2, panelLeft() + 314);
    }

    private int scrollbarHeight() {
        int viewportHeight = height - VIEWPORT_MARGIN * 2;
        return Math.min(viewportHeight, Math.max(MIN_SCROLLBAR_HEIGHT,
                viewportHeight * viewportHeight / CONTENT_HEIGHT));
    }

    private int scrollbarY() {
        int maxScroll = maxScrollOffset();
        int travel = height - VIEWPORT_MARGIN * 2 - scrollbarHeight();
        return maxScroll == 0 ? VIEWPORT_MARGIN
                : VIEWPORT_MARGIN + scrollOffset * travel / maxScroll;
    }

    private void setScrollOffset(int offset) {
        int updated = Math.max(0, Math.min(offset, maxScrollOffset()));
        int delta = updated - scrollOffset;
        if (delta == 0) {
            return;
        }
        scrollOffset = updated;
        moveWidgets(-delta);
    }

    private void moveWidgets(int deltaY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof AbstractWidget) {
                ((AbstractWidget) child).y += deltaY;
            }
        }
    }

    private void scrollToMouse(double mouseY) {
        int trackHeight = height - VIEWPORT_MARGIN * 2;
        int travel = trackHeight - scrollbarHeight();
        if (travel <= 0) {
            return;
        }
        double thumbTop = mouseY - VIEWPORT_MARGIN - scrollbarHeight() / 2.0;
        setScrollOffset((int) Math.round(thumbTop * maxScrollOffset() / travel));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (maxScrollOffset() == 0 || scrollY == 0.0) {
            return false;
        }
        setScrollOffset(scrollOffset - (int) Math.round(scrollY * SCROLL_RATE));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScrollOffset() > 0 && button == 0
                && mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= VIEWPORT_MARGIN && mouseY < height - VIEWPORT_MARGIN) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        saveSelection();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean textInputFocused = getFocused() instanceof EditBox && ((EditBox) getFocused()).canConsumeInput();
        if (!textInputFocused && AutoTorchClient.OPEN_SCREEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        // 背景先按整屏绘制，再裁剪滚动内容，避免屏幕上下留出未压暗区域。
        renderBackground();
        {
            double scale = minecraft.window.getGuiScale();
            int windowHeight = minecraft.window.getHeight();
            LegacyText.enableScissor(0, (int) (windowHeight - height * scale),
                    (int) (width * scale), (int) ((height - 2 * VIEWPORT_MARGIN) * scale));
        }
        super.render(mouseX, mouseY, partialTick);
        int left = panelLeft();
        int offset = scrollOffset;
        drawCenteredString(font, LegacyText.string(title), width / 2, 6 - offset, 0xFFFFFFFF);
        boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
        drawString(font, LegacyText.string(sphere ? "C" : "A"), left + 5, 50 - offset, 0xFF70A0FF);
        drawString(font, LegacyText.string(sphere ? "R" : "B"), left + 5, 72 - offset, 0xFF70A0FF);
        if (sphere) {
            drawString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.radius_label")), left + 2, 94 - offset, 0xFF70A0FF);
        } else {
            drawString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.length_label")), left + 2, 94 - offset, 0xFF70A0FF);
            drawString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.width_label")), left + 104, 94 - offset, 0xFF70A0FF);
            drawString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.height_label")), left + 206, 94 - offset, 0xFF70A0FF);
        }
        int informationY = 208 - offset;
        if (!error.getString().isEmpty()) {
            drawCenteredString(font, LegacyText.string(error), width / 2, informationY, 0xFFFF6060);
        } else if (!rangeMessage.getString().isEmpty()) {
            drawCenteredString(font, LegacyText.string(rangeMessage), width / 2, informationY, 0xFFFFC060);
        } else if (!AutoTorchRules.canTorchMeetLightThreshold(ClientConfig.defaultTaskLightThreshold())) {
            drawCenteredString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_threshold_unreachable",
                    ClientConfig.defaultTaskLightThreshold())), width / 2, informationY, 0xFFFF6060);
        } else if (ServerConfigState.minSpacing() > AutoTorchRules.maxSafeSecondPassSpacing(
                ClientConfig.defaultTaskLightThreshold())) {
            drawCenteredString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.second_pass_spacing_limited",
                    effectiveSecondPassSpacing(), ClientConfig.defaultTaskLightThreshold())),
                    width / 2, informationY, 0xFFFFC060);
        } else {
            drawString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.zone_summary",
                    SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size())),
                    left, informationY, 0xFFA0A0A0);
        }
        fill(left, 218 - offset, left + 310, 219 - offset, 0xFF606060);
        drawCenteredString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_title")),
                width / 2, 222 - offset, 0xFFFFFFFF);
        fill(left, 310 - offset, left + 310, 311 - offset, 0xFF606060);
        drawCenteredString(font, LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.nearby_auto_torch_title")),
                width / 2, 314 - offset, 0xFFFFFFFF);
        LegacyText.disableScissor();

        if (maxScrollOffset() > 0) {
            int x = scrollbarX();
            int y = scrollbarY();
            int thumbColor = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH
                    && mouseY >= VIEWPORT_MARGIN && mouseY < height - VIEWPORT_MARGIN
                    ? 0xFFE0E0E0 : 0xFFB0B0B0;
            fill(x, VIEWPORT_MARGIN, x + SCROLLBAR_WIDTH, height - VIEWPORT_MARGIN, 0x80000000);
            fill(x, y, x + SCROLLBAR_WIDTH, y + scrollbarHeight(), thumbColor);
        }
        LegacyTooltips.render(this, font, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 标题区域内仅在悬停时显示文字的重置入口。 */
    private int resetButtonX(Component message) {
        return panelLeft() + 310 - font.width(LegacyText.string(message));
    }

    /** 在按钮左侧显示当前选区工具的物品图标，文字右移让出图标位置。 */
    private static final class SelectionToolButton extends Button {
        private static final ResourceLocation BUTTON = new ResourceLocation("minecraft", "widget/button");
        private static final ResourceLocation BUTTON_HIGHLIGHTED = new ResourceLocation("minecraft", "widget/button_highlighted");
        private static final ResourceLocation BUTTON_DISABLED = new ResourceLocation("minecraft", "widget/button_disabled");
        private ItemStack toolStack = ItemStack.EMPTY;

        private SelectionToolButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, LegacyText.string(message), onPress);
        }

        private void setToolStack(ItemStack value) {
            toolStack = value;
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            ResourceLocation sprite = !active ? BUTTON_DISABLED : (isHovered() || isFocused()) ? BUTTON_HIGHLIGHTED : BUTTON;
            LegacyWidgetSprites.blit(sprite, x, y, getWidth(), height);
            if (!toolStack.isEmpty()) {
                Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(toolStack, x + 4, y + 2);
            }
            // 图标右侧文字左对齐，避免文字位置随开关状态变化而漂移。
            int textLeft = x + 22;
            drawString(Minecraft.getInstance().font, LegacyText.string(getMessage()), textLeft, y + 6,
                    active ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    private static final class ResetButton extends Button {
        private final int hoverLeft;
        private final int hoverRight;

        private ResetButton(int x, int y, int width, int height, Component message,
                            int hoverLeft, int hoverRight, OnPress onPress) {
            super(x, y, width, height, LegacyText.string(message), onPress);
            this.hoverLeft = hoverLeft;
            this.hoverRight = hoverRight;
        }

        @Override
        public void renderButton(int mouseX, int mouseY, float partialTick) {
            boolean rowHovered = mouseX >= hoverLeft && mouseX < hoverRight
                    && mouseY >= y && mouseY < y + height;
            if ((isHovered() || isFocused()) || rowHovered) {
                drawString(Minecraft.getInstance().font, LegacyText.string(getMessage()), x + getWidth() -
                        Minecraft.getInstance().font.width(LegacyText.string(getMessage())), y + 4,
                        (isHovered() || isFocused()) ? 0xFFFFFF00 : 0xFFFFFFFF);
            }
        }
    }

    private static final class LightRangeSlider extends ScrollingSliderButton {
        private LightRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(LightOverlayState.horizontalRange()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.light_overlay_range_value", range())));
        }

        @Override
        protected void applyValue() {
            LightOverlayState.setHorizontalRange(range());
        }

        private int range() {
            int steps = ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.maxValue()
                    - ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue();
            return ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue()
                    + (int) Math.round(value * steps);
        }

        private static double toSliderValue(int range) {
            return (double) (range - ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue())
                    / (ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.maxValue()
                    - ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue());
        }
    }

    private static final class NearbyAutoTorchThresholdSlider extends ScrollingSliderButton {
        private NearbyAutoTorchThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(ClientConfig.nearbyAutoTorchThreshold()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(LegacyText.string(new net.minecraft.network.chat.TranslatableComponent(
                    "screen.autotorch.nearby_auto_torch_threshold", threshold())));
        }

        @Override
        protected void applyValue() {
            ClientConfig.setNearbyAutoTorchThreshold(threshold());
        }

        private int threshold() {
            int steps = ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.maxValue()
                    - ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.minValue();
            return ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.minValue()
                    + (int) Math.round(value * steps);
        }

        private static double toSliderValue(int threshold) {
            return (double) (threshold - ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.minValue())
                    / (ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.maxValue()
                    - ConfigDefinitions.NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.minValue());
        }
    }

    private static final class MinSpacingSlider extends ScrollingSliderButton {
        private MinSpacingSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(ClientStatusJson.effectiveDefaultMinSpacing()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int spacing = spacing();
            setMessage(LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.min_spacing", spacing)));
            LegacyTooltips.set(this, new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.min_spacing.tooltip.dynamic",
                    spacing, AutoTorchRules.secondPassSpacing(spacing,
                            ClientConfig.defaultTaskLightThreshold(), ServerConfigState.minSpacing())));
        }

        @Override
        protected void applyValue() {
            ClientConfig.setDefaultMinSpacing(spacing());
        }

        private int spacing() {
            int steps = ServerConfigState.maxSpacing() - ServerConfigState.minSpacing();
            return ServerConfigState.minSpacing() + (int) Math.round(value * steps);
        }

        private static double toSliderValue(int spacing) {
            int steps = ServerConfigState.maxSpacing() - ServerConfigState.minSpacing();
            return steps == 0 ? 0.0 : (double) (spacing - ServerConfigState.minSpacing()) / steps;
        }
    }

    private static final class AreaLightThresholdSlider extends ScrollingSliderButton {
        private AreaLightThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(ClientConfig.defaultTaskLightThreshold()));
            LegacyTooltips.set(this, new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.area_light_threshold.tooltip"));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(LegacyText.string(new net.minecraft.network.chat.TranslatableComponent("screen.autotorch.area_light_threshold", threshold())));
        }

        @Override
        protected void applyValue() {
            ClientConfig.setDefaultTaskLightThreshold(threshold());
        }

        private int threshold() {
            int steps = ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.maxValue()
                    - ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue();
            return ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue()
                    + (int) Math.round(value * steps);
        }

        private static double toSliderValue(int threshold) {
            return (double) (threshold - ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue())
                    / (ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.maxValue()
                    - ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue());
        }
    }
}
