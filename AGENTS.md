# 项目概览

Auto Torch 是一个面向 Minecraft 的自动插火把与光照显示模组，当前主分支适配 Minecraft 最新版，并同时支持 Fabric、Forge 和 NeoForge。主要功能包括纯客户端的附近自动插火把与光照/刷怪风险显示，以及需要客户端和服务端共同安装的区间自动照明任务。

项目采用 Gradle 多模块结构：

- `common`：共享业务逻辑、客户端界面与渲染、选区与照明任务、网络载荷、配置定义及主要单元测试。
- `fabric`：Fabric 平台入口、网络注册和基于 Night Config 的 TOML 配置适配。
- `forge`：Forge 平台入口、网络与配置适配，以及模组元数据模板。
- `neoforge`：NeoForge 平台入口、网络与配置适配，以及模组元数据模板。
- `tests`：Python 测试脚本。
- `docs`：使用说明、版本升级和发布文档及 README 图片资源。
- `tools`：开发辅助脚本，包括 Windows 开发客户端启动脚本和图标生成脚本。

目前支持 1.7.10~最新版，分支名称是 mc/<Minecraft 版本号>。

# 开发约定

1. Java 找不到就在 C:\Software\Deps\Java\
2. 注释用中文
3. Gradle 首次编译或解析依赖可能耗时较长，执行编译命令时超时时间至少设置为 120 秒
