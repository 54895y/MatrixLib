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
        runtimeName = "Matrix shared service bus"
    )

    override fun onLoad() {
        MatrixConsoleVisuals.renderBoot(
            branding = branding,
            headline = "Loading shared Matrix runtime",
            details = listOf(
                MatrixConsoleFact("Exports", "branding / text / yaml / console"),
                MatrixConsoleFact("Usage", "Required by MatrixShop, MatrixAuth, MatrixCook")
            )
        )
    }

    override fun onEnable() {
        MatrixConsoleVisuals.renderReady(
            branding = branding,
            version = pluginVersion,
            details = listOf(
                MatrixConsoleFact("API", "MatrixText, MatrixYamlBundle"),
                MatrixConsoleFact("Runtime", "Console visuals and resource bridge")
            )
        )
    }

    override fun onDisable() {
        MatrixConsoleVisuals.renderShutdown(
            branding = branding,
            details = listOf(
                MatrixConsoleFact("State", "Shared runtime offline")
            )
        )
    }
}
