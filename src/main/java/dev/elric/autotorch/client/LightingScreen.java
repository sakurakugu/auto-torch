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

    private final EditBox[] first = new EditBox[3];
    private final EditBox[] second = new EditBox[3];
    private EditBox maxTorches;
    private EditBox minSpacing;
    private Button shapeButton;
    private Button displayButton;
    private Button useCurrentFirstButton;
    private Button useCurrentSecondButton;
    private Button exclusionButton;
    private Button consumeButton;
    private Button undergroundButton;
    private boolean consumeTorches;
    private boolean undergroundOnly = true;
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
        }).bounds(left, 22, 150, 20).build());
        displayButton = addRenderableWidget(Button.builder(displayMessage(), button -> {
            SelectionState.DisplayMode next = SelectionState.displayMode() == SelectionState.DisplayMode.FACES
                    ? SelectionState.DisplayMode.LINES : SelectionState.DisplayMode.FACES;
            SelectionState.setDisplayMode(next);
            displayButton.setMessage(displayMessage());
        }).bounds(left + 160, 22, 150, 20).build());

        createCoordinateRow(first, left, 48, SelectionState.first(playerPos));
        useCurrentFirstButton = addRenderableWidget(Button.builder(firstPointMessage(), button -> {
            setPosition(first, currentPosition());
            saveSelection();
        }).bounds(left + 176, 48, 134, 20).build());

        createCoordinateRow(second, left, 72, SelectionState.second(playerPos));
        useCurrentSecondButton = addRenderableWidget(Button.builder(secondPointMessage(), button -> {
            setPosition(second, currentPosition());
            saveSelection();
        }).bounds(left + 176, 72, 134, 20).build());

        addRenderableWidget(new ColoredButton(left, 98, 120, 20,
                Component.translatable("screen.autotorch.set_lighting"), button -> setLightingZone(),
                0xDD176B35, 0xEE218A47));
        exclusionButton = addRenderableWidget(new ColoredButton(left + 124, 98, 120, 20,
                exclusionMessage(), button -> addExclusion(), 0xDDA52B2B, 0xEEC83C3C));
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.manage_exclusions"), button -> {
            saveSelection();
            minecraft.setScreen(new ExclusionListScreen());
        }).bounds(left + 248, 98, 62, 20).build());

        maxTorches = limitBox(left + 80, 124, 60, "∞");
        minSpacing = integerBox(left + 250, 124, 60, "8");

        consumeButton = addRenderableWidget(Button.builder(consumeMessage(), button -> {
            consumeTorches = !consumeTorches;
            consumeButton.setMessage(consumeMessage());
        }).bounds(left, 150, 150, 20).build());
        undergroundButton = addRenderableWidget(Button.builder(undergroundMessage(), button -> {
            undergroundOnly = !undergroundOnly;
            undergroundButton.setMessage(undergroundMessage());
        }).bounds(left + 160, 150, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.start"), button -> startTask())
                .bounds(left, 176, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.cancel_task"), button -> {
            ClientPacketDistributor.sendToServer(new CancelLightingPayload());
            onClose();
        }).bounds(left + 160, 176, 150, 20).build());
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
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        boolean sphere = SelectionState.shape() == AreaShape.SPHERE;
        graphics.text(font, sphere ? "C" : "A", left + 5, 54, 0xFF70A0FF);
        graphics.text(font, sphere ? "R" : "B", left + 5, 78, 0xFF70A0FF);
        graphics.text(font, Component.translatable("screen.autotorch.max_torches"), left, 130, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.min_spacing"), left + 155, 130, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.zone_summary",
                SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size()), left, 202, 0xFFA0A0A0);
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, 216, 0xFFFF6060);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
