package com.sakurakugu.autotorch.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;

/** 创建带固定线宽的覆盖层线框渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final Map<Integer, RenderType> LINES = new HashMap<>();
    private static final Map<Integer, RenderType> SEE_THROUGH_LINES = new HashMap<>();

    private AutoTorchRenderTypes() {
    }

    public static RenderType lines(float width, boolean seeThrough) {
        int widthKey = Math.round(width * 100.0F);
        Map<Integer, RenderType> renderTypes = seeThrough ? SEE_THROUGH_LINES : LINES;
        return renderTypes.computeIfAbsent(widthKey,
                ignored -> RenderTypeAccess.createLines(widthKey / 100.0F, seeThrough));
    }

    /** 在相机矩阵仍有效时提交选区线框的全部线宽批次。 */
    public static void endLineBatches(MultiBufferSource.BufferSource buffers) {
        for (RenderType renderType : LINES.values()) {
            buffers.endBatch(renderType);
        }
        for (RenderType renderType : SEE_THROUGH_LINES.values()) {
            buffers.endBatch(renderType);
        }
    }

    /** 旧版本将复合渲染状态及其预设声明为 protected，只能经由子类访问。 */
    private static final class RenderTypeAccess extends RenderType {
        private RenderTypeAccess() {
            super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                    1, false, false, () -> { }, () -> { });
        }

        private static RenderType createLines(float width, boolean seeThrough) {
            CompositeState.CompositeStateBuilder builder = CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(width)))
                    .setCullState(NO_CULL);
            if (seeThrough) {
                builder.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE);
            } else {
                // 与原版 RenderType.lines() 保持相同的图层、透明和输出状态，仅替换固定线宽。
                builder.setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE);
            }
            CompositeState state = builder.createCompositeState(false);
            try {
                Method create = findCreateMethod();
                return (RenderType) create.invoke(null,
                        seeThrough ? "autotorch_light_overlay_see_through_lines" : "autotorch_light_overlay_lines",
                        DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
                        state);
            } catch (ReflectiveOperationException | SecurityException exception) {
                // 某些加载器会在 RenderType 加载后才初始化 Mixin，反射失败时退回原版线框，避免客户端崩溃。
                System.err.println("Auto Torch 无法创建固定线宽渲染类型，将使用普通线框: " + exception);
                return RenderType.lines();
            }
        }

        private static Method findCreateMethod() throws NoSuchMethodException {
            for (Method method : RenderType.class.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                // 1.21.1 中工厂方法的声明返回类型为 RenderType 的子类 CompositeRenderType。
                if (Modifier.isStatic(method.getModifiers())
                        && RenderType.class.isAssignableFrom(method.getReturnType())
                        && parameters.length == 7 && parameters[0] == String.class
                        && parameters[1] == VertexFormat.class && parameters[2] == VertexFormat.Mode.class
                        && parameters[3] == int.class && parameters[4] == boolean.class
                        && parameters[5] == boolean.class && parameters[6] == CompositeState.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new NoSuchMethodException("RenderType(String, VertexFormat, Mode, int, boolean, boolean, CompositeState) factory");
        }
    }
}
