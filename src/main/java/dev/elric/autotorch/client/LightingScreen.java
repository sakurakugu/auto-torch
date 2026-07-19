package dev.elric.autotorch.client;

import dev.elric.autotorch.network.CancelLightingPayload;
import dev.elric.autotorch.network.ExclusionZone;
import dev.elric.autotorch.network.StartLightingPayload;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 自动照明的参数界面，负责客户端校验并把任务配置发送给服务端。 */
public final class LightingScreen extends Screen {
    private static final Predicate<String> INTEGER_FILTER = value ->
            value.isEmpty() || value.equals("-") || value.matches("-?\\d{0,8}");
    private static final Predicate<String> LIMIT_FILTER = value ->
            value.isEmpty() || value.equals("∞") || value.matches("\\d{0,4}");

    private final EditBox[] first = new EditBox[3];
    private final EditBox[] second = new EditBox[3];
    private final EditBox[] excludedCenter = new EditBox[3];
    private EditBox maxTorches;
    private EditBox minSpacing;
    private EditBox excludedRadius;
    private Button consumeButton;
    private Button undergroundButton;
    private boolean consumeTorches = true;
    private boolean undergroundOnly = true;
    private Component error = Component.empty();

    public LightingScreen() {
        super(Component.translatable("screen.autotorch.title"));
    }

    @Override
    protected void init() {
        BlockPos playerPos = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        int left = panelLeft();

        createCoordinateRow(first, left, 42, SelectionState.first(playerPos));
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.use_current_a"), button -> {
            setPosition(first, currentPosition());
            saveSelection();
        })
                .bounds(left + 176, 42, 134, 20).build());

        createCoordinateRow(second, left, 68, SelectionState.second(playerPos));
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.use_current_b"), button -> {
            setPosition(second, currentPosition());
            saveSelection();
        })
                .bounds(left + 176, 68, 134, 20).build());

        maxTorches = limitBox(left + 80, 100, 60, "∞");
        minSpacing = integerBox(left + 250, 100, 60, "8");

        consumeButton = addRenderableWidget(Button.builder(consumeMessage(), button -> {
            consumeTorches = !consumeTorches;
            consumeButton.setMessage(consumeMessage());
        }).bounds(left, 126, 150, 20).build());
        undergroundButton = addRenderableWidget(Button.builder(undergroundMessage(), button -> {
            undergroundOnly = !undergroundOnly;
            undergroundButton.setMessage(undergroundMessage());
        }).bounds(left + 160, 126, 150, 20).build());

        createCompactCoordinateRow(excludedCenter, left, 168, playerPos);
        excludedRadius = integerBox(left + 144, 168, 38, "16");
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.add_exclusion"), button -> addExclusion())
                .bounds(left + 186, 168, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.undo_exclusion"), button -> {
            if (!SelectionState.removeLastExclusion()) {
                error = Component.translatable("screen.autotorch.no_exclusion");
            } else {
                error = Component.empty();
            }
        }).bounds(left + 250, 168, 60, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.start"), button -> startTask())
                .bounds(left, 200, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.autotorch.cancel_task"), button -> {
            ClientPacketDistributor.sendToServer(new CancelLightingPayload());
            onClose();
        }).bounds(left + 160, 200, 150, 20).build());
    }

    private void createCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + 18 + i * 52, y, 48, Integer.toString(values[i]));
        }
    }

    private void createCompactCoordinateRow(EditBox[] boxes, int left, int y, BlockPos initial) {
        int[] values = {initial.getX(), initial.getY(), initial.getZ()};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = integerBox(left + i * 48, y, 44, Integer.toString(values[i]));
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

    private void startTask() {
        try {
            BlockPos firstPos = readPosition(first);
            BlockPos secondPos = readPosition(second);
            int max = readLimit(maxTorches);
            int spacing = readPositive(minSpacing, 3, 12);
            SelectionState.setFirst(firstPos);
            SelectionState.setSecond(secondPos);

            // 客户端校验用于即时反馈；服务端仍会再次校验所有不可信的网络参数。
            ClientPacketDistributor.sendToServer(new StartLightingPayload(
                    firstPos,
                    secondPos,
                    max,
                    spacing,
                    consumeTorches,
                    undergroundOnly,
                    SelectionState.exclusions()
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
            // 协议中以 0 表示不限制火把数量。
            return 0;
        }
        return readPositive(box, 1, 4096);
    }

    private void addExclusion() {
        try {
            ExclusionZone exclusion = new ExclusionZone(
                    readPosition(excludedCenter),
                    readPositive(excludedRadius, 1, 128)
            );
            if (!SelectionState.addExclusion(exclusion)) {
                error = Component.translatable("screen.autotorch.too_many_exclusions");
            } else {
                error = Component.empty();
            }
        } catch (IllegalArgumentException exception) {
            error = Component.translatable("screen.autotorch.invalid_value");
        }
    }

    private void saveSelection() {
        try {
            SelectionState.setFirst(readPosition(first));
            SelectionState.setSecond(readPosition(second));
        } catch (IllegalArgumentException ignored) {
            // 关闭界面时可能仍有未输入完整的坐标，此时保留上一次有效选区。
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
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.range_hint"), left, 28, 0xFFA0A0A0);
        graphics.text(font, "A", left + 5, 48, 0xFFFFFFFF);
        graphics.text(font, "B", left + 5, 74, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.max_torches"), left, 106, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.min_spacing"), left + 155, 106, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.autotorch.exclusion_hint", SelectionState.exclusions().size()), left, 154, 0xFFA0A0A0);
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, 226, 0xFFFF6060);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
