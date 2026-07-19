package dev.elric.autotorch.client;

import dev.elric.autotorch.network.ExclusionZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** 保存当前客户端世界中的临时选区；这些数据不会跨世界持久化。 */
public final class SelectionState {
    private static @Nullable ClientLevel level;
    private static @Nullable BlockPos first;
    private static @Nullable BlockPos second;
    private static final List<ExclusionZone> EXCLUSIONS = new ArrayList<>();

    private SelectionState() {
    }

    public static void updateLevel(@Nullable ClientLevel currentLevel, BlockPos currentPosition) {
        if (level != currentLevel) {
            level = currentLevel;
            first = currentPosition.immutable();
            second = currentPosition.immutable();
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
    }

    public static void setSecond(BlockPos pos) {
        second = pos.immutable();
    }

    public static List<ExclusionZone> exclusions() {
        // 返回副本，防止界面或渲染代码绕过数量限制直接修改内部列表。
        return List.copyOf(EXCLUSIONS);
    }

    public static boolean addExclusion(ExclusionZone exclusion) {
        if (EXCLUSIONS.size() >= 32) {
            return false;
        }
        EXCLUSIONS.add(exclusion);
        return true;
    }

    public static boolean removeLastExclusion() {
        if (EXCLUSIONS.isEmpty()) {
            return false;
        }
        EXCLUSIONS.removeLast();
        return true;
    }
}
