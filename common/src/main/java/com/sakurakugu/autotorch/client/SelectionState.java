package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.world.World;
import net.minecraft.util.BlockPos;

/** 保存当前客户端世界中的临时选区；这些数据不会跨世界持久化。 */
public final class SelectionState {
    private static World level;
    private static BlockPos first;
    private static BlockPos second;
    private static AreaZone lightingZone;
    private static AreaShape shape = AreaShape.BOX;
    private static DisplayMode displayMode = ClientConfig.usesSelectionLines() ? DisplayMode.LINES : DisplayMode.FACES;
    private static SphereDisplayMode sphereDisplayMode = ClientConfig.usesSmoothSpheres()
            ? SphereDisplayMode.SMOOTH : SphereDisplayMode.BLOCKY;
    private static boolean overlayEnabled = ClientConfig.isSelectionOverlayEnabled();
    private static boolean drafting = true;
    private static int editingExclusion = -1;
    private static final List<AreaZone> EXCLUSIONS = new ArrayList<>();
    private static long renderRevision;

    private SelectionState() {
    }

    /** 配置整体替换后同步运行时渲染选项。 */
    public static void reloadConfig() {
        displayMode = ClientConfig.usesSelectionLines() ? DisplayMode.LINES : DisplayMode.FACES;
        sphereDisplayMode = ClientConfig.usesSmoothSpheres()
                ? SphereDisplayMode.SMOOTH : SphereDisplayMode.BLOCKY;
        overlayEnabled = ClientConfig.isSelectionOverlayEnabled();
        renderRevision++;
    }

    public static void updateLevel(World currentLevel, BlockPos currentPosition) {
        if (level != currentLevel) {
            level = currentLevel;
            first = currentPosition.getImmutable();
            second = currentPosition.getImmutable();
            lightingZone = null;
            shape = AreaShape.BOX;
            displayMode = ClientConfig.usesSelectionLines() ? DisplayMode.LINES : DisplayMode.FACES;
            sphereDisplayMode = ClientConfig.usesSmoothSpheres()
                    ? SphereDisplayMode.SMOOTH : SphereDisplayMode.BLOCKY;
            overlayEnabled = ClientConfig.isSelectionOverlayEnabled();
            drafting = true;
            editingExclusion = -1;
            EXCLUSIONS.clear();
            renderRevision++;
        }
    }

    public static BlockPos first(BlockPos fallback) {
        if (first == null) {
            first = fallback.getImmutable();
            renderRevision++;
        }
        return first;
    }

    public static BlockPos second(BlockPos fallback) {
        if (second == null) {
            second = fallback.getImmutable();
            renderRevision++;
        }
        return second;
    }

    public static void setFirst(BlockPos pos) {
        first = pos.getImmutable();
        drafting = true;
        renderRevision++;
    }

    public static void setSecond(BlockPos pos) {
        second = pos.getImmutable();
        drafting = true;
        renderRevision++;
    }

    public static void swapPoints(BlockPos fallback) {
        BlockPos oldFirst = first(fallback);
        first = second(fallback);
        second = oldFirst;
        drafting = true;
        renderRevision++;
    }

    public static void clearDraft(BlockPos fallback) {
        first = fallback.toImmutable();
        second = fallback.toImmutable();
        shape = AreaShape.BOX;
        editingExclusion = -1;
        drafting = true;
        renderRevision++;
    }

    public static AreaShape shape() {
        return shape;
    }

    public static void setShape(AreaShape value) {
        shape = value;
        drafting = true;
        renderRevision++;
    }

    public static DisplayMode displayMode() {
        return displayMode;
    }

    public static void setDisplayMode(DisplayMode value) {
        displayMode = value;
        ClientConfig.setUsesSelectionLines(value == DisplayMode.LINES);
        renderRevision++;
    }

    public static SphereDisplayMode sphereDisplayMode() {
        return sphereDisplayMode;
    }

    public static void setSphereDisplayMode(SphereDisplayMode value) {
        sphereDisplayMode = value;
        ClientConfig.setUsesSmoothSpheres(value == SphereDisplayMode.SMOOTH);
        renderRevision++;
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static boolean toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        ClientConfig.setSelectionOverlayEnabled(overlayEnabled);
        renderRevision++;
        return overlayEnabled;
    }

    public static boolean drafting() {
        return drafting;
    }

    public static AreaZone draft(BlockPos fallback) {
        return new AreaZone(shape, first(fallback), second(fallback));
    }

    public static AreaZone lightingZone() {
        return lightingZone;
    }

    public static void setLightingZone(AreaZone zone) {
        lightingZone = zone;
        drafting = false;
        editingExclusion = -1;
        renderRevision++;
    }

    public static boolean beginEditingLightingZone() {
        if (lightingZone == null) {
            return false;
        }
        first = lightingZone.first();
        second = lightingZone.second();
        shape = lightingZone.shape();
        editingExclusion = -1;
        drafting = true;
        renderRevision++;
        return true;
    }

    public static boolean removeLightingZone() {
        if (lightingZone == null) {
            return false;
        }
        lightingZone = null;
        renderRevision++;
        return true;
    }

    public static List<AreaZone> exclusions() {
        // 返回副本，防止界面或渲染代码绕过数量限制直接修改内部列表。
        return Collections.unmodifiableList(new ArrayList<>(EXCLUSIONS));
    }

    public static boolean addExclusion(AreaZone exclusion) {
        if (editingExclusion >= 0 && editingExclusion < EXCLUSIONS.size()) {
            EXCLUSIONS.set(editingExclusion, exclusion);
            editingExclusion = -1;
            drafting = false;
            renderRevision++;
            return true;
        }
        if (EXCLUSIONS.size() >= ServerConfigState.maxExclusions()) {
            return false;
        }
        EXCLUSIONS.add(exclusion);
        drafting = false;
        renderRevision++;
        return true;
    }

    public static boolean beginEditingExclusion(int index) {
        if (index < 0 || index >= EXCLUSIONS.size()) {
            return false;
        }
        AreaZone zone = EXCLUSIONS.get(index);
        first = zone.first();
        second = zone.second();
        shape = zone.shape();
        editingExclusion = index;
        drafting = true;
        renderRevision++;
        return true;
    }

    public static boolean removeExclusion(int index) {
        if (index < 0 || index >= EXCLUSIONS.size()) {
            return false;
        }
        EXCLUSIONS.remove(index);
        if (editingExclusion == index) {
            editingExclusion = -1;
        } else if (editingExclusion > index) {
            editingExclusion--;
        }
        renderRevision++;
        return true;
    }

    public static boolean replaceExclusion(int index, AreaZone zone) {
        if (index < 0 || index >= EXCLUSIONS.size()) {
            return false;
        }
        EXCLUSIONS.set(index, zone);
        editingExclusion = -1;
        drafting = false;
        renderRevision++;
        return true;
    }

    public static void clearExclusions() {
        if (!EXCLUSIONS.isEmpty() || editingExclusion >= 0) {
            EXCLUSIONS.clear();
            editingExclusion = -1;
            renderRevision++;
        }
    }

    public static void clearZones() {
        lightingZone = null;
        EXCLUSIONS.clear();
        editingExclusion = -1;
        drafting = true;
        renderRevision++;
    }

    static long renderRevision() {
        return renderRevision;
    }

    public static boolean isEditingExclusion() {
        return editingExclusion >= 0;
    }

    public enum DisplayMode {
        FACES,
        LINES
    }

    public enum SphereDisplayMode {
        BLOCKY,
        SMOOTH
    }
}
