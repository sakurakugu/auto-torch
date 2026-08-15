# 项目概览

Auto Torch 网易版是一个面向 Minecraft 网易版的自动插火把与光照显示 Addon，使用独立分支 `mc/netease` 开发。主要功能包括纯客户端的附近自动插火把与光照/刷怪风险显示，以及需要客户端和服务端共同安装的区间自动照明任务。

- `behavior_pack_Zk0FPYhh`：行为包，包含 Python 脚本、实体定义和行为包清单。
- `resource_pack_apdk6ZGp`：资源包，包含纹理等客户端资源和资源包清单。
- `developer_mods`：网易服务端 Mod 目录。
- `work.mcscfg`：MC Studio 工程配置。
- `world_behavior_packs.json`、`world_resource_packs.json`：行为包和资源包关联配置。
- `.mcs`、`studio.json`：MC Studio 生成的本地状态，包含个人设置、账号或本机路径。

# 开发约定

1. Python2 找不到就在 C:\Software\Deps\Python\Python27，若要虚拟环境用 virtualenv。
2. 网易相关资源：
   - 文档：`..\other\netease_docs`
   - 编辑器：`C:\Software\Apps\Netease\MCStudio`
   - 默认下载位置：`C:\MCStudioDownload`
3. 注释使用中文。
