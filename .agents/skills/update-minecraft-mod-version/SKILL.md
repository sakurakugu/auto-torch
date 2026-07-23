---
name: update-minecraft-mod-version
description: 将此 Minecraft mod 升级到 Fabric、Forge 和 Neo Forge 上请求的游戏版本。 在更改 Minecraft 或加载程序版本、迁移映射或加载程序 API、在游戏更新后修复跨加载程序构建或准备特定于版本的发布工件时使用。
---

# 升级 Minecraft Mod 版本

升级前完整阅读仓库根目录的 `AGENTS.md` 和 `../../../docs/Minecraft 版本升级指南.md`。遵守其中的 Java 路径、注释语言和验证要求。

## 工作流程

1.  检查 `git status --short --branch`，保留所有既有改动。
2.  搜索版本属性、插件版本、元数据模板、README 和 CI 中的旧版本引用。
3.  运行 `scripts/resolve_versions.ps1 -MinecraftVersion <目标版本>` 查询官方元数据。把结果视为候选值，并核对构件确实存在。
4.  先只更新 Minecraft、Fabric API/Loader、Forge、NeoForge 及其加载器范围。除非兼容性要求，不要顺手升级构建插件或模组版本。
5.  使用与 Gradle toolchain 一致的 JDK 编译，依据真实错误迁移 API。优先采用各加载器的正式事件和当前渲染模型。
6.  分别编译 `common`、`fabric`、`forge`、`neoforge`，避免一个下载故障掩盖其他模块结果。
7.  运行根项目完整 `build`，检查测试、发布 JAR 名称及 JAR 内嵌元数据。
8.  搜索旧版本和已删除 API 的残留引用，执行 `git diff --check` 并复核最终差异。
9.  涉及 GUI、输入、网络或渲染时，明确列出仍需执行的游戏内冒烟测试；未执行时不得声称运行时已验证，或让用户来验证。
10. 用户验证完成后，新增分支 `mc/<目标版本>`，提交改动并推送到远程仓库。若用户要求，打标签并发布构件。

## 约束条件

- 只使用 Mojang、Fabric、Forge 和 NeoForge 的官方清单或 Maven 仓库选择版本。
- 不因短暂 TLS、下载或缓存错误降级依赖。先串行重试，再定位具体损坏文件。
- 仅清理已确认损坏且属于本次任务的缓存文件，不删除整个 Gradle 缓存。
- 不通过放宽 Minecraft 版本范围掩盖未验证的兼容性。
- 不提交、打标签或发布，除非用户明确要求。
- 所有新增代码注释使用中文。

## 完成报告

报告目标版本、各加载器版本、主要 API 迁移、执行过的命令、测试数量、产物路径和未完成的运行时验证。
