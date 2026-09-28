# 网易版 Shader 移植备忘

## 使用链路

网易版资源包中的 Shader 不会因为放入 `shaders/` 目录就自动生效，需要经过以下引用链：

```text
shaders/*.vertex、*.fragment
        ↓
materials/*.material
        ↓
materials/common.json、fancy.json 或 sad.json
        ↓
实体、渲染控制器或骨骼模型引用材质
```

## 资源包目录

以当前网易资源包为例，文件放在：

```text
netease/resource_pack_apdk6ZGp/
├─ materials/
│  ├─ common.json
│  └─ entity.material
└─ shaders/
   ├─ my_entity.vertex
   └─ my_entity.fragment
```

`materials/common.json` 需要列出要加载的材质文件：

```json
[
  { "path": "materials/entity.material" }
]
```

`materials/entity.material` 中定义材质并指定 Shader：

```json
{
  "materials": {
    "version": "1.0.0",
    "my_entity:entity": {
      "vertexShader": "shaders/my_entity.vertex",
      "fragmentShader": "shaders/my_entity.fragment"
    }
  }
}
```

客户端实体定义中引用材质：

```json
"materials": {
  "default": "my_entity"
}
```

## 编写注意事项

- 网易版这套接口使用 GLSL，建议从目标版本开发包中的 `entity.vertex` 和 `entity.fragment` 复制后修改。
- 自定义 Shader 必须保持与材质的顶点字段、宏和 `uniform` 接口一致，不能直接套用 Java 版 OptiFine/Iris 的 `.vsh/.fsh` 文件。
- 公共 GLSL 头文件位于开发包的 `data/shaders/glsl`，可通过 `#include` 引用。
- 材质文件只有被 `common.json`、`fancy.json` 或 `sad.json` 列出后才会加载。
- 复杂效果应分别为 `fancy` 和 `sad` 画质准备高、低消耗版本。
- 骨骼模型可在 `models/netease_models.json` 中指定 `material_cpu` 和 `material`。
- 不要直接修改 `C:\MCStudioDownload` 下的原版文件；那里只作为目标版本的接口和 Shader 参考。

## 兼容性判断

移植前先确认开源项目的目标格式：

1. 网易 GLSL 材质 Shader：可以按本备忘录的资源包流程移植。
2. Java 版 OptiFine/Iris Shader：不能直接放入网易资源包，需要重新适配材质接口。
3. Bedrock RenderDragon/Vibrant Visuals Shader：接口和编译产物不同，不能直接按旧 GLSL 材质流程使用。
4. `.materialbin` 等编译文件：通常与游戏版本和平台相关，不能默认跨版本使用。

## 参考文档

- `other/netease_docs/mcguide/16-美术/7-材质与着色器/Shader使用简介.md`
- `other/netease_docs/mcguide/16-美术/7-材质与着色器/材质配置说明.md`
- `other/netease_docs/mcguide/20-玩法开发/15-自定义游戏内容/3-自定义生物/01-自定义基础生物.md`
- `other/netease_docs/mcguide/20-玩法开发/15-自定义游戏内容/9-骨骼模型自定义材质.md`

