# Minecraft 版本升级指南

本文适用于本仓库的 `common`、`fabric`、`forge`、`neoforge` 多加载器结构。目标是完成可复现的版本迁移。

## 1. 升级前检查

1. 阅读根目录 `AGENTS.md`。
2. 执行 `git status --short --branch`，记录并保留已有改动。
3. 检查 `gradle.properties`、根 `build.gradle`、各子模块构建文件、模组元数据、README 和 CI。
4. 确认 `%JAVA_HOME%` 下存在与 `JavaLanguageVersion` 一致的 JDK。

不要在同一次迁移中顺带升级无关插件、重构代码或修改 `mod_version`，除非兼容性或发布计划明确要求。

> 比如有两个需求，新增一个功能和升级到新的 Minecraft 版本，应该分两次提交。

## 2. 查询官方版本

在仓库根目录运行：

```powershell
.\.agents\skills\update-minecraft-mod-version\scripts\resolve_versions.ps1 -MinecraftVersion <目标版本>
```

脚本查询以下官方来源：

| 组件              | 官方来源                                                          |
| ----------------- | ----------------------------------------------------------------- |
| Minecraft         | `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` |
| Fabric Loader/API | `https://maven.fabricmc.net/`                                     |
| Forge             | `https://maven.minecraftforge.net/`                               |
| NeoForge          | `https://maven.neoforged.net/releases/`                           |

脚本仅列出候选值。采用版本前还要确认对应 POM/JAR 存在，并留意预览版、Beta 版和加载器最低版本范围。

## 3. 更新构建属性

通常修改 `gradle.properties` 中的：

```properties
minecraft_version=<目标版本>
neo_version=<匹配的 NeoForge 版本>
forge_version=<匹配的 Forge 版本>
forge_loader_version_range=[<Forge 主加载器版本>,)
fabric_loader_version=<兼容的 Fabric Loader>
fabric_api_version=<匹配目标 Minecraft 的 Fabric API>
```

只有出现插件兼容问题时才更新根 `build.gradle` 中的 Fabric Loom、ForgeGradle 或 ModDevGradle。更新后检查 `minecraft_version_range` 和三端生成的元数据，不要只看 Gradle 是否解析成功。

同步 README、CI、发布脚本中的明确版本文字。资源包格式应从目标 Minecraft 源码或官方资料确认；现有范围已覆盖时不要制造无意义改动。

> 注: 当前项目版本 `mod_version` 不必随 Minecraft 版本升级而改变，除非兼容性或发布计划明确要求。

## 4. 分阶段编译和迁移

先设置 Java，再分别编译，确保错误归属清楚：

```powershell
$env:JAVA_HOME='C:\Software\Deps\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :common:compileJava :fabric:compileJava --console=plain
.\gradlew.bat :forge:compileJava --console=plain
.\gradlew.bat :neoforge:compileJava --console=plain
```

或

```powershell
.\tools\1.一键启动mc脚本.ps1 --build
```

按编译错误逐项迁移。
跨加载器公共代码只保留三端都存在的 Minecraft API。加载器专属事件和生命周期处理应留在对应子模块。

## 5. 下载和缓存故障

首次升级可能需要反编译并重编译 Minecraft，耗时数分钟是正常现象。日志中的 `Cache miss!` 若标明仅供信息参考，不等同于构建失败。

遇到 TLS EOF、握手中断或下载超时时：

1. 确认官方 URL 可访问。
2. 使用 `--no-parallel --max-workers=1` 串行重试。
3. 避免同时运行两个生成同一 Forge/NeoForge 缓存的构建。
4. 若出现 `zip END header not found`，定位并校验最近下载的具体 JAR/ZIP，只清理确认损坏的文件。
5. 不要通过选择旧依赖来掩盖临时网络问题。

## 6. 完整验证

依赖和源码迁移完成后运行：

```powershell
.\gradlew.bat clean build --no-parallel --max-workers=1 --console=plain
git diff --check
rg -n '<旧 Minecraft 版本>' -g '!build/**' -g '!.gradle/**'
```

检查以下结果：

- `common` 单元测试全部通过。
- Fabric、Forge、NeoForge 均成功编译和打包。
- 根 `build/` 中存在三端目标版本 JAR。
- `fabric.mod.json`、`mods.toml`、`neoforge.mods.toml` 内嵌版本正确。
- 没有旧版本、已删除 API 或临时日志残留。

`build` 成功不能证明渲染和交互行为正确。涉及相关代码时，发布前分别启动三端客户端并验证：

- 主界面和世界能够进入，无加载器错误。
- `G` 配置界面及子界面可打开、关闭和返回。
- `F7` 光照覆盖层、选区线框/面和深度关系正确。
- 木斧选区、附近自动插火把和服务端区域照明正常。
- 单人和多人网络 Payload 没有注册或编解码错误。

## 7. 完成标准

升级报告至少列出：目标 Minecraft 版本、三套加载器/API 版本、主要兼容修改、构建和测试结果、JAR 路径，以及尚未执行的游戏内测试。未运行客户端冒烟测试时，应明确写出该限制。

> 最后记得新增分支 `mc/<目标版本>`，提交改动并推送到远程仓库。
