package dev.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.sakurakugu.autotorch.network.AreaShape;
import dev.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** 保存当前客户端世界中的临时选区；这些数据不会跨世界持久化。 */
public final class SelectionState {
    private static @Nullable ClientLevel level;
    private static @Nullable BlockPos first;
    private static @Nullable BlockPos second;
    private static @Nullable AreaZone lightingZone;
    private static AreaShape shape = AreaShape.BOX;
    private static DisplayMode displayMode = DisplayMode.FACES;
    private static SphereDisplayMode sphereDisplayMode = SphereDisplayMode.BLOCKY;
    private static boolean overlayEnabled = true;
    private static boolean drafting = true;
    private static int editingExclusion = -1;
    private static final List<AreaZone> EXCLUSIONS = new ArrayList<>();

    private SelectionState() {
    }

    public static void updateLevel(@Nullable ClientLevel currentLevel, BlockPos currentPosition) {
        if (level != currentLevel) {
            level = currentLevel;
            first = currentPosition.immutable();
            second = currentPosition.immutable();
            lightingZone = null;
            shape = AreaShape.BOX;
            displayMode = DisplayMode.FACES;
            sphereDisplayMode = SphereDisplayMode.BLOCKY;
            overlayEnabled = true;
            drafting = true;
            editingExclusion = -1;
            EXCLUSIONS.clear();
        }
    }

    public static BlockPos first(BlockPos fallback) {
        if (first == null) {
            first = fallback.immutable();
        }
        return first;
    }

    public static BlockPos second(BlockPos fallback) {
        if (second == null) {
            second = fallback.immutable();
        }
        return second;
    }

    public static void setFirst(BlockPos pos) {
        first = pos.immutable();
        drafting = true;
    }

    public static void setSecond(BlockPos pos) {
        second = pos.immutable();
        drafting = true;
    }

    public static AreaShape shape() {
        return shape;
    }

    public static void setShape(AreaShape value) {
        shape = value;
        drafting = true;
    }

    public static DisplayMode displayMode() {
        return displayMode;
    }

    public static void setDisplayMode(DisplayMode value) {
        displayMode = value;
    }

    public static SphereDisplayMode sphereDisplayMode() {
        return sphereDisplayMode;
    }

    public static void setSphereDisplayMode(SphereDisplayMode value) {
        sphereDisplayMode = value;
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static boolean toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        return overlayEnabled;
    }

    public static boolean drafting() {
        return drafting;
    }

    public static AreaZone draft(BlockPos fallback) {
        return new AreaZone(shape, first(fallback), second(fallback));
    }

    public static @Nullable AreaZone lightingZone() {
        return lightingZone;
    }

    public static void setLightingZone(AreaZone zone) {
        lightingZone = zone;
        drafting = false;
        editingExclusion = -1;
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
        return true;
    }

    public static boolean removeLightingZone() {
        if (lightingZone == null) {
            return false;
        }
        lightingZone = null;
        return true;
    }

    public static List<AreaZone> exclusions() {
        // 返回副本，防止界面或渲染代码绕过数量限制直接修改内部列表。
        return List.copyOf(EXCLUSIONS);
    }

    public static boolean addExclusion(AreaZone exclusion) {
        if (editingExclusion >= 0 && editingExclusion < EXCLUSIONS.size()) {
            EXCLUSIONS.set(editingExclusion, exclusion);
            editingExclusion = -1;
            drafting = false;
            return true;
        }
        if (EXCLUSIONS.size() >= 32) {
            return false;
        }
        EXCLUSIONS.add(exclusion);
        drafting = false;
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
        return true;
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
