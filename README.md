> [!CAUTION]
> 本项目由 Codex 协作维护，如发现问题请直接提交 Issue。

# MatrixLib

**Keywords:** Minecraft plugin library, Paper plugin library, Folia plugin library, Bukkit plugin API, Spigot plugin API, TabooLib, Kotlin plugin library, GUI framework, shared runtime, Matrix plugin framework

MatrixLib is a shared Minecraft plugin runtime and dependency library for MatrixShop, MatrixAuth and MatrixCook. It targets Paper, Bukkit, Spigot and Folia servers, and provides shared branding, console, menu, YAML, compat and action APIs.

**中文关键词：** Minecraft 插件前置, Paper 插件前置, Folia 插件前置, Bukkit 插件 API, Spigot 插件 API, TabooLib 前置, Kotlin 插件库, GUI 菜单框架, 共享运行时, Matrix 插件框架

## Discoverability

- English: Minecraft plugin library, Paper plugin framework, Folia plugin framework, Bukkit shared runtime, TabooLib shared API, Kotlin Minecraft library
- 中文: Minecraft 插件前置, 服务器插件前置, 中文控制台前置, 共享菜单前置, 兼容层前置, GUI 菜单前置

## What MatrixLib Provides

- Shared `branding / console / text / yaml` APIs
- Shared `menu / compat / action` APIs
- Unified Chinese terminal banner and lifecycle output
- Shared Bukkit / Folia compatibility bridge
- Shared runtime for MatrixShop, MatrixAuth and MatrixCook

## Downstream Build Integration

Dependency coordinate:

- `com.y54895.matrixlib:matrixlib-api:1.0.1`

Downstream projects support two build modes:

1. Local linked mode  
   If the workspace contains a local `MatrixLib` directory, downstream projects use `includeBuild` for direct local source linkage.
2. GitHub source mode  
   If there is no local `MatrixLib` directory, Gradle resolves MatrixLib directly from GitHub through `sourceControl`.

Reference configuration:

```kotlin
val matrixLibModule = "com.y54895.matrixlib:matrixlib-api"

sourceControl {
    gitRepository(uri("https://github.com/54895y/MatrixLib.git")) {
        producesModule(matrixLibModule)
    }
}
```

## Current Public Release

- First public release: `1.0.1`

## Links

- GitHub Repo: [https://github.com/54895y/MatrixLib](https://github.com/54895y/MatrixLib)
- Issues: [https://github.com/54895y/MatrixLib/issues](https://github.com/54895y/MatrixLib/issues)
- Releases: [https://github.com/54895y/MatrixLib/releases](https://github.com/54895y/MatrixLib/releases)
- Related docs: `MatrixDevDocs`
