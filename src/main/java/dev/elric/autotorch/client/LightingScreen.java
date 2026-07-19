package dev.elric.autotorch.client;

import dev.elric.autotorch.network.AreaShape;
import dev.elric.autotorch.network.AreaZone;
import dev.elric.autotorch.network.CancelLightingPayload;
import dev.elric.autotorch.network.StartLightingPayload;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 自动照明的参数界面，负责选区管理、客户端校验和任务提交。 */
public final class LightingScreen extends Screen {
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
    private Button sphereDisplayButton;
    private Button displayButton;
    private Button useCurrentFirstButton;
    private Button useCurrentSecondButton;
    private Button exclusionButton;
    private Button consumeButton;
    private Button undergroundButton;
    private boolean consumeTorches;
    private boolean undergroundOnly = true;
    private boolean syncingInputs;
    private Component error = Component.empty();

    public LightingScreen() {
        super(Component.translatable("screen.autotorch.title"));
        consumeTorches = Minecraft.getInstance().player == null || !Minecraft.getInstance().player.isCreative();
    }

    @Override
    protected void init() {
        BlockPos playerPos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        int left = panelLeft();

        shapeButton = addRenderableWidget(Button.builder(shapeMessage(), button -> {
            SelectionState.setShape(SelectionState.shape() == AreaShape.BOX ? AreaShape.SPHERE : AreaShape.BOX);
            shapeButton.setMessage(shapeMessage());
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
            SelectionState.DisplayMode next = SelectionState.displayMode() == SelectionState.DisplayMode.FACES
                    ? SelectionState.DisplayMode.LINES : SelectionState.DisplayMode.FACES;
            SelectionState.setDisplayMode(next);
            displayButton.setMessage(displayMessage());
        }).bounds(left + 224, 20, 86, 20).build());

        createCoordinateRow(first, left, 44, SelectionState.first(playerPos));
        useCurrentFirstButton = addRenderableWidget(Button.builder(firstPointMessage(), button -> {
            setCoordinatePosition(first, currentPosition());
        }).bounds(left + 176, 44, 134, 20).build());

        createCoordinateRow(second, left, 66, SelectionState.second(playerPos));
        useCurrentSecondButton = addRenderableWidget(Button.builder(secondPointMessage(), button -> {
            setCoordinatePosition(second, currentPosition());
        }).bounds(left + 176, 66, 134, 20).build());

        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = sizeBox(left + 18 + i * 52, 88, 48);
            dimensions[i].setResponder(value -> onDimensionChanged());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.swap_points"), button -> swapPoints())
                .bounds(left + 176, 88, 134, 20).build());

        for (EditBox box : first) {
            box.setResponder(value -> onCoordinatesChanged());
        }
        for (EditBox box : second) {
            box.setResponder(value -> onCoordinatesChanged());
        }
        refreshDimensionInputs();

        addRenderableWidget(new ColoredButton(left, 112, 120, 20,
                Component.translatable("screen.autotorch.set_lighting"), button -> setLightingZone(),
                0xDD176B35, 0xEE218A47));
        exclusionButton = addRenderableWidget(new ColoredButton(left + 124, 112, 120, 20,
                exclusionMessage(), button -> addExclusion(), 0xDDA52B2B, 0xEEC83C3C));
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.manage_exclusions"), button -> {
            saveSelection();
            minecraft.setScreen(new ExclusionListScreen());
        }).bounds(left + 248, 112, 62, 20).build());

        maxTorches = limitBox(left + 80, 136, 60, "∞");
        minSpacing = integerBox(left + 250, 136, 60, "8");

        consumeButton = addRenderableWidget(Button.builder(consumeMessage(), button -> {
            consumeTorches = !consumeTorches;
            consumeButton.setMessage(consumeMessage());
        }).bounds(left, 160, 150, 20).build());
        undergroundButton = addRenderableWidget(Button.builder(undergroundMessage(), button -> {
            undergroundOnly = !undergroundOnly;
            undergroundButton.setMessage(undergroundMessage());
        }).bounds(left + 160, 160, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.start"), button -> startTask())
                .bounds(left, 184, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.cancel_task"), button -> {
            ClientPacketDistributor.sendToServer(new CancelLightingPayload());
            onClose();
        }).bounds(left + 160, 184, 150, 20).build());
    }

    private void createCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + 18 + i * 52, y, 48, Integer.toString(values[i]));
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
            ClientPacketDistributor.sendToServer(new StartLightingPayload(
                    selection, max, spacing, consumeTorches, undergroundOnly, SelectionState.exclusions()
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

    private Component displayMessage() {
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

    private int panelLeft() {
        return width / 2 - 155;
    }

    @Override
    public void onClose() {
        saveSelection();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);
        boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
        graphics.text(font, sphere ? "C" : "A", left + 5, 50, 0xFF70A0FF);
        graphics.text(font, sphere ? "R" : "B", left + 5, 72, 0xFF70A0FF);
        if (sphere) {
            graphics.text(font, Component.translatable("screen.autotorch.radius_label"), left + 5, 94, 0xFF70A0FF);
        } else {
            graphics.text(font, Component.translatable("screen.autotorch.length_label"), left + 5, 94, 0xFF70A0FF);
            graphics.text(font, Component.translatable("screen.autotorch.width_label"), left + 57, 94, 0xFF70A0FF);
            graphics.text(font, Component.translatable("screen.autotorch.height_label"), left + 109, 94, 0xFF70A0FF);
        }
        graphics.text(font, Component.translatable("screen.autotorch.max_torches"), left, 142, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.min_spacing"), left + 155, 142, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.zone_summary",
                SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size()), left, 210, 0xFFA0A0A0);
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, 224, 0xFFFF6060);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
