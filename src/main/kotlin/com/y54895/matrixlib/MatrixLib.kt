package com.y54895.matrixlib

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.console.MatrixConsoleFact
import com.y54895.matrixlib.api.console.MatrixConsoleVisuals
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.pluginVersion

object MatrixLib : Plugin() {

    val branding = MatrixBranding(
        displayName = "MatrixLib",
        rootCommand = "/matrixlib",
        runtimeName = "MatrixLib 共享运行时"
    )

    override fun onLoad() {
        MatrixConsoleVisuals.renderBoot(
            branding = branding,
            headline = "正在装载共享接口与运行时桥接",
            details = listOf(
                MatrixConsoleFact("导出接口", "action / menu / compat / text / yaml / console"),
                MatrixConsoleFact("服务对象", "MatrixShop / MatrixAuth / MatrixCook")
            )
        )
    }

    override fun onEnable() {
        MatrixConsoleVisuals.renderReady(
            branding = branding,
            version = pluginVersion,
            details = listOf(
                MatrixConsoleFact("共享接口", "action / menu / compat / text / yaml / console"),
                MatrixConsoleFact("运行状态", "控制台品牌 / 资源桥接 / 通用适配")
            )
        )
    }

    override fun onDisable() {
        MatrixConsoleVisuals.renderShutdown(
            branding = branding,
            details = listOf(
                MatrixConsoleFact("运行状态", "共享运行时已离线")
            )
        )
    }
}
