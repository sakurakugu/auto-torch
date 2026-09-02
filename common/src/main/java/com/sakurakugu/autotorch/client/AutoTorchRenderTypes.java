package com.sakurakugu.autotorch.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final RenderType SEE_THROUGH_LINES = RenderTypeAccess.createSeeThroughLines();

    private AutoTorchRenderTypes() {
    }

    public static RenderType seeThroughLines() {
        return SEE_THROUGH_LINES;
    }

    /** 旧版本将复合渲染状态及其预设声明为 protected，只能经由子类访问。 */
    private static final class RenderTypeAccess extends RenderType {
        private RenderTypeAccess() {
            super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                    1, false, false, () -> { }, () -> { });
        }

        private static RenderType createSeeThroughLines() {
            CompositeState state = CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
            try {
                Method create = findCreateMethod();
                return (RenderType) create.invoke(null, "autotorch_light_overlay_see_through_lines",
                        DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
                        state);
            } catch (ReflectiveOperationException | SecurityException exception) {
                // 某些加载器会在 RenderType 加载后才初始化 Mixin，反射失败时退回原版线框，避免客户端崩溃。
                System.err.println("Auto Torch 无法创建透视渲染类型，将使用普通线框: " + exception);
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
