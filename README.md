# MatrixLib

`MatrixLib` 是 Matrix 系列插件的共享前置插件，用于统一品牌风格、控制台输出、文本能力、YAML 读取、兼容层与共享菜单能力。

它负责承载 `MatrixShop`、`MatrixAuth`、`MatrixCook` 中重复、稳定、可复用的基础设施，减少重复造轮子，并统一后续维护入口。

## 核心能力

- 共享 `branding / console / text / yaml` 能力
- 共享 `menu / compat / action` API
- 统一中文终端 banner 与生命周期输出
- 统一 Bukkit / Folia 兼容桥接

## 下游构建方式

下游仓库统一依赖坐标：

- `com.y54895.matrixlib:matrixlib-api:1.0.1`

当前支持两种构建模式：

1. 本地联动模式  
   如果工作区存在本地 `MatrixLib` 目录，则优先通过 `includeBuild` 直接联动本地源码。
2. GitHub 源码模式  
   如果本地没有 `MatrixLib` 目录，则 Gradle 会通过 `sourceControl` 从 GitHub 拉取 `MatrixLib` 源码参与构建。

参考配置：

```kotlin
val matrixLibModule = "com.y54895.matrixlib:matrixlib-api"

sourceControl {
    gitRepository(uri("https://github.com/54895y/MatrixLib.git")) {
        producesModule(matrixLibModule)
    }
}
```

## 当前发布

- 首个公开发布版本：`1.0.1`
- GitHub Repo: [https://github.com/54895y/MatrixLib](https://github.com/54895y/MatrixLib)

## 文档

- 开发文档由 `MatrixDevDocs` 仓库统一维护
- Wiki 入口以仓库主页和文档仓库为准
