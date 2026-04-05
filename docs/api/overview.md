Module MatrixLib API

MatrixLib API 面向 Matrix 系列插件开发者，当前提供：

- 统一品牌与控制台输出
- 文本与 YAML bundle
- 菜单与动作执行
- Bukkit / Folia 兼容探测
- 共享货币与结算
- 全息兼容桥接
- bStats 注册封装
- GitHub Releases 更新检查与审批下载

## Code Tree

```text
src/main/kotlin/com/y54895/matrixlib/
├─ MatrixLib.kt
├─ api/
│  ├─ action/ActionApi.kt
│  ├─ brand/MatrixBranding.kt
│  ├─ compat/CompatApi.kt
│  ├─ console/MatrixConsoleVisuals.kt
│  ├─ economy/MatrixEconomy.kt
│  ├─ hologram/
│  │  ├─ MatrixHologramRequest.kt
│  │  ├─ MatrixHolograms.kt
│  │  └─ internal/
│  ├─ menu/MenuApi.kt
│  ├─ metrics/MatrixBStats.kt
│  ├─ resource/MatrixResourceFiles.kt
│  ├─ text/
│  │  ├─ MatrixText.kt
│  │  └─ MatrixYamlBundle.kt
│  └─ update/MatrixPluginUpdates.kt
├─ command/MatrixLibCommands.kt
└─ metrics/BStatsMetrics.kt
```

Public API recommendation:

- Prefer types under `api/`
- Treat `api/hologram/internal/` as provider internals
- Treat `command/` and `metrics/` as MatrixLib runtime wiring, not downstream extension points

推荐入口类型：

- `MatrixBranding`
- `MatrixText`
- `MatrixYamlBundle`
- `MenuLoader`
- `MenuRenderer`
- `MatrixEconomy`
- `MatrixHolograms`
- `MatrixBStats`
- `MatrixPluginUpdates`
