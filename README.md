# MatrixLib

`MatrixLib` 是 Matrix 系列插件的共享前置插件，用于统一品牌风格、控制台输出、文本能力、YAML 读取、兼容层和共享菜单能力。

它的目标是把 `MatrixShop`、`MatrixAuth`、`MatrixCook` 中重复、稳定、可复用的基础设施沉淀出来，减少重复实现，统一后续维护入口。

## 提供能力

- 共享 `branding / console / text / yaml` 能力
- 共享 `menu / compat / action` API
- 统一终端 banner 与中文生命周期输出
- 统一 Bukkit / Folia 兼容桥接

## GitHub 源码构建

`MatrixLib` 现在作为公开 GitHub 仓库提供给下游插件直接参与构建。

下游仓库的 `settings.gradle.kts` 已支持两种模式：

1. 本地开发模式  
   如果工作区存在本地 `MatrixLib` 目录，则优先通过 `includeBuild` 直接联动本地源码。
2. GitHub 构建模式  
   如果本地没有 `MatrixLib` 目录，则 Gradle 会通过 `sourceControl` 从 GitHub 拉取 `MatrixLib` 源码参与构建。

GitHub 仓库地址：

- Repo: [https://github.com/54895y/MatrixLib](https://github.com/54895y/MatrixLib)

## Wiki

- 项目 Wiki: [https://github.com/54895y/MatrixLib/wiki](https://github.com/54895y/MatrixLib/wiki)
