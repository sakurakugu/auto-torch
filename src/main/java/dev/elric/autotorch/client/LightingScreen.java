package dev.sakurakugu.autotorch.client;

import java.util.function.Predicate;

import dev.sakurakugu.autotorch.network.AreaShape;
import dev.sakurakugu.autotorch.network.AreaZone;
import dev.sakurakugu.autotorch.network.CancelLightingPayload;
import dev.sakurakugu.autotorch.network.StartLightingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 自动照明的参数界面，负责选区管理、客户端校验和任务提交。 */
public final class LightingScreen extends Screen {
    private static final int CONTENT_HEIGHT = 374;
    private static final int VIEWPORT_MARGIN = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_SCROLLBAR_HEIGHT = 20;
    private static final int SCROLL_RATE = 20;
    private static final int MAX_SELECTION_BOUND_AXIS = AreaZone.MAX_SPHERE_RADIUS * 2 + 1;
    private static final long MAX_SELECTION_BOUND_VOLUME =
            (long) MAX_SELECTION_BOUND_AXIS * MAX_SELECTION_BOUND_AXIS * MAX_SELECTION_BOUND_AXIS;
    private static final Predicate<String> INTEGER_FILTER = value ->
            value.isEmpty() || value.equals("-") || value.matches("-?\\d{0,8}");
    private static final Predicate<String> LIMIT_FILTER = value ->
            value.isEmpty() || value.equals("∞") || value.matches("\\d{0,4}");
    private static final Predicate<String> SIZE_FILTER = value ->
            value.isEmpty() || value.matches("\\d{0,9}");

    private final EditBox[] first = new EditBox[3];
    private final EditBox[] second = new EditBox[3];
    private final EditBox[] dimensions = new EditBox[3];
    private EditBox maxTorches;
    private EditBox minSpacing;
    private Button shapeButton;
    private Button convertShapeButton;
    private Button sphereDisplayButton;
    private Button displayButton;
    private Button useCurrentFirstButton;
    private Button useCurrentSecondButton;
    private Button exclusionButton;
    private Button consumeButton;
    private Button undergroundButton;
    private Button lightOverlayButton;
    private Button lightOverlayModeButton;
    private Button swampSlimeDetectionButton;
    private Button drownedDetectionButton;
    private Button nearbyAutoTorchButton;
    private Button nearbyAutoTorchSkyLightButton;
    private boolean consumeTorches;
    private boolean undergroundOnly;
    private boolean syncingInputs;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private Component error = Component.empty();

    public LightingScreen() {
        super(Component.translatable("screen.autotorch.title"));
        consumeTorches = isCreativePlayer() && ClientConfig.creativeConsumesTorches();
        undergroundOnly = ClientConfig.isDefaultUndergroundOnly();
    }

    @Override
    protected void init() {
        BlockPos playerPos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        int left = panelLeft();

        shapeButton = addRenderableWidget(Button.builder(shapeMessage(), button -> {
            SelectionState.setShape(SelectionState.shape() == AreaShape.BOX ? AreaShape.SPHERE : AreaShape.BOX);
            shapeButton.setMessage(shapeMessage());
            convertShapeButton.setMessage(convertShapeMessage());
            updatePointButtonMessages();
            refreshDimensionInputs();
        }).bounds(left, 20, 126, 20).build());
        sphereDisplayButton = addRenderableWidget(Button.builder(sphereDisplayMessage(), button -> {
            SelectionState.SphereDisplayMode next =
                    SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                            ? SelectionState.SphereDisplayMode.SMOOTH : SelectionState.SphereDisplayMode.BLOCKY;
            SelectionState.setSphereDisplayMode(next);
            sphereDisplayButton.setMessage(sphereDisplayMessage());
        }).bounds(left + 130, 20, 90, 20).build());
        displayButton = addRenderableWidget(Button.builder(displayMessage(), button -> {
            cycleSelectionDisplay();
            displayButton.setMessage(displayMessage());
        }).bounds(left + 224, 20, 86, 20).build());

        createCoordinateRow(first, left, 44, SelectionState.first(playerPos));
        useCurrentFirstButton = addRenderableWidget(Button.builder(firstPointMessage(), button -> {
            setCoordinatePosition(first, currentPosition());
        }).bounds(left + 190, 44, 120, 20).build());

        createCoordinateRow(second, left, 66, SelectionState.second(playerPos));
        useCurrentSecondButton = addRenderableWidget(Button.builder(secondPointMessage(), button -> {
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

        convertShapeButton = addRenderableWidget(Button.builder(convertShapeMessage(), button -> convertSelectionShape())
                .bounds(left, 112, 153, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.swap_points"), button -> swapPoints())
                .bounds(left + 157, 112, 153, 20).build());

        addRenderableWidget(new ColoredButton(left, 136, 120, 20,
                Component.translatable("screen.autotorch.set_lighting"), button -> setLightingZone(),
                0xDD176B35, 0xEE218A47));
        exclusionButton = addRenderableWidget(new ColoredButton(left + 124, 136, 120, 20,
                exclusionMessage(), button -> addExclusion(), 0xDDA52B2B, 0xEEC83C3C));
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.manage_exclusions"), button -> {
            saveSelection();
            saveTaskDefaults();
            minecraft.setScreen(new ExclusionListScreen());
        }).bounds(left + 248, 136, 62, 20).build());

        int configuredMaxTorches = ClientConfig.defaultMaxTorches();
        maxTorches = limitBox(left + 80, 160, 60,
                configuredMaxTorches == 0 ? "∞" : Integer.toString(configuredMaxTorches));
        minSpacing = integerBox(left + 250, 160, 60, Integer.toString(ClientConfig.defaultMinSpacing()));

        consumeButton = addRenderableWidget(Button.builder(consumeMessage(), button -> {
            consumeTorches = !consumeTorches;
            ClientConfig.setCreativeConsumesTorches(consumeTorches);
            consumeButton.setMessage(consumeMessage());
        }).bounds(left, 184, 153, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.autotorch.consume.creative_only")))
                .build());
        consumeButton.active = isCreativePlayer();
        undergroundButton = addRenderableWidget(Button.builder(undergroundMessage(), button -> {
            undergroundOnly = !undergroundOnly;
            ClientConfig.setDefaultUndergroundOnly(undergroundOnly);
            undergroundButton.setMessage(undergroundMessage());
        }).bounds(left + 157, 184, 153, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.start"), button -> startTask())
                .bounds(left, 208, 153, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.cancel_task"), button -> {
            ClientPacketDistributor.sendToServer(new CancelLightingPayload());
            onClose();
        }).bounds(left + 157, 208, 153, 20).build());

        lightOverlayButton = addRenderableWidget(Button.builder(lightOverlayMessage(), button -> {
            LightOverlayState.toggle();
            lightOverlayButton.setMessage(lightOverlayMessage());
        }).bounds(left, 258, 106, 20).build());
        lightOverlayModeButton = addRenderableWidget(Button.builder(lightOverlayModeMessage(), button -> {
            LightOverlayState.cycleDisplayMode();
            lightOverlayModeButton.setMessage(lightOverlayModeMessage());
        }).bounds(left + 110, 258, 88, 20).build());
        addRenderableWidget(new LightRangeSlider(left + 202, 258, 108, 20));

        swampSlimeDetectionButton = addRenderableWidget(Button.builder(swampSlimeDetectionMessage(), button -> {
            LightOverlayState.toggleSwampSlimeDetection();
            swampSlimeDetectionButton.setMessage(swampSlimeDetectionMessage());
        }).bounds(left, 282, 153, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.autotorch.swamp_slime_detection.tooltip")))
                .build());
        drownedDetectionButton = addRenderableWidget(Button.builder(drownedDetectionMessage(), button -> {
            LightOverlayState.toggleDrownedDetection();
            drownedDetectionButton.setMessage(drownedDetectionMessage());
        }).bounds(left + 157, 282, 153, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.autotorch.drowned_detection.tooltip")))
                .build());

        nearbyAutoTorchButton = addRenderableWidget(Button.builder(nearbyAutoTorchMessage(), button -> {
            ClientConfig.setNearbyAutoTorchEnabled(!ClientConfig.isNearbyAutoTorchEnabled());
            nearbyAutoTorchButton.setMessage(nearbyAutoTorchMessage());
        }).bounds(left, 326, 153, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.autotorch.nearby_auto_torch.tooltip")))
                .build());
        addRenderableWidget(new NearbyAutoTorchThresholdSlider(left + 157, 326, 153, 20));
        nearbyAutoTorchSkyLightButton = addRenderableWidget(Button.builder(nearbyAutoTorchSkyLightMessage(), button -> {
            ClientConfig.setIncludesSkyLight(!ClientConfig.includesSkyLight());
            nearbyAutoTorchSkyLightButton.setMessage(nearbyAutoTorchSkyLightMessage());
        }).bounds(left, 350, 310, 20).build());

        scrollOffset = Math.min(scrollOffset, maxScrollOffset());
        moveWidgets(-scrollOffset);
    }

    private void createCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + 18 + i * 57, y, 53, Integer.toString(values[i]));
        }
    }

    private EditBox integerBox(int x, int y, int boxWidth, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.empty());
        box.setMaxLength(9);
        box.setFilter(INTEGER_FILTER);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private EditBox limitBox(int x, int y, int boxWidth, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.empty());
        box.setMaxLength(4);
        box.setFilter(LIMIT_FILTER);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private EditBox sizeBox(int x, int y, int boxWidth) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.empty());
        box.setMaxLength(9);
        box.setFilter(SIZE_FILTER);
        return addRenderableWidget(box);
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
            if (SelectionState.shape() == AreaShape.SPHERE) {
                int radius = readPositive(dimensions[0], 1, AreaZone.MAX_SPHERE_RADIUS);
                updatedSecond = offsetChecked(anchor, radius, 0, 0);
            } else {
                int sizeX = readPositive(dimensions[0], 1, 256);
                int sizeZ = readPositive(dimensions[1], 1, 256);
                int sizeY = readPositive(dimensions[2], 1, 256);
                int[] anchorValues = {anchor.getX(), anchor.getY(), anchor.getZ()};
                int[] secondValues = {currentSecond.getX(), currentSecond.getY(), currentSecond.getZ()};
                int[] sizes = {sizeX, sizeY, sizeZ};
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
            dimensions[0].setX(panelLeft() + 18);
            dimensions[0].setValue(Integer.toString(sphere
                    ? new AreaZone(AreaShape.SPHERE, firstPos, secondPos).radius()
                    : Math.abs(secondPos.getX() - firstPos.getX()) + 1));
            dimensions[1].visible = !sphere;
            dimensions[2].visible = !sphere;
            if (!sphere) {
                dimensions[1].setValue(Integer.toString(Math.abs(secondPos.getZ() - firstPos.getZ()) + 1));
                dimensions[2].setValue(Integer.toString(Math.abs(secondPos.getY() - firstPos.getY()) + 1));
            }
        } finally {
            syncingInputs = false;
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
            error = Component.translatable("screen.autotorch.invalid_value");
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
                if (radius < 1) {
                    error = Component.translatable("screen.autotorch.convert_box_too_small");
                    return;
                }
                if (radius > AreaZone.MAX_SPHERE_RADIUS) {
                    error = Component.translatable("screen.autotorch.convert_sphere_too_large");
                    return;
                }
                convertedFirst = midpoint(min, max);
                convertedSecond = offsetChecked(convertedFirst, radius, 0, 0);
                convertedShape = AreaShape.SPHERE;
            } else {
                AreaZone sphere = new AreaZone(AreaShape.SPHERE, firstPos, secondPos);
                validateZone(sphere);
                int radius = sphere.radius();
                if (radius * 2L + 1L > 256L) {
                    error = Component.translatable("screen.autotorch.convert_box_too_large");
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
            shapeButton.setMessage(shapeMessage());
            convertShapeButton.setMessage(convertShapeMessage());
            updatePointButtonMessages();
            refreshDimensionInputs(convertedFirst, convertedSecond);
            error = Component.empty();
        } catch (IllegalArgumentException exception) {
            error = Component.translatable("screen.autotorch.invalid_value");
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
            exclusionButton.setMessage(exclusionMessage());
            error = Component.empty();
        } catch (IllegalArgumentException exception) {
            error = Component.translatable("screen.autotorch.invalid_value");
        }
    }

    private void addExclusion() {
        try {
            AreaZone zone = readDraftZone();
            validateZone(zone);
            if (!SelectionState.addExclusion(zone)) {
                error = Component.translatable("screen.autotorch.too_many_exclusions");
            } else {
                exclusionButton.setMessage(exclusionMessage());
                error = Component.empty();
            }
        } catch (IllegalArgumentException exception) {
            error = Component.translatable("screen.autotorch.invalid_value");
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
            long maxRadiusSquared = (long) AreaZone.MAX_SPHERE_RADIUS * AreaZone.MAX_SPHERE_RADIUS;
            if (zone.radiusSquared() <= 0L || zone.radiusSquared() > maxRadiusSquared) {
                throw new IllegalArgumentException("Sphere radius out of range");
            }
            return;
        }
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        if ((long) max.getX() - min.getX() >= 256L
                || (long) max.getY() - min.getY() >= 256L
                || (long) max.getZ() - min.getZ() >= 256L) {
            throw new IllegalArgumentException("Box axis out of range");
        }
    }

    private static void validateLightingZone(AreaZone zone) {
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        long sizeX = (long) max.getX() - min.getX() + 1L;
        long sizeY = (long) max.getY() - min.getY() + 1L;
        long sizeZ = (long) max.getZ() - min.getZ() + 1L;
        if (sizeX > MAX_SELECTION_BOUND_AXIS || sizeY > MAX_SELECTION_BOUND_AXIS
                || sizeZ > MAX_SELECTION_BOUND_AXIS
                || sizeX * sizeY * sizeZ > MAX_SELECTION_BOUND_VOLUME) {
            throw new IllegalArgumentException("Lighting area too large");
        }
    }

    private void startTask() {
        try {
            AreaZone selection = SelectionState.lightingZone();
            if (selection == null) {
                error = Component.translatable("screen.autotorch.no_lighting_zone");
                return;
            }
            if (SelectionState.drafting()
                    || !readPosition(first).equals(SelectionState.first(selection.first()))
                    || !readPosition(second).equals(SelectionState.second(selection.second()))) {
                error = Component.translatable("screen.autotorch.confirm_draft");
                return;
            }
            int max = readLimit(maxTorches);
            int spacing = readPositive(minSpacing, 3, 12);
            ClientConfig.setDefaultMaxTorches(max);
            ClientConfig.setDefaultMinSpacing(spacing);
            ClientPacketDistributor.sendToServer(new StartLightingPayload(
                    selection, max, spacing, isCreativePlayer() && consumeTorches,
                    undergroundOnly, SelectionState.exclusions()
            ));
            onClose();
        } catch (IllegalArgumentException exception) {
            error = Component.translatable("screen.autotorch.invalid_value");
        }
    }

    private static BlockPos readPosition(EditBox[] boxes) {
        return new BlockPos(
                Integer.parseInt(boxes[0].getValue()),
                Integer.parseInt(boxes[1].getValue()),
                Integer.parseInt(boxes[2].getValue())
        );
    }

    private static int readPositive(EditBox box, int minimum, int maximum) {
        int value = Integer.parseInt(box.getValue());
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Out of range");
        }
        return value;
    }

    private static int readLimit(EditBox box) {
        String value = box.getValue();
        if (value.isEmpty() || value.equals("∞")) {
            return 0;
        }
        return readPositive(box, 1, 4096);
    }

    private void saveTaskDefaults() {
        try {
            ClientConfig.setDefaultMaxTorches(readLimit(maxTorches));
            ClientConfig.setDefaultMinSpacing(readPositive(minSpacing, 3, 12));
        } catch (IllegalArgumentException ignored) {
            // Keep the last valid defaults when closing a screen with incomplete input.
        }
    }

    private static boolean isCreativePlayer() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
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
        return minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
    }

    private static void setPosition(EditBox[] boxes, BlockPos pos) {
        boxes[0].setValue(Integer.toString(pos.getX()));
        boxes[1].setValue(Integer.toString(pos.getY()));
        boxes[2].setValue(Integer.toString(pos.getZ()));
    }

    private Component shapeMessage() {
        return Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.shape_sphere" : "screen.autotorch.shape_box");
    }

    private Component convertShapeMessage() {
        return Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.convert_to_circumscribed_box"
                : "screen.autotorch.convert_to_inscribed_sphere");
    }

    private Component firstPointMessage() {
        return Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_center" : "screen.autotorch.use_current_a");
    }

    private Component secondPointMessage() {
        return Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_radius" : "screen.autotorch.use_current_b");
    }

    private Component exclusionMessage() {
        return Component.translatable(SelectionState.isEditingExclusion()
                ? "screen.autotorch.save_exclusion" : "screen.autotorch.add_exclusion");
    }

    private void updatePointButtonMessages() {
        useCurrentFirstButton.setMessage(firstPointMessage());
        useCurrentSecondButton.setMessage(secondPointMessage());
    }

    private static void cycleSelectionDisplay() {
        if (!SelectionState.isOverlayEnabled()) {
            SelectionState.toggleOverlay();
            SelectionState.setDisplayMode(SelectionState.DisplayMode.FACES);
        } else if (SelectionState.displayMode() == SelectionState.DisplayMode.FACES) {
            SelectionState.setDisplayMode(SelectionState.DisplayMode.LINES);
        } else {
            SelectionState.toggleOverlay();
        }
    }

    private Component displayMessage() {
        if (!SelectionState.isOverlayEnabled()) {
            return Component.translatable("screen.autotorch.display_off");
        }
        return Component.translatable(SelectionState.displayMode() == SelectionState.DisplayMode.FACES
                ? "screen.autotorch.display_faces" : "screen.autotorch.display_lines");
    }

    private Component sphereDisplayMessage() {
        return Component.translatable(SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                ? "screen.autotorch.sphere_display_blocky" : "screen.autotorch.sphere_display_smooth");
    }

    private Component consumeMessage() {
        return Component.translatable(consumeTorches ? "screen.autotorch.consume_on" : "screen.autotorch.consume_off");
    }

    private Component undergroundMessage() {
        return Component.translatable(undergroundOnly ? "screen.autotorch.underground_on" : "screen.autotorch.underground_off");
    }

    private Component lightOverlayMessage() {
        return Component.translatable(LightOverlayState.isEnabled()
                ? "screen.autotorch.light_overlay_on" : "screen.autotorch.light_overlay_off");
    }

    private Component lightOverlayModeMessage() {
        return Component.translatable(LightOverlayState.displayMode() == LightOverlayState.DisplayMode.CROSSES
                ? "screen.autotorch.light_overlay_mode_crosses" : "screen.autotorch.light_overlay_mode_numbers");
    }

    private Component swampSlimeDetectionMessage() {
        return Component.translatable(LightOverlayState.isSwampSlimeDetectionEnabled()
                ? "screen.autotorch.swamp_slime_detection_on"
                : "screen.autotorch.swamp_slime_detection_off");
    }

    private Component drownedDetectionMessage() {
        return Component.translatable(LightOverlayState.isDrownedDetectionEnabled()
                ? "screen.autotorch.drowned_detection_on"
                : "screen.autotorch.drowned_detection_off");
    }

    private Component nearbyAutoTorchMessage() {
        return Component.translatable(ClientConfig.isNearbyAutoTorchEnabled()
                ? "screen.autotorch.nearby_auto_torch_on"
                : "screen.autotorch.nearby_auto_torch_off");
    }

    private Component nearbyAutoTorchSkyLightMessage() {
        return Component.translatable(ClientConfig.includesSkyLight()
                ? "screen.autotorch.nearby_auto_torch_sky_light_on"
                : "screen.autotorch.nearby_auto_torch_sky_light_off");
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
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.setY(widget.getY() + deltaY);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (maxScrollOffset() == 0 || scrollY == 0.0) {
            return false;
        }
        setScrollOffset(scrollOffset - (int) Math.round(scrollY * SCROLL_RATE));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (maxScrollOffset() > 0 && event.button() == 0
                && event.x() >= scrollbarX() && event.x() < scrollbarX() + SCROLLBAR_WIDTH
                && event.y() >= VIEWPORT_MARGIN && event.y() < height - VIEWPORT_MARGIN) {
            draggingScrollbar = true;
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        saveSelection();
        saveTaskDefaults();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(0, VIEWPORT_MARGIN, width, height - VIEWPORT_MARGIN);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int offset = scrollOffset;
        graphics.centeredText(font, title, width / 2, 6 - offset, 0xFFFFFFFF);
        boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
        graphics.text(font, sphere ? "C" : "A", left + 5, 50 - offset, 0xFF70A0FF);
        graphics.text(font, sphere ? "R" : "B", left + 5, 72 - offset, 0xFF70A0FF);
        if (sphere) {
            graphics.text(font, Component.translatable("screen.autotorch.radius_label"), left + 2, 94 - offset, 0xFF70A0FF);
        } else {
            graphics.text(font, Component.translatable("screen.autotorch.length_label"), left + 2, 94 - offset, 0xFF70A0FF);
            graphics.text(font, Component.translatable("screen.autotorch.width_label"), left + 104, 94 - offset, 0xFF70A0FF);
            graphics.text(font, Component.translatable("screen.autotorch.height_label"), left + 206, 94 - offset, 0xFF70A0FF);
        }
        graphics.text(font, Component.translatable("screen.autotorch.max_torches"), left, 166 - offset, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.min_spacing"), left + 155, 166 - offset, 0xFFFFFFFF);
        int informationY = 232 - offset;
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, informationY, 0xFFFF6060);
        } else {
            graphics.text(font, Component.translatable("screen.autotorch.zone_summary",
                    SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size()),
                    left, informationY, 0xFFA0A0A0);
        }
        graphics.fill(left, 242 - offset, left + 310, 243 - offset, 0xFF606060);
        graphics.centeredText(font, Component.translatable("screen.autotorch.light_overlay_title"),
                width / 2, 246 - offset, 0xFFFFFFFF);
        graphics.fill(left, 310 - offset, left + 310, 311 - offset, 0xFF606060);
        graphics.centeredText(font, Component.translatable("screen.autotorch.nearby_auto_torch_title"),
                width / 2, 314 - offset, 0xFFFFFFFF);
        graphics.disableScissor();

        if (maxScrollOffset() > 0) {
            int x = scrollbarX();
            int y = scrollbarY();
            int thumbColor = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH
                    && mouseY >= VIEWPORT_MARGIN && mouseY < height - VIEWPORT_MARGIN
                    ? 0xFFE0E0E0 : 0xFFB0B0B0;
            graphics.fill(x, VIEWPORT_MARGIN, x + SCROLLBAR_WIDTH, height - VIEWPORT_MARGIN, 0x80000000);
            graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + scrollbarHeight(), thumbColor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class LightRangeSlider extends AbstractSliderButton {
        private LightRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), toSliderValue(LightOverlayState.horizontalRange()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.autotorch.light_overlay_range_value", range()));
        }

        @Override
        protected void applyValue() {
            LightOverlayState.setHorizontalRange(range());
        }

        private int range() {
            int steps = LightOverlayState.MAX_HORIZONTAL_RANGE - LightOverlayState.MIN_HORIZONTAL_RANGE;
            return LightOverlayState.MIN_HORIZONTAL_RANGE + (int) Math.round(value * steps);
        }

        private static double toSliderValue(int range) {
            return (double) (range - LightOverlayState.MIN_HORIZONTAL_RANGE)
                    / (LightOverlayState.MAX_HORIZONTAL_RANGE - LightOverlayState.MIN_HORIZONTAL_RANGE);
        }
    }

    private static final class NearbyAutoTorchThresholdSlider extends AbstractSliderButton {
        private NearbyAutoTorchThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (ClientConfig.nearbyAutoTorchThreshold() - 1) / 15.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(
                    "screen.autotorch.nearby_auto_torch_threshold", threshold()));
        }

        @Override
        protected void applyValue() {
            ClientConfig.setNearbyAutoTorchThreshold(threshold());
        }

        private int threshold() {
            return 1 + (int) Math.round(value * 15.0);
        }
    }
}
