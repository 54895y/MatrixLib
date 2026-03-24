package com.y54895.matrixlib.api.console

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

data class MatrixConsoleFact(
    val label: String,
    val value: String
)

object MatrixConsoleVisuals {

    private const val BORDER = "&8&m+------------------------------------------------------------------+"

    private val logo = listOf(
        "&3 __  __       _        _        __  __       _        _      ",
        "&b|  \\/  | __ _| |_ _ __(_)_  __ |  \\/  | __ _| |_ _ __(_)_  __",
        "&b| |\\/| |/ _` | __| '__| \\ \\/ / | |\\/| |/ _` | __| '__| \\ \\/ /",
        "&9| |  | | (_| | |_| |  | |>  <  | |  | | (_| | |_| |  | |>  < ",
        "&9|_|  |_|\\__,_|\\__|_|  |_/_/\\_\\ |_|  |_|\\__,_|\\__|_|  |_/_/\\_\\"
    )

    fun renderBoot(
        branding: MatrixBranding,
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "LOAD",
            headline = headline,
            details = defaultDetails(branding) + details,
            includeLogo = true,
            leadingBlank = true
        )
    }

    fun renderStage(
        branding: MatrixBranding,
        stage: String,
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = stage,
            headline = headline,
            details = details
        )
    }

    fun renderReady(
        branding: MatrixBranding,
        version: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "READY",
            headline = "${branding.runtimeName} online",
            details = listOf(MatrixConsoleFact("Version", version)) + defaultDetails(branding) + details,
            trailingBlank = true
        )
    }

    fun renderFailure(
        branding: MatrixBranding,
        reason: String
    ) {
        renderBlock(
            branding = branding,
            stage = "FAIL",
            headline = "${branding.runtimeName} startup aborted",
            details = listOf(MatrixConsoleFact("Reason", reason))
        )
    }

    fun renderShutdown(
        branding: MatrixBranding,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "STOP",
            headline = "${branding.runtimeName} shutting down",
            details = details,
            leadingBlank = true
        )
    }

    private fun renderBlock(
        branding: MatrixBranding,
        stage: String,
        headline: String,
        details: List<MatrixConsoleFact>,
        includeLogo: Boolean = false,
        leadingBlank: Boolean = false,
        trailingBlank: Boolean = false
    ) {
        val lines = mutableListOf<String>()
        if (leadingBlank) {
            lines += ""
        }
        lines += BORDER
        if (includeLogo) {
            lines += logo
            lines += stageLine(branding, "INFO", branding.runtimeName)
            lines += BORDER
        }
        lines += stageLine(branding, stage, headline)
        details.forEach { lines += detailLine(branding, it) }
        lines += BORDER
        if (trailingBlank) {
            lines += ""
        }
        send(lines)
    }

    private fun defaultDetails(branding: MatrixBranding): List<MatrixConsoleFact> {
        val details = mutableListOf(MatrixConsoleFact("Command", branding.rootCommand))
        branding.adminCommand?.let { details += MatrixConsoleFact("Admin", it) }
        return details
    }

    private fun stageLine(branding: MatrixBranding, stage: String, headline: String): String {
        val color = when (stage.uppercase()) {
            "LOAD", "INIT" -> "&e"
            "READY" -> "&a"
            "FAIL", "STOP" -> "&c"
            else -> branding.accentColor
        }
        return "${prefix(branding)}$color[$stage] &f$headline"
    }

    private fun detailLine(branding: MatrixBranding, fact: MatrixConsoleFact): String {
        return "${prefix(branding)}&f${fact.label} &8>> ${branding.accentColor}${fact.value}"
    }

    private fun prefix(branding: MatrixBranding): String {
        return "&8[${branding.accentColor}${branding.displayName}&8] "
    }

    private fun send(lines: List<String>) {
        lines.forEach { line ->
            val rendered = MatrixText.color(line)
            runCatching {
                Bukkit.getConsoleSender().sendMessage(rendered)
            }.getOrElse {
                info(rendered)
            }
        }
    }
}
