package com.sakurakugu.autotorch.localtest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.LightingScreen;
import com.sakurakugu.autotorch.client.SelectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;

/** 由本地 Python 驱动的客户端冒烟测试；不会进入正式发布 JAR。 */
public final class LocalClientTestRunner implements Consumer<Minecraft> {
    private static final String HOST_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_HOST";
    private static final String PORT_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_PORT";
    private static final String TOKEN_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_TOKEN";
    private static final String WORLD_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_WORLD";
    private static final String CREATE_WORLD_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_CREATE_WORLD";
    private static final String TEMPLATE_WORLD_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_TEMPLATE_WORLD";
    private static final int SETTLE_TICKS = 30;
    private static final int CAPTURE_TIMEOUT_TICKS = 20 * 45;

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final String requestedWorld;
    private final boolean autoCreateWorld;
    private final boolean templateWorld;
    private Stage stage = Stage.WAITING_FOR_WORLD;
    private int ticks;
    private int waitingTicks;
    private boolean originalLightOverlayEnabled;
    private LightOverlayState.DisplayMode originalLightOverlayMode;
    private boolean originalSelectionOverlayEnabled;
    private SelectionState.DisplayMode originalSelectionMode;
    private boolean configurationCaptured;
    private boolean createWorldRequested;
    private boolean createWorldConfirmed;
    private CompletableFuture<String> templatePreparation;

    public LocalClientTestRunner() {
        requestedWorld = requireEnvironment(WORLD_ENVIRONMENT);
        autoCreateWorld = "1".equals(requireEnvironment(CREATE_WORLD_ENVIRONMENT));
        templateWorld = "1".equals(requireEnvironment(TEMPLATE_WORLD_ENVIRONMENT));
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(
                    requireEnvironment(HOST_ENVIRONMENT),
                    Integer.parseInt(requireEnvironment(PORT_ENVIRONMENT))), 10_000);
            socket.setTcpNoDelay(true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            JsonObject hello = new JsonObject();
            hello.addProperty("type", "hello");
            hello.addProperty("token", requireEnvironment(TOKEN_ENVIRONMENT));
            send(hello);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("无法连接本地测试驱动", exception);
        }
    }

    @Override
    public void accept(Minecraft minecraft) {
        if (stage == Stage.FINISHED) {
            return;
        }
        try {
            runStage(minecraft);
        } catch (Exception exception) {
            fail(minecraft, exception);
        }
    }

    private void runStage(Minecraft minecraft) throws IOException {
        switch (stage) {
            case WAITING_FOR_WORLD -> waitForWorld(minecraft);
            case PREPARING_WORLD -> prepareWorldCheckpoint(minecraft);
            case WAITING_WORLD_CAPTURE -> waitForCapture(minecraft, "01-world", Stage.OPENING_SETTINGS);
            case OPENING_SETTINGS -> openSettings(minecraft);
            case WAITING_SETTINGS_CAPTURE -> waitForCapture(minecraft, "02-settings", Stage.PREPARING_SELECTION);
            case PREPARING_SELECTION -> prepareSelection(minecraft);
            case WAITING_SELECTION_CAPTURE -> waitForCapture(minecraft, "03-selection", Stage.PREPARING_LIGHT_OVERLAY);
            case PREPARING_LIGHT_OVERLAY -> prepareLightOverlay(minecraft);
            case WAITING_LIGHT_CAPTURE -> waitForCapture(minecraft, "04-light-overlay", Stage.COMPLETING);
            case COMPLETING -> complete(minecraft);
            case FINISHED -> { }
        }
    }

    private void waitForWorld(Minecraft minecraft) throws IOException {
        ticks++;
        if (minecraft.level == null || minecraft.player == null) {
            createWorldIfNeeded(minecraft);
            return;
        }
        if (minecraft.gui.screen() != null) {
            return;
        }

        originalLightOverlayEnabled = LightOverlayState.isEnabled();
        originalLightOverlayMode = LightOverlayState.displayMode();
        originalSelectionOverlayEnabled = SelectionState.isOverlayEnabled();
        originalSelectionMode = SelectionState.displayMode();
        configurationCaptured = true;
        appendResult("PASS", "世界加载", "客户端和玩家已就绪");
        enter(Stage.PREPARING_WORLD);
    }

    private void createWorldIfNeeded(Minecraft minecraft) throws IOException {
        if (!autoCreateWorld || createWorldConfirmed) return;
        if (!createWorldRequested) {
            if (minecraft.gui.screen() == null || ticks < SETTLE_TICKS) return;
            createWorldRequested = true;
            appendResult("PASS", "创建世界", "请求创建存档：" + requestedWorld);
            CreateWorldScreen.openFresh(minecraft, () -> { });
            return;
        }
        if (!(minecraft.gui.screen() instanceof CreateWorldScreen screen)) return;

        screen.getUiState().setName(requestedWorld);
        if (templateWorld) configureTemplateWorld(screen.getUiState());
        createWorldConfirmed = true;
        try {
            // 原版没有公开自动确认创建的方法，本地测试通过反射调用创建按钮对应逻辑。
            Method onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(screen);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IOException("无法调用原版创建世界流程", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IOException("原版创建世界流程失败", cause);
        }
    }

    private static void configureTemplateWorld(WorldCreationUiState state) {
        state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        state.setDifficulty(Difficulty.PEACEFUL);
        state.setAllowCommands(true);
        state.setGenerateStructures(false);
        state.getNormalPresetList().stream()
                .filter(entry -> entry.preset().is(WorldPresets.FLAT))
                .findFirst()
                .ifPresentOrElse(state::setWorldType,
                        () -> { throw new IllegalStateException("创建界面中没有超平坦世界预设"); });
        var generator = state.getSettings().selectedDimensions().overworld();
        if (!(generator instanceof FlatLevelSource flatGenerator)) {
            throw new IllegalStateException("创建界面中的超平坦生成器不可用");
        }
        var layers = flatGenerator.settings().getLayersInfo();
        layers.clear();
        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(113, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        flatGenerator.settings().updateLayers();
        state.updateDimensions(PresetEditor.flatWorldConfigurator(flatGenerator.settings()));
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private void prepareWorldCheckpoint(Minecraft minecraft) throws IOException {
        if (templateWorld) {
            prepareTemplateWorld(minecraft);
            if (templatePreparation == null || !templatePreparation.isDone()) return;
            String detail;
            try {
                detail = templatePreparation.join();
            } catch (RuntimeException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) throw runtimeException;
                throw exception;
            }
            if (ticks == 0) appendResult("PASS", "模板世界", detail);
        }
        if (!settled()) return;
        checkpoint("01-world", "游戏世界已加载");
        enter(Stage.WAITING_WORLD_CAPTURE);
    }

    private void prepareTemplateWorld(Minecraft minecraft) {
        if (templatePreparation != null) return;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("模板世界必须运行在单人服务器中");
        templatePreparation = new CompletableFuture<>();
        server.execute(() -> {
            try {
                templatePreparation.complete(buildAndVerifyTemplate(server));
            } catch (Exception exception) {
                templatePreparation.completeExceptionally(exception);
            }
        });
    }

    private static String buildAndVerifyTemplate(MinecraftServer server) {
        var data = server.getWorldData();
        data.setGameType(GameType.CREATIVE);
        data.setDifficulty(Difficulty.PEACEFUL);
        data.setDifficultyLocked(false);
        data.setAllowCommands(true);
        server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.CREATIVE));

        var source = server.createCommandSourceStack()
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput();
        List<String> commands = List.of(
                "gamerule advance_time false",
                "time set noon",
                "weather clear",
                "setworldspawn 0 51 0",
                // 东侧：石头地板、七格高空气层和实体方块顶板组成的封闭无光室。
                "fill 16 50 -8 32 58 8 minecraft:tinted_glass",
                "fill 17 50 -7 31 50 7 minecraft:stone",
                "fill 17 51 -7 31 57 7 minecraft:air",
                // 西侧两片区域保留超平坦草地，只替换生物群系。
                "fillbiome -32 50 -8 -9 50 8 minecraft:swamp",
                "fillbiome -32 50 16 -9 50 32 minecraft:mangrove_swamp",
                // 北侧河道为浅水区，东北侧水池提供连续深水柱。
                "fill 15 50 15 48 50 48 minecraft:air",
                "fill -8 51 16 8 55 47 minecraft:water",
                "fillbiome -8 51 16 8 55 47 minecraft:river",
                "fill -9 50 15 9 50 48 minecraft:air",
                "fill 16 51 16 47 66 47 minecraft:water",
                "fillbiome 16 51 16 47 66 47 minecraft:deep_ocean",
                "setblock 32 62 32 minecraft:water",
                "fillbiome 32 62 32 32 62 32 minecraft:ocean",
                "tp @a 0 51 0"
        );
        commands.forEach(command -> server.getCommands().performPrefixedCommand(source, command));

        var level = server.overworld();
        require(data.isFlatWorld(), "存档不是超平坦世界");
        require(data.getGameType() == GameType.CREATIVE, "默认游戏模式不是创造模式");
        require(data.getDifficulty() == Difficulty.PEACEFUL, "难度不是和平模式");
        require(data.isAllowCommands(), "存档未允许作弊");
        require(level.getBlockState(new BlockPos(0, 50, 0)).is(Blocks.GRASS_BLOCK),
                "露天超平坦平台缺失");
        require(level.getBlockState(new BlockPos(0, -64, 0)).is(Blocks.BEDROCK)
                        && level.getBlockState(new BlockPos(0, 0, 0)).is(Blocks.DIRT)
                        && level.getBlockState(new BlockPos(0, 50, 0)).is(Blocks.GRASS_BLOCK),
                "自定义超平坦地形层不完整");
        require(level.getBlockState(new BlockPos(24, 50, 0)).is(Blocks.STONE)
                        && level.getBlockState(new BlockPos(24, 54, 0)).isAir()
                        && level.getBlockState(new BlockPos(24, 58, 0)).is(Blocks.TINTED_GLASS),
                "无光地下空间结构不完整");
        require(level.getBiome(new BlockPos(-20, 50, 0)).is(Biomes.SWAMP), "沼泽测试区缺失");
        require(level.getBiome(new BlockPos(-20, 50, 24)).is(Biomes.MANGROVE_SWAMP),
                "红树林沼泽测试区缺失");
        require(level.getBiome(new BlockPos(0, 51, 32)).is(Biomes.RIVER)
                        && level.getBlockState(new BlockPos(0, 51, 32)).is(Blocks.WATER),
                "河流测试区缺失");
        require((level.getBiome(new BlockPos(32, 62, 32)).is(Biomes.DEEP_OCEAN)
                        || level.getBiome(new BlockPos(32, 62, 32)).is(Biomes.OCEAN))
                        && level.getBlockState(new BlockPos(32, 62, 32)).is(Blocks.WATER),
                "海洋深水测试区缺失");
        return "超平坦、创造、和平、允许作弊；平台、暗室、双沼泽、河流和深水区均已校验";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void openSettings(Minecraft minecraft) throws IOException {
        if (!(minecraft.gui.screen() instanceof LightingScreen)) {
            minecraft.gui.setScreen(new LightingScreen());
            ticks = 0;
            return;
        }
        if (!settled()) return;
        if (!(minecraft.gui.screen() instanceof LightingScreen)) {
            throw new IllegalStateException("自动照明设置界面未能打开");
        }
        appendResult("PASS", "设置界面", "LightingScreen 已成功初始化");
        checkpoint("02-settings", "自动照明设置界面");
        enter(Stage.WAITING_SETTINGS_CAPTURE);
    }

    private void prepareSelection(Minecraft minecraft) throws IOException {
        if (minecraft.player == null) throw new IllegalStateException("玩家已离开世界");
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
            ticks = 0;
            return;
        }
        if (!settled()) return;

        BlockPos player = minecraft.player.blockPosition();
        Direction direction = minecraft.player.getDirection();
        BlockPos center = player.relative(direction, 5);
        BlockPos first = center.offset(-2, -1, -2);
        BlockPos second = center.offset(2, 3, 2);
        SelectionState.setFirst(first);
        SelectionState.setSecond(second);
        SelectionState.setDisplayMode(SelectionState.DisplayMode.FACES);
        if (!SelectionState.isOverlayEnabled()) SelectionState.toggleOverlay();

        if (!SelectionState.draft(player).first().equals(first)
                || !SelectionState.draft(player).second().equals(second)) {
            throw new IllegalStateException("选区状态与测试坐标不一致");
        }
        appendResult("PASS", "选区状态", first + " -> " + second);
        checkpoint("03-selection", "玩家前方的方块选区");
        enter(Stage.WAITING_SELECTION_CAPTURE);
    }

    private void prepareLightOverlay(Minecraft minecraft) throws IOException {
        LightOverlayState.setDisplayMode(LightOverlayState.DisplayMode.NUMBERS);
        LightOverlayState.setEnabled(true);
        if (!settled()) return;
        if (!LightOverlayState.isEnabled()) {
            throw new IllegalStateException("光照覆盖未能启用");
        }
        appendResult("PASS", "光照覆盖", "已启用数字模式，当前标记数：" + LightOverlayState.markers().size());
        checkpoint("04-light-overlay", "光照强度数字覆盖");
        enter(Stage.WAITING_LIGHT_CAPTURE);
    }

    private void waitForCapture(Minecraft minecraft, String name, Stage next) throws IOException {
        waitingTicks++;
        while (reader.ready()) {
            String line = reader.readLine();
            if (line == null) throw new IOException("本地测试驱动已断开连接");
            JsonObject message = JsonParser.parseString(line).getAsJsonObject();
            if ("screenshot_captured".equals(message.get("type").getAsString())
                    && name.equals(message.get("name").getAsString())) {
                appendResult("PASS", name + " 截图", "截图驱动已确认保存");
                enter(next);
                return;
            }
        }
        if (waitingTicks >= CAPTURE_TIMEOUT_TICKS) {
            throw new IllegalStateException("等待截图超时：" + name);
        }
    }

    private void complete(Minecraft minecraft) throws IOException {
        restoreConfiguration();
        sendCompleted("PASS");
        stage = Stage.FINISHED;
        closeConnection();
        minecraft.stop();
    }

    private void fail(Minecraft minecraft, Exception exception) {
        try {
            restoreConfiguration();
            appendResult("FAIL", "客户端冒烟测试", exception.toString());
            sendCompleted("FAIL");
        } catch (IOException ignored) {
            // 原始异常更重要；通信再次失败时直接退出客户端。
        }
        stage = Stage.FINISHED;
        closeConnection();
        minecraft.stop();
    }

    private void restoreConfiguration() {
        if (!configurationCaptured) return;
        LightOverlayState.setEnabled(originalLightOverlayEnabled);
        LightOverlayState.setDisplayMode(originalLightOverlayMode);
        if (SelectionState.isOverlayEnabled() != originalSelectionOverlayEnabled) SelectionState.toggleOverlay();
        SelectionState.setDisplayMode(originalSelectionMode);
    }

    private boolean settled() {
        return ++ticks >= SETTLE_TICKS;
    }

    private void checkpoint(String name, String description) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("type", "checkpoint_ready");
        message.addProperty("name", name);
        message.addProperty("description", description);
        send(message);
    }

    private void enter(Stage next) {
        stage = next;
        ticks = 0;
        waitingTicks = 0;
    }

    private void appendResult(String result, String test, String detail) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("type", "assertion");
        message.addProperty("status", result);
        message.addProperty("test", test);
        message.addProperty("detail", detail.replace('\n', ' '));
        send(message);
    }

    private void sendCompleted(String status) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("type", "completed");
        message.addProperty("status", status);
        send(message);
    }

    private void send(JsonObject message) throws IOException {
        writer.write(message.toString());
        writer.newLine();
        writer.flush();
    }

    private void closeConnection() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 测试已经结束，关闭连接失败不影响最终结果。
        }
    }

    private enum Stage {
        WAITING_FOR_WORLD,
        PREPARING_WORLD,
        WAITING_WORLD_CAPTURE,
        OPENING_SETTINGS,
        WAITING_SETTINGS_CAPTURE,
        PREPARING_SELECTION,
        WAITING_SELECTION_CAPTURE,
        PREPARING_LIGHT_OVERLAY,
        WAITING_LIGHT_CAPTURE,
        COMPLETING,
        FINISHED
    }
}
