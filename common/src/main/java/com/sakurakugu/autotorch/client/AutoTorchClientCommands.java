package com.sakurakugu.autotorch.client;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.network.TaskStatusRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/** 注册只在本地执行的 Auto Torch 客户端命令。 */
public final class AutoTorchClientCommands {
    private static final CommandDispatcher<Object> DISPATCHER = new CommandDispatcher<>();
    private static final TextFormatting STATUS_TITLE_COLOR = TextFormatting.GOLD;
    private static final TextFormatting STATUS_NEARBY_COLOR = TextFormatting.GREEN;
    private static final TextFormatting STATUS_OVERLAY_COLOR = TextFormatting.AQUA;
    private static final TextFormatting STATUS_DETAILS_COLOR = TextFormatting.YELLOW;
    private static final TextFormatting HELP_OPTION_COLOR = TextFormatting.WHITE;
    private static final TextFormatting HELP_SEPARATOR_COLOR = TextFormatting.AQUA;

    private AutoTorchClientCommands() {
    }

    static {
        register(DISPATCHER);
    }

    /** 旧版加载器没有客户端命令事件时，从聊天发送入口执行本地命令。 */
    public static boolean tryExecute(String message) {
        if (!message.equals("/autotorch") && !message.startsWith("/autotorch ")) {
            return false;
        }
        try {
            DISPATCHER.execute(message.substring(1), new Object());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            chat(new TextComponentString(exception.getMessage()));
        }
        return true;
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
                .then(AutoTorchClientCommands.<S>literal("config")
                        .then(AutoTorchClientCommands.<S>literal("defaults")
                                .executes(context -> resetConfigDefaults())))
                .then(AutoTorchClientCommands.<S>nearby())
                .then(AutoTorchClientCommands.<S>overlay())
                .then(AutoTorchClientCommands.<S>selection())
                .then(AutoTorchClientCommands.<S>zone())
                .then(AutoTorchClientCommands.<S>task());
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

    private static <S> LiteralArgumentBuilder<S> selection() {
        return AutoTorchClientCommands.<S>literal("selection")
                .then(pointCommand("pos1", true))
                .then(pointCommand("pos2", false))
                .then(AutoTorchClientCommands.<S>literal("swap").executes(context -> swapSelection()))
                .then(AutoTorchClientCommands.<S>literal("clear").executes(context -> clearSelection()))
                .then(AutoTorchClientCommands.<S>literal("box")
                        .then(AutoTorchClientCommands.<S>positionArgument("first")
                                .then(AutoTorchClientCommands.<S>positionArgument("second")
                                        .executes(context -> setBox(context)))))
                .then(AutoTorchClientCommands.<S>literal("sphere")
                        .then(AutoTorchClientCommands.<S>positionArgument("center")
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                        IntegerArgumentType.integer(1, AreaZone.MAX_SPHERE_RADIUS))
                                        .executes(context -> setSphere(context)))))
                .then(toolCommand("tool"))
                .then(toolCommand("wooden_axe"))
                .then(AutoTorchClientCommands.<S>literal("list").executes(context -> listZones()));
    }

    private static <S> LiteralArgumentBuilder<S> pointCommand(String name, boolean first) {
        return AutoTorchClientCommands.<S>literal(name)
                .executes(context -> setPoint(first, targetPosition()))
                .then(AutoTorchClientCommands.<S>literal("here")
                        .executes(context -> setPoint(first, playerPosition())))
                .then(AutoTorchClientCommands.<S>literal("target")
                        .executes(context -> setPoint(first, targetPosition())))
                .then(AutoTorchClientCommands.<S>positionArgument("pos")
                        .executes(context -> setPoint(first, position(context, "pos"))));
    }

    private static <S> LiteralArgumentBuilder<S> toolCommand(String name) {
        return AutoTorchClientCommands.<S>literal(name)
                .then(AutoTorchClientCommands.<S>literal("on").executes(context -> setSelectionTool(true)))
                .then(AutoTorchClientCommands.<S>literal("off").executes(context -> setSelectionTool(false)));
    }

    private static <S> LiteralArgumentBuilder<S> zone() {
        return AutoTorchClientCommands.<S>literal("zone")
                .then(AutoTorchClientCommands.<S>literal("list")
                        .executes(context -> listZones())
                        .then(RequiredArgumentBuilder.<S, Integer>argument("number", IntegerArgumentType.integer(0))
                                .executes(context -> listZone(IntegerArgumentType.getInteger(context, "number")))))
                .then(AutoTorchClientCommands.<S>literal("clear").executes(context -> clearZones()))
                .then(AutoTorchClientCommands.<S>literal("lighting")
                        .then(AutoTorchClientCommands.<S>literal("set").executes(context -> setLightingZone()))
                        .then(AutoTorchClientCommands.<S>literal("load").executes(context -> loadLightingZone()))
                        .then(AutoTorchClientCommands.<S>literal("clear").executes(context -> clearLightingZone())))
                .then(AutoTorchClientCommands.<S>literal("exclusion")
                        .then(AutoTorchClientCommands.<S>literal("add").executes(context -> addExclusion()))
                        .then(indexedZoneCommand("load", AutoTorchClientCommands::loadExclusion))
                        .then(indexedZoneCommand("replace", AutoTorchClientCommands::replaceExclusion))
                        .then(indexedZoneCommand("remove", AutoTorchClientCommands::removeExclusion))
                        .then(AutoTorchClientCommands.<S>literal("clear").executes(context -> clearExclusions())));
    }

    private static <S> LiteralArgumentBuilder<S> indexedZoneCommand(String name,
                                                                     java.util.function.IntUnaryOperator action) {
        return AutoTorchClientCommands.<S>literal(name)
                .then(RequiredArgumentBuilder.<S, Integer>argument("number", IntegerArgumentType.integer(1))
                        .executes(context -> action.applyAsInt(IntegerArgumentType.getInteger(context, "number"))));
    }

    private static <S> LiteralArgumentBuilder<S> task() {
        return AutoTorchClientCommands.<S>literal("task")
                .then(AutoTorchClientCommands.<S>literal("start").executes(context -> startTask()))
                .then(AutoTorchClientCommands.<S>literal("cancel").executes(context -> cancelTask()))
                .then(AutoTorchClientCommands.<S>literal("status").executes(context -> requestTaskStatus()));
    }

    private static <S> RequiredArgumentBuilder<S, String> positionArgument(String name) {
        return RequiredArgumentBuilder.argument(name,
                com.mojang.brigadier.arguments.StringArgumentType.greedyString());
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

    private static int resetConfigDefaults() {
        ClientConfig.resetDefaults();
        LightOverlayState.reloadConfig();
        SelectionState.reloadConfig();
        PlatformNetworking.sendToServer(new SetSelectionToolPayload(
                ClientConfig.isWoodenAxeSelectionEnabled()));
        return feedback("command.autotorch.config_defaults");
    }

    private static int setOverlayMode(LightOverlayState.DisplayMode mode) {
        LightOverlayState.setDisplayMode(mode);
        return feedback("command.autotorch.overlay_mode",
                new TextComponentTranslation(mode == LightOverlayState.DisplayMode.CROSSES
                        ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"));
    }

    private static <S> BlockPos position(CommandContext<S> context, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3d origin = minecraft.player == null ? Vec3d.ZERO : minecraft.player.getPositionVector();
        String input = context.getNodes().entrySet().stream()
                .filter(node -> node.getKey().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing command argument: " + name))
                .getValue()
                .get(context.getInput());
        String[] values = input.trim().split("\\s+");
        if (values[0].startsWith("^") && minecraft.player != null) {
            double left = localCoordinate(values[0]);
            double up = localCoordinate(values[1]);
            double forwards = localCoordinate(values[2]);
            float yaw = (minecraft.player.rotationYaw + 90.0F) * ((float) Math.PI / 180.0F);
            float pitch = -minecraft.player.rotationPitch * ((float) Math.PI / 180.0F);
            float upPitch = (-minecraft.player.rotationPitch + 90.0F) * ((float) Math.PI / 180.0F);
            Vec3d forward = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch),
                    Math.sin(yaw) * Math.cos(pitch));
            Vec3d upVector = new Vec3d(Math.cos(yaw) * Math.cos(upPitch), Math.sin(upPitch),
                    Math.sin(yaw) * Math.cos(upPitch));
            Vec3d leftVector = forward.crossProduct(upVector).scale(-1.0D);
            Vec3d eye = new Vec3d(minecraft.player.posX,
                    minecraft.player.posY + minecraft.player.getEyeHeight(), minecraft.player.posZ);
            Vec3d target = new Vec3d(
                    eye.x + leftVector.x * left + upVector.x * up + forward.x * forwards,
                    eye.y + leftVector.y * left + upVector.y * up + forward.y * forwards,
                    eye.z + leftVector.z * left + upVector.z * up + forward.z * forwards);
            return new BlockPos(MathHelper.floor(target.x), MathHelper.floor(target.y), MathHelper.floor(target.z));
        }
        return new BlockPos(MathHelper.floor(worldCoordinate(values[0], origin.x)),
                MathHelper.floor(worldCoordinate(values[1], origin.y)),
                MathHelper.floor(worldCoordinate(values[2], origin.z)));
    }

    private static double worldCoordinate(String value, double origin) {
        return value.startsWith("~") ? origin + localCoordinate(value) : Double.parseDouble(value);
    }

    private static double localCoordinate(String value) {
        return value.length() == 1 ? 0.0D : Double.parseDouble(value.substring(1));
    }

    private static BlockPos playerPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? BlockPos.ORIGIN : minecraft.player.getPosition();
    }

    private static BlockPos targetPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.objectMouseOver != null && minecraft.objectMouseOver.type == RayTraceResult.Type.BLOCK
                ? minecraft.objectMouseOver.getBlockPos() : playerPosition();
    }

    private static int setPoint(boolean first, BlockPos pos) {
        if (first) SelectionState.setFirst(pos); else SelectionState.setSecond(pos);
        return feedback("command.autotorch.selection_point", first ? 1 : 2, formatPosition(pos));
    }

    private static int swapSelection() {
        SelectionState.swapPoints(playerPosition());
        return feedback("command.autotorch.selection_swapped");
    }

    private static int clearSelection() {
        SelectionState.clearDraft(playerPosition());
        return feedback("command.autotorch.selection_cleared");
    }

    private static <S> int setBox(CommandContext<S> context) {
        BlockPos first = position(context, "first");
        BlockPos second = position(context, "second");
        SelectionState.setShape(AreaShape.BOX);
        SelectionState.setFirst(first);
        SelectionState.setSecond(second);
        return feedback("command.autotorch.selection_box", formatPosition(first), formatPosition(second));
    }

    private static <S> int setSphere(CommandContext<S> context) {
        BlockPos center = position(context, "center");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        SelectionState.setShape(AreaShape.SPHERE);
        SelectionState.setFirst(center);
        SelectionState.setSecond(center.add(radius, 0, 0));
        return feedback("command.autotorch.selection_sphere", formatPosition(center), radius);
    }

    private static int setSelectionTool(boolean enabled) {
        ClientConfig.setWoodenAxeSelectionEnabled(enabled);
        PlatformNetworking.sendToServer(new SetSelectionToolPayload(enabled));
        return feedback("command.autotorch.selection_tool", state(enabled));
    }

    private static int listZones() {
        feedback("command.autotorch.zone_summary", SelectionState.lightingZone() == null ? 0 : 1,
                SelectionState.exclusions().size());
        if (SelectionState.lightingZone() != null) showZone(0, SelectionState.lightingZone());
        for (int i = 0; i < SelectionState.exclusions().size(); i++) {
            showZone(i + 1, SelectionState.exclusions().get(i));
        }
        return 1;
    }

    private static int listZone(int number) {
        AreaZone zone = numberedZone(number);
        if (zone == null) return feedback("command.autotorch.zone_not_found", number);
        showZone(number, zone);
        return 1;
    }

    private static AreaZone numberedZone(int number) {
        if (number == 0) return SelectionState.lightingZone();
        int index = number - 1;
        return index >= 0 && index < SelectionState.exclusions().size()
                ? SelectionState.exclusions().get(index) : null;
    }

    private static void showZone(int number, AreaZone zone) {
        feedback("command.autotorch.zone_entry", number,
                new TextComponentTranslation(zone.shape() == AreaShape.SPHERE
                        ? "command.autotorch.shape_sphere" : "command.autotorch.shape_box"),
                formatPosition(zone.first()), formatPosition(zone.second()));
    }

    private static int clearZones() {
        SelectionState.clearZones();
        return feedback("command.autotorch.zones_cleared");
    }

    private static int setLightingZone() {
        AreaZone zone = SelectionState.draft(playerPosition());
        if (!validDraftZone(zone)) return feedback("command.autotorch.zone_out_of_range");
        SelectionState.setLightingZone(zone);
        return feedback("command.autotorch.lighting_set");
    }

    private static int loadLightingZone() {
        return SelectionState.beginEditingLightingZone()
                ? feedback("command.autotorch.lighting_loaded")
                : feedback("command.autotorch.no_lighting_zone");
    }

    private static int clearLightingZone() {
        return SelectionState.removeLightingZone()
                ? feedback("command.autotorch.lighting_cleared")
                : feedback("command.autotorch.no_lighting_zone");
    }

    private static int addExclusion() {
        AreaZone zone = SelectionState.draft(playerPosition());
        if (!validDraftZone(zone)) return feedback("command.autotorch.zone_out_of_range");
        return SelectionState.addExclusion(zone)
                ? feedback("command.autotorch.exclusion_added", SelectionState.exclusions().size())
                : feedback("command.autotorch.too_many_exclusions", ServerConfigState.maxExclusions());
    }

    private static int loadExclusion(int number) {
        return SelectionState.beginEditingExclusion(number - 1)
                ? feedback("command.autotorch.exclusion_loaded", number)
                : feedback("command.autotorch.zone_not_found", number);
    }

    private static int replaceExclusion(int number) {
        AreaZone zone = SelectionState.draft(playerPosition());
        if (!validDraftZone(zone)) return feedback("command.autotorch.zone_out_of_range");
        return SelectionState.replaceExclusion(number - 1, zone)
                ? feedback("command.autotorch.exclusion_replaced", number)
                : feedback("command.autotorch.zone_not_found", number);
    }

    private static int removeExclusion(int number) {
        return SelectionState.removeExclusion(number - 1)
                ? feedback("command.autotorch.exclusion_removed", number)
                : feedback("command.autotorch.zone_not_found", number);
    }

    private static int clearExclusions() {
        SelectionState.clearExclusions();
        return feedback("command.autotorch.exclusions_cleared");
    }

    private static boolean validDraftZone(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            long maximum = ServerConfigState.maxSphereRadius();
            return zone.radiusSquared() > 0L && zone.radiusSquared() <= maximum * maximum;
        }
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        int maximum = ServerConfigState.maxBoxAxisLength();
        return (long) max.getX() - min.getX() + 1L <= maximum
                && (long) max.getY() - min.getY() + 1L <= maximum
                && (long) max.getZ() - min.getZ() + 1L <= maximum;
    }

    private static int startTask() {
        AreaZone selection = SelectionState.lightingZone();
        if (selection == null) return feedback("command.autotorch.no_lighting_zone");
        if (SelectionState.drafting()) return feedback("command.autotorch.confirm_draft");
        Minecraft minecraft = Minecraft.getInstance();
        boolean consume = minecraft.player != null && minecraft.player.isCreative()
                ? ClientConfig.creativeConsumesTorches()
                : (minecraft.isIntegratedServerRunning() ? ClientConfig.survivalConsumesTorches()
                        : ServerConfigState.survivalConsumesTorches());
        PlatformNetworking.sendToServer(new StartLightingPayload(
                selection, effectiveDefaultMaxTorches(), effectiveDefaultMinSpacing(),
                ClientConfig.defaultTaskLightThreshold(), consume,
                ClientConfig.isDefaultUndergroundOnly(), SelectionState.exclusions()));
        return feedback("command.autotorch.task_submitted");
    }

    private static int cancelTask() {
        PlatformNetworking.sendToServer(new CancelLightingPayload());
        return feedback("command.autotorch.task_cancel_requested");
    }

    private static int requestTaskStatus() {
        PlatformNetworking.sendToServer(new TaskStatusRequestPayload());
        return 1;
    }

    public static void receiveTaskStatus(TaskStatusPayload status) {
        if (status.running()) {
            feedback("command.autotorch.task_running", status.percent(), status.placed());
        } else {
            feedback("command.autotorch.task_idle");
        }
    }

    private static int openScreen() {
        AutoTorchClient.requestOpenScreen();
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
        helpLine(option("/autotorch selection pos1"), separator("|"), option("pos2 [here|target|<pos>]"));
        helpLine(option("/autotorch selection box <pos1> <pos2>"));
        helpLine(option("/autotorch selection sphere <center> <radius>"));
        helpLine(option("/autotorch selection swap"), separator("|"), option("clear"));
        helpLine(option("/autotorch selection tool on"), separator("|"), option("off"));
        helpLine(option("/autotorch zone list [number]"), separator("|"), option("clear"));
        helpLine(option("/autotorch zone lighting set"), separator("|"), option("load"), separator("|"), option("clear"));
        helpLine(option("/autotorch zone exclusion add"), separator("|"), option("load"), separator("|"),
                option("replace"), separator("|"), option("remove <number>"), separator("|"), option("clear"));
        helpLine(option("/autotorch task start"), separator("|"), option("cancel"), separator("|"), option("status"));
        helpLine(option("/autotorch config defaults"));
        feedbackColored("------------------------------------------------", TextFormatting.WHITE);
        return 1;
    }

    private static void helpLine(ITextComponent... parts) {
        ITextComponent line = new TextComponentString("");
        for (ITextComponent part : parts) {
            line.appendSibling(part);
        }
        chat(line);
    }

    private static ITextComponent option(String text) {
        return new TextComponentString(text).applyTextStyle(HELP_OPTION_COLOR);
    }

    private static ITextComponent separator(String text) {
        return new TextComponentString(text).applyTextStyle(HELP_SEPARATOR_COLOR);
    }

    private static int showStatus() {
        feedbackColored("command.autotorch.status.title", STATUS_TITLE_COLOR);
        feedbackColored("command.autotorch.status.nearby", STATUS_NEARBY_COLOR,
                state(ClientConfig.isNearbyAutoTorchEnabled()),
                ClientConfig.nearbyAutoTorchThreshold(), state(ClientConfig.includesSkyLight()));
        feedbackColored("command.autotorch.status.overlay", STATUS_OVERLAY_COLOR,
                state(LightOverlayState.isEnabled()), LightOverlayState.horizontalRange(),
                new TextComponentTranslation(LightOverlayState.displayMode() == LightOverlayState.DisplayMode.CROSSES
                        ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"),
                specialDetection());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            AreaZone draft = SelectionState.draft(minecraft.player.getPosition());
            boolean sphere = draft.shape() == AreaShape.SPHERE;
            boolean consumesTorches = minecraft.player.isCreative()
                    ? ClientConfig.creativeConsumesTorches()
                    : (minecraft.isIntegratedServerRunning()
                            ? ClientConfig.survivalConsumesTorches()
                            : ServerConfigState.survivalConsumesTorches());
            int maxTorches = effectiveDefaultMaxTorches();
            feedbackColored("command.autotorch.status.selection", STATUS_DETAILS_COLOR,
                    new TextComponentTranslation(sphere
                            ? "command.autotorch.shape_sphere" : "command.autotorch.shape_box"),
                    new TextComponentTranslation(sphere
                            ? "command.autotorch.point1_c" : "command.autotorch.point1_a"),
                    formatPosition(draft.first()),
                    new TextComponentTranslation(sphere
                            ? "command.autotorch.point2_r" : "command.autotorch.point2_b"),
                    formatPosition(draft.second()),
                    SelectionState.lightingZone() == null ? 0 : 1, SelectionState.exclusions().size());
            feedbackColored("command.autotorch.status.task", STATUS_DETAILS_COLOR,
                    state(ClientConfig.isWoodenAxeSelectionEnabled()), state(consumesTorches),
                    maxTorches == 0 ? new TextComponentTranslation("command.autotorch.unlimited") : maxTorches,
                    effectiveDefaultMinSpacing(), ClientConfig.defaultTaskLightThreshold(),
                    state(ClientConfig.includesSkyLight()),
                    new TextComponentTranslation(sphere
                            ? "command.autotorch.shape_sphere" : "command.autotorch.shape_box"),
                    state(LightOverlayState.isEnabled()),
                    new TextComponentTranslation(LightOverlayState.displayMode() == LightOverlayState.DisplayMode.CROSSES
                            ? "command.autotorch.mode_crosses" : "command.autotorch.mode_numbers"));
        }
        feedbackColored("------------------------------------------------", TextFormatting.WHITE);
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

    private static ITextComponent specialDetection() {
        boolean swampSlime = LightOverlayState.isSwampSlimeDetectionEnabled();
        boolean drowned = LightOverlayState.isDrownedDetectionEnabled();
        String key = swampSlime
                ? (drowned ? "command.autotorch.special.both" : "command.autotorch.special.swamp_slime")
                : (drowned ? "command.autotorch.special.drowned" : "command.autotorch.special.none");
        return new TextComponentTranslation(key);
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static ITextComponent state(boolean enabled) {
        return new TextComponentTranslation(enabled ? "command.autotorch.on" : "command.autotorch.off");
    }

    private static int feedback(String key, Object... arguments) {
        chat(new TextComponentTranslation(key, arguments));
        return 1;
    }

    private static int feedbackColored(String key, TextFormatting color, Object... arguments) {
        chat(new TextComponentTranslation(key, arguments).applyTextStyle(color));
        return 1;
    }

    private static void chat(ITextComponent message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendMessage(message);
        }
    }

    private static <S> LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
