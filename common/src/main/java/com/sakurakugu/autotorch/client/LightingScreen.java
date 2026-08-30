package com.sakurakugu.autotorch.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.opengl.GL11;
import com.sakurakugu.autotorch.network.PlatformNetworking;

/** 自动照明的参数界面，负责选区管理、客户端校验和任务提交。 */
public final class LightingScreen extends Screen {
    private static final int CONTENT_HEIGHT = 374;
    private static final int VIEWPORT_MARGIN = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_SCROLLBAR_HEIGHT = 20;
    private static final int SCROLL_RATE = 20;
    private final EditBox[] first = new EditBox[3];
    private final EditBox[] second = new EditBox[3];
    private final EditBox[] dimensions = new EditBox[3];
    private final Map<Button, ITextComponent> tooltips = new LinkedHashMap<>();
    private EditBox maxTorches;
    private Button shapeButton;
    private Button convertShapeButton;
    private Button sphereDisplayButton;
    private Button displayButton;
    private Button moreSettingsButton;
    private Button useCurrentFirstButton;
    private Button useCurrentSecondButton;
    private Button setLightingButton;
    private Button exclusionButton;
    private Button consumeButton;
    private Button undergroundButton;
    private Button lightOverlayButton;
    private Button lightOverlayModeButton;
    private Button swampSlimeDetectionButton;
    private Button drownedDetectionButton;
    private Button nearbyAutoTorchButton;
    private Button nearbyAutoTorchSkyLightButton;
    private Button woodenAxeSelectionButton;
    private Button startTaskButton;
    private Button cancelTaskButton;
    private boolean consumeTorches;
    private boolean undergroundOnly;
    private boolean syncingInputs;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private boolean selectionInRange = true;
    private ITextComponent error = new TextComponentString("");
    private ITextComponent rangeMessage = new TextComponentString("");

    public LightingScreen() {
        super(new TextComponentTranslation("screen.autotorch.title"));
        consumeTorches = initialConsumeTorches();
        undergroundOnly = ClientConfig.isDefaultUndergroundOnly();
    }

    @Override
    protected void init() {
        tooltips.clear();
        BlockPos playerPos = minecraft.player == null
                ? BlockPos.ORIGIN : minecraft.player.getPosition();
        int left = panelLeft();

        shapeButton = addRenderableWidget(button(left, 20, 126, 20, shapeMessage(), button -> {
            SelectionState.setShape(SelectionState.shape() == AreaShape.BOX ? AreaShape.SPHERE : AreaShape.BOX);
            shapeButton.setMessage(shapeMessage().getString());
            convertShapeButton.setMessage(convertShapeMessage().getString());
            updatePointButtonMessages();
            refreshDimensionInputs();
        }));
        sphereDisplayButton = addRenderableWidget(button(left + 130, 20, 90, 20, sphereDisplayMessage(), button -> {
            SelectionState.SphereDisplayMode next =
                    SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                            ? SelectionState.SphereDisplayMode.SMOOTH : SelectionState.SphereDisplayMode.BLOCKY;
            SelectionState.setSphereDisplayMode(next);
            sphereDisplayButton.setMessage(sphereDisplayMessage());
        }).bounds(left + 216, 88, 94, 20).build());
        displayButton = addRenderableWidget(Button.builder(displayMessage(), button -> {
            cycleSelectionDisplay();
            displayButton.setMessage(displayMessage());
        }).bounds(left + 130, 20, 90, 20).build());
        moreSettingsButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.autotorch.more_settings"), button -> {
                    // 更多设置入口暂不包含具体选项。
                }).bounds(left + 224, 20, 86, 20).build());

        createCoordinateRow(first, left, 44, SelectionState.first(playerPos));
        useCurrentFirstButton = addRenderableWidget(button(left + 190, 44, 120, 20, firstPointMessage(), button -> {
            setCoordinatePosition(first, currentPosition());
        }));

        createCoordinateRow(second, left, 66, SelectionState.second(playerPos));
        useCurrentSecondButton = addRenderableWidget(button(left + 190, 66, 120, 20, secondPointMessage(), button -> {
            setCoordinatePosition(second, currentPosition());
        }));

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

        convertShapeButton = addRenderableWidget(button(left, 112, 126, 20,
                convertShapeMessage(), button -> convertSelectionShape()));
        addRenderableWidget(button(left + 130, 112, 82, 20,
                new TextComponentTranslation("screen.autotorch.swap_points"), button -> swapPoints()));
        woodenAxeSelectionButton = addRenderableWidget(withTooltip(button(left + 216, 112, 94, 20,
                woodenAxeSelectionMessage(), button -> {
            boolean enabled = !ClientConfig.isWoodenAxeSelectionEnabled();
            ClientConfig.setWoodenAxeSelectionEnabled(enabled);
            PlatformNetworking.sendToServer(new SetSelectionToolPayload(enabled));
            woodenAxeSelectionButton.setMessage(woodenAxeSelectionMessage().getString());
        }), new TextComponentTranslation("screen.autotorch.wooden_axe_selection.tooltip")));

        setLightingButton = addRenderableWidget(new ColoredButton(left, 136, 120, 20,
                new TextComponentTranslation("screen.autotorch.set_lighting"), button -> setLightingZone(),
                0xDD176B35, 0xEE218A47));
        exclusionButton = addRenderableWidget(new ColoredButton(left + 124, 136, 120, 20,
                exclusionMessage(), button -> addExclusion(), 0xDDA52B2B, 0xEEC83C3C));
        updateZoneButtonAvailability();
        addRenderableWidget(button(left + 248, 136, 62, 20,
                new TextComponentTranslation("screen.autotorch.manage_exclusions"), button -> {
            saveSelection();
            saveTaskDefaults();
            minecraft.displayGuiScreen(new ExclusionListScreen());
        }));

        int configuredMaxTorches = effectiveDefaultMaxTorches();
        maxTorches = limitBox(left + 70, 160, 42,
                configuredMaxTorches == 0 ? "∞" : Integer.toString(configuredMaxTorches));
        addRenderableWidget(withTooltip(new MinSpacingSlider(left + 120, 160, 100, 20),
                new TextComponentTranslation("screen.autotorch.min_spacing.tooltip")));
        addRenderableWidget(withTooltip(new AreaLightThresholdSlider(left + 224, 160, 86, 20),
                new TextComponentTranslation("screen.autotorch.area_light_threshold.tooltip")));

        consumeButton = addRenderableWidget(button(left, 184, 153, 20, consumeMessage(), button -> {
            consumeTorches = !consumeTorches;
            if (isCreativePlayer()) {
                ClientConfig.setCreativeConsumesTorches(consumeTorches);
            } else {
                ClientConfig.setSurvivalConsumesTorches(consumeTorches);
            }
            consumeButton.setMessage(consumeMessage().getString());
        }));
        consumeButton.active = canChooseConsumeTorches();
        if (!consumeButton.active) {
            withTooltip(consumeButton, new TextComponentTranslation("screen.autotorch.consume.survival_server"));
        }
        undergroundButton = addRenderableWidget(button(left + 157, 184, 153, 20, undergroundMessage(), button -> {
            undergroundOnly = !undergroundOnly;
            ClientConfig.setDefaultUndergroundOnly(undergroundOnly);
            undergroundButton.setMessage(undergroundMessage().getString());
        }));

        startTaskButton = addRenderableWidget(button(left, 208, 153, 20,
                new TextComponentTranslation("screen.autotorch.start"), button -> startTask()));
        cancelTaskButton = addRenderableWidget(button(left + 157, 208, 153, 20,
                new TextComponentTranslation("screen.autotorch.cancel_task"), button -> {
            PlatformNetworking.sendToServer(new CancelLightingPayload());
            onClose();
        }));
        updateTaskButtonAvailability();

        lightOverlayButton = addRenderableWidget(button(left, 258, 106, 20, lightOverlayMessage(), button -> {
            LightOverlayState.toggle();
            lightOverlayButton.setMessage(lightOverlayMessage().getString());
        }));
        lightOverlayModeButton = addRenderableWidget(button(left + 110, 258, 88, 20,
                lightOverlayModeMessage(), button -> {
            LightOverlayState.cycleDisplayMode();
            lightOverlayModeButton.setMessage(lightOverlayModeMessage().getString());
        }));
        addRenderableWidget(new LightRangeSlider(left + 202, 258, 108, 20));

        swampSlimeDetectionButton = addRenderableWidget(button(left, 282, 153, 20,
                swampSlimeDetectionMessage(), button -> { }));
        swampSlimeDetectionButton.active = false;
        drownedDetectionButton = addRenderableWidget(withTooltip(button(left + 157, 282, 153, 20,
                drownedDetectionMessage(), button -> {
            LightOverlayState.toggleDrownedDetection();
            drownedDetectionButton.setMessage(drownedDetectionMessage().getString());
        }), new TextComponentTranslation("screen.autotorch.drowned_detection.tooltip.1.17.1-1.13.2")));

        nearbyAutoTorchButton = addRenderableWidget(withTooltip(button(left, 326, 153, 20,
                nearbyAutoTorchMessage(), button -> {
            ClientConfig.setNearbyAutoTorchEnabled(!ClientConfig.isNearbyAutoTorchEnabled());
            nearbyAutoTorchButton.setMessage(nearbyAutoTorchMessage().getString());
        }), new TextComponentTranslation("screen.autotorch.nearby_auto_torch.tooltip")));
        addRenderableWidget(new NearbyAutoTorchThresholdSlider(left + 157, 326, 153, 20));
        nearbyAutoTorchSkyLightButton = addRenderableWidget(button(left, 350, 310, 20,
                nearbyAutoTorchSkyLightMessage(), button -> {
            ClientConfig.setIncludesSkyLight(!ClientConfig.includesSkyLight());
            nearbyAutoTorchSkyLightButton.setMessage(nearbyAutoTorchSkyLightMessage().getString());
        }));

        scrollOffset = Math.min(scrollOffset, maxScrollOffset());
        moveWidgets(-scrollOffset);
    }

    private static Button button(int x, int y, int width, int height,
            ITextComponent message, Button.OnPress onPress) {
        return new Button(x, y, width, height, message.getString(), onPress);
    }

    private <T extends Button> T withTooltip(T widget, ITextComponent tooltip) {
        tooltips.put(widget, tooltip);
        return widget;
    }

    @Override
    public void tick() {
        super.tick();
        updateTaskButtonAvailability();
    }

    private void updateTaskButtonAvailability() {
        boolean enabled = ServerConfigState.lightingTaskEnabled();
        if (startTaskButton != null) startTaskButton.active = enabled;
        if (cancelTaskButton != null) cancelTaskButton.active = enabled;
    }

    private void createCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + 18 + i * 57, y, 53, Integer.toString(values[i]));
        }
    }

    private EditBox integerBox(int x, int y, int boxWidth, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, "");
        box.setMaxLength(9);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private EditBox limitBox(int x, int y, int boxWidth, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, "");
        box.setMaxLength(4);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private EditBox sizeBox(int x, int y, int boxWidth) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, "");
        box.setMaxLength(9);
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
            error = new TextComponentString("");
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
            error = new TextComponentString("");
            if (dimensionClamped) {
                rangeMessage = new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
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
            dimensions[0].setX(panelLeft() + 18);
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
            rangeMessage = selectionInRange ? new TextComponentString("")
                    : new TextComponentTranslation("screen.autotorch.sphere_radius_too_large",
                    ServerConfigState.maxSphereRadius());
        } else {
            long sizeX = Math.abs((long) secondPos.getX() - firstPos.getX()) + 1L;
            long sizeY = Math.abs((long) secondPos.getY() - firstPos.getY()) + 1L;
            long sizeZ = Math.abs((long) secondPos.getZ() - firstPos.getZ()) + 1L;
            selectionInRange = sizeX <= ServerConfigState.maxBoxAxisLength()
                    && sizeY <= ServerConfigState.maxBoxAxisLength()
                    && sizeZ <= ServerConfigState.maxBoxAxisLength();
            rangeMessage = selectionInRange ? new TextComponentString("")
                    : new TextComponentTranslation("screen.autotorch.box_axis_too_large",
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
            error = new TextComponentTranslation("screen.autotorch.invalid_value");
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
                    error = new TextComponentTranslation("screen.autotorch.convert_sphere_too_large");
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
                    error = new TextComponentTranslation(
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
            shapeButton.setMessage(shapeMessage().getString());
            convertShapeButton.setMessage(convertShapeMessage().getString());
            updatePointButtonMessages();
            refreshDimensionInputs(convertedFirst, convertedSecond);
            error = new TextComponentString("");
        } catch (IllegalArgumentException exception) {
            error = new TextComponentTranslation("screen.autotorch.invalid_value");
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
            exclusionButton.setMessage(exclusionMessage().getString());
            error = new TextComponentString("");
        } catch (IllegalArgumentException exception) {
            error = new TextComponentTranslation("screen.autotorch.invalid_value");
        }
    }

    private void addExclusion() {
        try {
            AreaZone zone = readDraftZone();
            validateZone(zone);
            if (!SelectionState.addExclusion(zone)) {
                error = new TextComponentTranslation("screen.autotorch.too_many_exclusions");
            } else {
                exclusionButton.setMessage(exclusionMessage().getString());
                error = new TextComponentString("");
            }
        } catch (IllegalArgumentException exception) {
            error = new TextComponentTranslation("screen.autotorch.invalid_value");
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
                error = new TextComponentTranslation("screen.autotorch.no_lighting_zone");
                return;
            }
            if (SelectionState.drafting()
                    || !readPosition(first).equals(SelectionState.first(selection.first()))
                    || !readPosition(second).equals(SelectionState.second(selection.second()))) {
                error = new TextComponentTranslation("screen.autotorch.confirm_draft");
                return;
            }
            int max = readLimit(maxTorches);
            int spacing = effectiveDefaultMinSpacing();
            int lightThreshold = ClientConfig.defaultTaskLightThreshold();
            ClientConfig.setDefaultMaxTorches(max);
            ClientConfig.setDefaultMinSpacing(spacing);
            PlatformNetworking.sendToServer(new StartLightingPayload(
                    selection, max, spacing, lightThreshold, consumeTorches,
                    undergroundOnly, SelectionState.exclusions()
            ));
            onClose();
        } catch (IllegalArgumentException exception) {
            error = new TextComponentTranslation("screen.autotorch.invalid_value");
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
            if (ServerConfigState.allowsUnlimitedTorches()) {
                return 0;
            }
            throw new IllegalArgumentException("Unlimited torches are disabled by the server");
        }
        return readPositive(box, 1, ServerConfigState.maxTorchesPerTask());
    }

    private void saveTaskDefaults() {
        try {
            ClientConfig.setDefaultMaxTorches(readLimit(maxTorches));
        } catch (IllegalArgumentException ignored) {
            // Keep the last valid defaults when closing a screen with incomplete input.
        }
    }

    private static int effectiveDefaultMaxTorches() {
        int configured = ClientConfig.defaultMaxTorches();
        if (configured == 0) {
            return ServerConfigState.allowsUnlimitedTorches()
                    ? 0 : ServerConfigState.maxTorchesPerTask();
        }
        return Math.min(configured, ServerConfigState.maxTorchesPerTask());
    }

    private static int effectiveDefaultMinSpacing() {
        return Math.max(ServerConfigState.minSpacing(),
                Math.min(ServerConfigState.maxSpacing(), ClientConfig.defaultMinSpacing()));
    }

    private static boolean isCreativePlayer() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }

    private static boolean isSingleplayerOwner() {
        return Minecraft.getInstance().isSingleplayer();
    }

    private static boolean canChooseConsumeTorches() {
        return isCreativePlayer() || isSingleplayerOwner();
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
        return minecraft.player == null
                ? BlockPos.ORIGIN : minecraft.player.getPosition();
    }

    private static void setPosition(EditBox[] boxes, BlockPos pos) {
        boxes[0].setValue(Integer.toString(pos.getX()));
        boxes[1].setValue(Integer.toString(pos.getY()));
        boxes[2].setValue(Integer.toString(pos.getZ()));
    }

    private ITextComponent shapeMessage() {
        return new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.shape_sphere" : "screen.autotorch.shape_box");
    }

    private ITextComponent convertShapeMessage() {
        return new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.convert_to_circumscribed_box"
                : "screen.autotorch.convert_to_inscribed_sphere");
    }

    private ITextComponent firstPointMessage() {
        return new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_center" : "screen.autotorch.use_current_a");
    }

    private ITextComponent secondPointMessage() {
        return new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                ? "screen.autotorch.use_current_radius" : "screen.autotorch.use_current_b");
    }

    private ITextComponent exclusionMessage() {
        return new TextComponentTranslation(SelectionState.isEditingExclusion()
                ? "screen.autotorch.save_exclusion" : "screen.autotorch.add_exclusion");
    }

    private void updatePointButtonMessages() {
        useCurrentFirstButton.setMessage(firstPointMessage().getString());
        useCurrentSecondButton.setMessage(secondPointMessage().getString());
    }

    private static void cycleSelectionDisplay() {
        if (!SelectionState.isOverlayEnabled()) {
            SelectionState.setDisplayMode(SelectionState.DisplayMode.FACES);
            SelectionState.toggleOverlay();
        } else if (SelectionState.displayMode() == SelectionState.DisplayMode.FACES) {
            SelectionState.setDisplayMode(SelectionState.DisplayMode.LINES);
        } else {
            SelectionState.toggleOverlay();
        }
    }

    private ITextComponent displayMessage() {
        if (!SelectionState.isOverlayEnabled()) {
            return new TextComponentTranslation("screen.autotorch.display_off");
        }
        return new TextComponentTranslation(SelectionState.displayMode() == SelectionState.DisplayMode.FACES
                ? "screen.autotorch.display_faces" : "screen.autotorch.display_lines");
    }

    private ITextComponent sphereDisplayMessage() {
        return new TextComponentTranslation(SelectionState.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY
                ? "screen.autotorch.sphere_display_blocky" : "screen.autotorch.sphere_display_smooth");
    }

    private ITextComponent consumeMessage() {
        if (!canChooseConsumeTorches()) {
            return new TextComponentTranslation(ServerConfigState.survivalConsumesTorches()
                    ? "screen.autotorch.consume_on" : "screen.autotorch.consume_off");
        }
        return new TextComponentTranslation(consumeTorches ? "screen.autotorch.consume_on" : "screen.autotorch.consume_off");
    }

    private ITextComponent undergroundMessage() {
        return new TextComponentTranslation(undergroundOnly ? "screen.autotorch.underground_on" : "screen.autotorch.underground_off");
    }

    private ITextComponent lightOverlayMessage() {
        return new TextComponentTranslation(LightOverlayState.isEnabled()
                ? "screen.autotorch.light_overlay_on" : "screen.autotorch.light_overlay_off");
    }

    private Component lightOverlayModeMessage() {
        return Component.translatable(switch (LightOverlayState.displayMode()) {
            case CROSSES -> "screen.autotorch.light_overlay_mode_crosses";
            case NUMBERS -> "screen.autotorch.light_overlay_mode_numbers";
            case BOXED_NUMBERS -> "screen.autotorch.light_overlay_mode_boxed_numbers";
        });
    }

    private ITextComponent swampSlimeDetectionMessage() {
        return new TextComponentTranslation("screen.autotorch.swamp_slime_detection_unavailable.1.17.1-");
    }

    private <T extends Button> T addRenderableWidget(T widget) {
        return addButton(widget);
    }

    private EditBox addRenderableWidget(EditBox widget) {
        children.add(widget);
        return widget;
    }

    private ITextComponent drownedDetectionMessage() {
        return new TextComponentTranslation(LightOverlayState.isDrownedDetectionEnabled()
                ? "screen.autotorch.drowned_detection_on"
                : "screen.autotorch.drowned_detection_off");
    }

    private ITextComponent nearbyAutoTorchMessage() {
        return new TextComponentTranslation(ClientConfig.isNearbyAutoTorchEnabled()
                ? "screen.autotorch.nearby_auto_torch_on"
                : "screen.autotorch.nearby_auto_torch_off");
    }

    private ITextComponent nearbyAutoTorchSkyLightMessage() {
        return new TextComponentTranslation(ClientConfig.includesSkyLight()
                ? "screen.autotorch.nearby_auto_torch_sky_light_on"
                : "screen.autotorch.nearby_auto_torch_sky_light_off");
    }

    private ITextComponent woodenAxeSelectionMessage() {
        return new TextComponentTranslation(ClientConfig.isWoodenAxeSelectionEnabled()
                ? "screen.autotorch.wooden_axe_selection_on"
                : "screen.autotorch.wooden_axe_selection_off");
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
        for (Object child : children()) {
            if (child instanceof Widget) {
                Widget widget = (Widget) child;
                widget.setY(widget.y() + deltaY);
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
        if (maxScrollOffset() > 0 && scrollY != 0.0) {
            setScrollOffset(scrollOffset - (int) Math.round(scrollY * SCROLL_RATE));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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
        saveTaskDefaults();
        super.onClose();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTick) {
        enableViewportScissor();
        super.render(mouseX, mouseY, partialTick);
        // 1.13 的 GuiScreen 只自动绘制按钮，文本框需要由界面显式绘制。
        for (Object child : children()) {
            if (child instanceof EditBox) {
                ((EditBox) child).render(mouseX, mouseY, partialTick);
            }
        }
        int left = panelLeft();
        int offset = scrollOffset;
        drawCenteredString(font, title.getString(), width / 2, 6 - offset, 0xFFFFFFFF);
        boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
        drawString(font, sphere ? "C" : "A", left + 5, 50 - offset, 0xFF70A0FF);
        drawString(font, sphere ? "R" : "B", left + 5, 72 - offset, 0xFF70A0FF);
        if (sphere) {
            drawString(font, new TextComponentTranslation("screen.autotorch.radius_label").getString(),
                    left + 2, 94 - offset, 0xFF70A0FF);
        } else {
            drawString(font, new TextComponentTranslation("screen.autotorch.length_label").getString(),
                    left + 2, 94 - offset, 0xFF70A0FF);
            drawString(font, new TextComponentTranslation("screen.autotorch.width_label").getString(),
                    left + 104, 94 - offset, 0xFF70A0FF);
            drawString(font, new TextComponentTranslation("screen.autotorch.height_label").getString(),
                    left + 206, 94 - offset, 0xFF70A0FF);
        }
        drawString(font, new TextComponentTranslation("screen.autotorch.max_torches").getString(),
                left, 166 - offset, 0xFFFFFFFF);
        int informationY = 232 - offset;
        if (!error.getString().isEmpty()) {
            drawCenteredString(font, error.getString(), width / 2, informationY, 0xFFFF6060);
        } else if (!rangeMessage.getString().isEmpty()) {
            drawCenteredString(font, rangeMessage.getString(), width / 2, informationY, 0xFFFFC060);
        } else {
            drawString(font, new TextComponentTranslation("screen.autotorch.zone_summary",
                    SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size()).getString(),
                    left, informationY, 0xFFA0A0A0);
        }
        fill(left, 242 - offset, left + 310, 243 - offset, 0xFF606060);
        drawCenteredString(font, new TextComponentTranslation("screen.autotorch.light_overlay_title").getString(),
                width / 2, 246 - offset, 0xFFFFFFFF);
        fill(left, 310 - offset, left + 310, 311 - offset, 0xFF606060);
        drawCenteredString(font, new TextComponentTranslation("screen.autotorch.nearby_auto_torch_title").getString(),
                width / 2, 314 - offset, 0xFFFFFFFF);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (maxScrollOffset() > 0) {
            int x = scrollbarX();
            int y = scrollbarY();
            int thumbColor = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH
                    && mouseY >= VIEWPORT_MARGIN && mouseY < height - VIEWPORT_MARGIN
                    ? 0xFFE0E0E0 : 0xFFB0B0B0;
            fill(x, VIEWPORT_MARGIN, x + SCROLLBAR_WIDTH,
                    height - VIEWPORT_MARGIN, 0x80000000);
            fill(x, y, x + SCROLLBAR_WIDTH, y + scrollbarHeight(), thumbColor);
        }
        if (mouseY >= VIEWPORT_MARGIN && mouseY < height - VIEWPORT_MARGIN) {
            for (Map.Entry<Button, ITextComponent> entry : tooltips.entrySet()) {
                if (entry.getKey().visible && entry.getKey().isMouseOver(mouseX, mouseY)) {
                    renderTooltip(entry.getValue().getString(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private void enableViewportScissor() {
        double scale = minecraft.mainWindow.getGuiScaleFactor();
        int bottom = (int) Math.round(VIEWPORT_MARGIN * scale);
        int viewportHeight = (int) Math.round((height - VIEWPORT_MARGIN * 2) * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, bottom, minecraft.mainWindow.getWidth(), viewportHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class LightRangeSlider extends AbstractSliderButton {
        private LightRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(LightOverlayState.horizontalRange()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new TextComponentTranslation(
                    "screen.autotorch.light_overlay_range_value", range()).getString());
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

    private static final class NearbyAutoTorchThresholdSlider extends AbstractSliderButton {
        private NearbyAutoTorchThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(ClientConfig.nearbyAutoTorchThreshold()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new TextComponentTranslation(
                    "screen.autotorch.nearby_auto_torch_threshold", threshold()).getString());
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

    private static final class MinSpacingSlider extends AbstractSliderButton {
        private MinSpacingSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(effectiveDefaultMinSpacing()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new TextComponentTranslation("screen.autotorch.min_spacing", spacing()).getString());
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

    private static final class AreaLightThresholdSlider extends AbstractSliderButton {
        private AreaLightThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, toSliderValue(ClientConfig.defaultTaskLightThreshold()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new TextComponentTranslation("screen.autotorch.area_light_threshold", threshold()).getString());
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
