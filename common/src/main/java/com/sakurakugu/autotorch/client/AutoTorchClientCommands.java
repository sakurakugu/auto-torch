package com.sakurakugu.autotorch.client;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 注册只在本地执行的 Auto Torch 客户端命令。 */
public final class AutoTorchClientCommands {
    private static final ChatFormatting STATUS_TITLE_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting STATUS_NEARBY_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting STATUS_OVERLAY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting STATUS_DETAILS_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting HELP_OPTION_COLOR = ChatFormatting.WHITE;
    private static final ChatFormatting HELP_SEPARATOR_COLOR = ChatFormatting.AQUA;

    private AutoTorchClientCommands() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(root());
    }

    private static <S> LiteralArgumentBuilder<S> root() {
        return AutoTorchClientCommands.<S>literal("autotorch")
                .executes(context -> openScreen())
                .then(AutoTorchClientCommands.<S>literal("gui").executes(context -> openScreen()))
                .then(AutoTorchClientCommands.<S>literal("help").executes(context -> showHelp()))
                .then(AutoTorchClientCommands.<S>literal("status").executes(context -> showStatus()))
                .then(AutoTorchClientCommands.<S>nearby())
                .then(AutoTorchClientCommands.<S>overlay());
    }

    private static <S> LiteralArgumentBuilder<S> nearby() {
        return AutoTorchClientCommands.<S>literal("nearby")
                .then(booleanLiteral("on", ClientConfig::setNearbyAutoTorchEnabled,
                        "command.autotorch.nearby", true))
                .then(booleanLiteral("off", ClientConfig::setNearbyAutoTorchEnabled,
                        "command.autotorch.nearby", false))
                .then(AutoTorchClientCommands.<S>literal("threshold")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<S, Integer>argument("value", IntegerArgumentType.integer(1, 16))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    ClientConfig.setNearbyAutoTorchThreshold(value);
                                    return feedback("command.autotorch.nearby_threshold", value);
                                })))
                .then(AutoTorchClientCommands.<S>literal("skylight")
                        .then(booleanLiteral("on", ClientConfig::setIncludesSkyLight,
                                "command.autotorch.nearby_skylight", true))
                        .then(booleanLiteral("off", ClientConfig::setIncludesSkyLight,
                                "command.autotorch.nearby_skylight", false)));
    }

    private static <S> LiteralArgumentBuilder<S> overlay() {
        return AutoTorchClientCommands.<S>literal("overlay")
                .then(booleanLiteral("on", LightOverlayState::setEnabled,
                        "command.autotorch.overlay", true))
                .then(booleanLiteral("off", LightOverlayState::setEnabled,
                        "command.autotorch.overlay", false))
                .then(AutoTorchClientCommands.<S>literal("range")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<S, Integer>argument("value", IntegerArgumentType.integer(1, 64))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    LightOverlayState.setHorizontalRange(value);
                                    return feedback("command.autotorch.overlay_range", value);
                                })))
                .then(AutoTorchClientCommands.<S>literal("mode")
                        .then(AutoTorchClientCommands.<S>literal("crosses").executes(context -> setOverlayMode(
                                LightOverlayState.DisplayMode.CROSSES)))
                        .then(AutoTorchClientCommands.<S>literal("numbers").executes(context -> setOverlayMode(
                                LightOverlayState.DisplayMode.NUMBERS))))
                .then(AutoTorchClientCommands.<S>literal("detect")
                        .then(AutoTorchClientCommands.<S>literal("swamp_slime")
                                .then(booleanLiteral("on", LightOverlayState::setSwampSlimeDetectionEnabled,
                                        "command.autotorch.overlay_swamp_slime", true))
                                .then(booleanLiteral("off", LightOverlayState::setSwampSlimeDetectionEnabled,
                                        "command.autotorch.overlay_swamp_slime", false)))
                        .then(AutoTorchClientCommands.<S>literal("drowned")
                                .then(booleanLiteral("on", LightOverlayState::setDrownedDetectionEnabled,
                                        "command.autotorch.overlay_drowned", true))
                                .then(booleanLiteral("off", LightOverlayState::setDrownedDetectionEnabled,
                                        "command.autotorch.overlay_drowned", false))));
    }

    private static <S> LiteralArgumentBuilder<S> booleanLiteral(String name,
                                                                Consumer<Boolean> setter,
                                                                String messageKey,
                                                                boolean value) {
        return AutoTorchClientCommands.<S>literal(name)
                .executes(context -> setBoolean(setter, messageKey, value));
    }

    private static int setBoolean(Consumer<Boolean> setter, String messageKey, boolean value) {
        setter.accept(value);
        return feedback(messageKey, state(value));
    }

    private static int setOverlayMode(LightOverlayState.DisplayMode mode) {
        LightOverlayState.setDisplayMode(mode);
        return feedback("command.autotorch.overlay_mode",
                Component.translatable(mode == LightOverlayState.DisplayMode.CROSSES
                        ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"));
    }

    private static int openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new LightingScreen());
        return 1;
    }

    private static int showHelp() {
        feedbackColored("command.autotorch.help.title", STATUS_TITLE_COLOR);
        helpLine(option("/autotorch [gui"), separator("|"), option("help"), separator("|"), option("status]"));
        helpLine(option("/autotorch nearby on"), separator("|"), option("off"));
        helpLine(option("/autotorch nearby threshold <1-16>"));
        helpLine(option("/autotorch nearby skylight on"), separator("|"), option("off"));
        helpLine(option("/autotorch overlay on"), separator("|"), option("off"));
        helpLine(option("/autotorch overlay range <1-64>"));
        helpLine(option("/autotorch overlay mode crosses"), separator("|"), option("numbers"));
        helpLine(option("/autotorch overlay detect swamp_slime"), separator("|"),
                option("drowned on"), separator("|"), option("off"));
        feedbackColored("------------------------------------------------", ChatFormatting.WHITE);
        return 1;
    }

    private static void helpLine(Component... parts) {
        MutableComponent line = Component.empty();
        for (Component part : parts) {
            line.append(part);
        }
        chat(line);
    }

    private static Component option(String text) {
        return Component.literal(text).withStyle(HELP_OPTION_COLOR);
    }

    private static Component separator(String text) {
        return Component.literal(text).withStyle(HELP_SEPARATOR_COLOR);
    }

    private static int showStatus() {
        feedbackColored("command.autotorch.status.title", STATUS_TITLE_COLOR);
        feedbackColored("command.autotorch.status.nearby", STATUS_NEARBY_COLOR,
                state(ClientConfig.isNearbyAutoTorchEnabled()),
                ClientConfig.nearbyAutoTorchThreshold(), state(ClientConfig.includesSkyLight()));
        feedbackColored("command.autotorch.status.overlay", STATUS_OVERLAY_COLOR,
                state(LightOverlayState.isEnabled()), LightOverlayState.horizontalRange(),
                Component.translatable(LightOverlayState.displayMode() == LightOverlayState.DisplayMode.CROSSES
                        ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"),
                specialDetection());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            AreaZone draft = SelectionState.draft(minecraft.player.blockPosition());
            boolean sphere = draft.shape() == AreaShape.SPHERE;
            boolean consumesTorches = minecraft.player.isCreative()
                    ? ClientConfig.creativeConsumesTorches()
                    : (minecraft.hasSingleplayerServer()
                            ? ClientConfig.survivalConsumesTorches()
                            : ServerConfigState.survivalConsumesTorches());
            int maxTorches = effectiveDefaultMaxTorches();
            feedbackColored("command.autotorch.status.selection", STATUS_DETAILS_COLOR,
                    Component.translatable(sphere
                            ? "command.autotorch.shape_sphere" : "command.autotorch.shape_box"),
                    Component.translatable(sphere
                            ? "command.autotorch.point1_c" : "command.autotorch.point1_a"),
                    formatPosition(draft.first()),
                    Component.translatable(sphere
                            ? "command.autotorch.point2_r" : "command.autotorch.point2_b"),
                    formatPosition(draft.second()),
                    SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size());
            feedbackColored("command.autotorch.status.task", STATUS_DETAILS_COLOR,
                    state(ClientConfig.isWoodenAxeSelectionEnabled()), state(consumesTorches),
                    maxTorches == 0 ? Component.translatable("command.autotorch.unlimited") : maxTorches,
                    effectiveDefaultMinSpacing(), ClientConfig.defaultTaskLightThreshold(),
                    state(ClientConfig.includesSkyLight()),
                    Component.translatable(sphere
                            ? "command.autotorch.shape_sphere" : "command.autotorch.shape_box"),
                    state(LightOverlayState.isEnabled()),
                    Component.translatable(LightOverlayState.displayMode() == LightOverlayState.DisplayMode.CROSSES
                            ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"));
        }
        feedbackColored("------------------------------------------------", ChatFormatting.WHITE);
        return 1;
    }

    private static int effectiveDefaultMaxTorches() {
        int configured = ClientConfig.defaultMaxTorches();
        if (configured == 0) {
            return ServerConfigState.allowsUnlimitedTorches() ? 0 : ServerConfigState.maxTorchesPerTask();
        }
        return Math.min(configured, ServerConfigState.maxTorchesPerTask());
    }

    private static int effectiveDefaultMinSpacing() {
        return Math.max(ServerConfigState.minSpacing(),
                Math.min(ServerConfigState.maxSpacing(), ClientConfig.defaultMinSpacing()));
    }

    private static Component specialDetection() {
        boolean swampSlime = LightOverlayState.isSwampSlimeDetectionEnabled();
        boolean drowned = LightOverlayState.isDrownedDetectionEnabled();
        String key = swampSlime
                ? (drowned ? "command.autotorch.special.both" : "command.autotorch.special.swamp_slime")
                : (drowned ? "command.autotorch.special.drowned" : "command.autotorch.special.none");
        return Component.translatable(key);
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static Component state(boolean enabled) {
        return Component.translatable(enabled ? "command.autotorch.on" : "command.autotorch.off");
    }

    private static int feedback(String key, Object... arguments) {
        chat(Component.translatable(key, arguments));
        return 1;
    }

    private static int feedbackColored(String key, ChatFormatting color, Object... arguments) {
        chat(Component.translatable(key, arguments).withStyle(color));
        return 1;
    }

    private static void chat(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }

    private static <S> LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
