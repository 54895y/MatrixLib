> [!CAUTION]
> 本项目由 Codex 协作维护，如发现问题请直接提交 Issue。

# MatrixLib

`MatrixLib` 是 Matrix 系列插件的共享前置插件，用于统一品牌风格、控制台输出、文本能力、YAML 读取、兼容层、共享菜单能力、共享货币模块，以及共享 `bStats` 遥测封装。

| 文档 | Release Notes | Releases | Issues |
| --- | --- | --- | --- |
| [Docs](https://54895y.github.io/docs/matrixlib) | [1.4.0](https://54895y.github.io/docs/matrixlib/release-notes-1-4-0) | [GitHub Releases](https://github.com/54895y/MatrixLib/releases) | [GitHub Issues](https://github.com/54895y/MatrixLib/issues) |

## 当前发布

- 当前版本：`1.4.0`
- 依赖坐标：`com.y54895.matrixlib:matrixlib-api:1.4.0`
- 服务对象：`MatrixShop / MatrixAuth / MatrixCook / MatrixStorage`
- 支持核心：`Paper / Bukkit / Spigot / Folia`

## 核心能力

- 共享 `branding / console / text / yaml` API
- 共享 `menu / compat / action / economy` API
- 统一中文控制台横幅与生命周期输出
- 共享 `Economy/currency.yml` 货币定义与运行时同步
- 共享 Bukkit / Folia 兼容桥接
- 共享 `bStats` 封装 API `MatrixBStats`
- 为下游插件统一承接 `bStats` shaded 依赖与图表注册
- 共享 GitHub Releases 更新检查、审批下载与 `plugins/update/` 投递

## 下游集成

下游项目当前统一引用：

```kotlin
dependencies {
    compileOnly("com.y54895.matrixlib:matrixlib-api:1.4.0")
}
```

如果工作区里存在本地 `MatrixLib` 目录，下游项目会通过 `includeBuild` 直接链接源码；否则会通过 GitHub `sourceControl` 拉取。

## 自动更新

当前推荐方案：

- 用 GitHub Releases latest API 检查新版本
- 默认只提示，不直接覆盖当前运行中的 jar
- 由管理员审批后下载到 `plugins/update/`
- 服务器重启后由 Bukkit / Spigot 更新目录机制自动替换

配置文件：

```text
plugins/MatrixLib/Update/config.yml
```

管理命令：

```text
/matrixlib update list
/matrixlib update check [all|插件名]
/matrixlib update notes <插件名>
/matrixlib update approve <插件名|all>
```

默认启用审批模式：

- `require-approval: true`

## bStats 遥测

- Plugin ID: `30557`
- 共享遥测入口：`com.y54895.matrixlib.api.metrics.MatrixBStats`

当前图表：

| Chart ID | 类型 | 含义 |
| --- | --- | --- |
| `configured_currency_count` | SingleLineChart | 当前已配置货币数量 |
| `currency_modes` | AdvancedPie | 货币模式分布 |
| `currency_setup` | SimplePie | 单货币 / 多货币部署类型 |

预留图表位：

| 预留 Chart ID | 计划用途 |
| --- | --- |
| `downstream_plugin_count` | 统计当前安装的 Matrix 系列下游插件数量 |
| `resource_sync_targets` | 统计共享资源同步目标分布 |
| `compat_bridge_modes` | 统计兼容桥接启用情况 |

## 相关链接

- Matrix 插件总文档入口：[https://54895y.github.io/docs/plugins](https://54895y.github.io/docs/plugins)
- Matrix 系列配置 Skill 文档：[https://54895y.github.io/docs/matrix-agent-skills/matrix-series-config](https://54895y.github.io/docs/matrix-agent-skills/matrix-series-config)
- MatrixAgentSkills 仓库：[https://github.com/54895y/MatrixAgentSkills](https://github.com/54895y/MatrixAgentSkills)
