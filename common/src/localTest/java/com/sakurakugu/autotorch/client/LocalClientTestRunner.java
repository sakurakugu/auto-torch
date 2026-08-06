package com.sakurakugu.autotorch.localtest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.LightingScreen;
import com.sakurakugu.autotorch.client.SelectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** 由本地 Python 驱动的客户端冒烟测试；不会进入正式发布 JAR。 */
public final class LocalClientTestRunner implements Consumer<Minecraft> {
    private static final String OUTPUT_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_DIR";
    private static final String WORLD_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_WORLD";
    private static final String CREATE_WORLD_ENVIRONMENT = "AUTOTORCH_LOCAL_TEST_CREATE_WORLD";
    private static final int SETTLE_TICKS = 30;
    private static final int CAPTURE_TIMEOUT_TICKS = 20 * 45;

    private final Path outputDirectory;
    private final String requestedWorld;
    private final boolean autoCreateWorld;
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

    public LocalClientTestRunner() {
        String configured = System.getenv(OUTPUT_ENVIRONMENT);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + OUTPUT_ENVIRONMENT);
        }
        outputDirectory = Path.of(configured).toAbsolutePath().normalize();
        requestedWorld = requireEnvironment(WORLD_ENVIRONMENT);
        autoCreateWorld = "1".equals(requireEnvironment(CREATE_WORLD_ENVIRONMENT));
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
            if (ticks == 1) {
                Files.createDirectories(outputDirectory);
                write("status.txt", "等待进入单人世界\n");
            }
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

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private void prepareWorldCheckpoint(Minecraft minecraft) throws IOException {
        if (!settled()) return;
        checkpoint("01-world", "游戏世界已加载");
        enter(Stage.WAITING_WORLD_CAPTURE);
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
        if (Files.exists(outputDirectory.resolve(name + ".captured"))) {
            appendResult("PASS", name + " 截图", "截图驱动已确认保存");
            enter(next);
        } else if (waitingTicks >= CAPTURE_TIMEOUT_TICKS) {
            throw new IllegalStateException("等待截图超时：" + name);
        }
    }

    private void complete(Minecraft minecraft) throws IOException {
        restoreConfiguration();
        write("status.txt", "PASS\n");
        write("completed", "PASS\n");
        stage = Stage.FINISHED;
        minecraft.stop();
    }

    private void fail(Minecraft minecraft, Exception exception) {
        try {
            restoreConfiguration();
            appendResult("FAIL", "客户端冒烟测试", exception.toString());
            write("status.txt", "FAIL: " + exception + "\n");
            write("completed", "FAIL\n");
        } catch (IOException ignored) {
            // 原始异常更重要；文件系统再次失败时直接退出客户端。
        }
        stage = Stage.FINISHED;
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
        write(name + ".ready", description + "\n");
    }

    private void enter(Stage next) {
        stage = next;
        ticks = 0;
        waitingTicks = 0;
    }

    private void appendResult(String result, String test, String detail) throws IOException {
        String line = result + "\t" + test + "\t" + detail.replace('\n', ' ') + System.lineSeparator();
        Files.writeString(outputDirectory.resolve("results.tsv"), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(outputDirectory.resolve(name), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
