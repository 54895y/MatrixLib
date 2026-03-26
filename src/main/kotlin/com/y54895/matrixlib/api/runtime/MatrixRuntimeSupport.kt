package com.y54895.matrixlib.api.runtime

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.console.MatrixConsoleFact
import com.y54895.matrixlib.api.console.MatrixConsoleVisuals
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.command.CommandSender

open class MatrixTextChannel(
    protected val branding: MatrixBranding
) {

    fun color(text: String): String {
        return MatrixText.color(text)
    }

    fun prefixed(text: String): String {
        return MatrixText.prefixed(branding, text)
    }

    fun send(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        MatrixText.send(sender, branding, text, placeholders)
    }

    fun sendRaw(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        MatrixText.sendRaw(sender, text, placeholders)
    }

    fun raw(text: String, placeholders: Map<String, String> = emptyMap()): String {
        return MatrixText.raw(text, placeholders)
    }

    fun apply(template: String, placeholders: Map<String, String> = emptyMap()): String {
        return MatrixText.raw(MatrixText.apply(template, placeholders))
    }

    fun apply(lines: List<String>, placeholders: Map<String, String> = emptyMap()): List<String> {
        return lines.map { apply(it, placeholders) }
    }
}

open class MatrixConsoleChannel(
    protected val branding: MatrixBranding
) {

    fun renderBoot(
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        MatrixConsoleVisuals.renderBoot(
            branding = branding,
            headline = headline,
            details = details
        )
    }

    fun renderStage(
        stage: String,
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        MatrixConsoleVisuals.renderStage(
            branding = branding,
            stage = stage,
            headline = headline,
            details = details
        )
    }

    fun renderReady(
        version: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        MatrixConsoleVisuals.renderReady(
            branding = branding,
            version = version,
            details = details
        )
    }

    fun renderFailure(reason: String) {
        MatrixConsoleVisuals.renderFailure(branding, reason)
    }

    fun renderShutdown(details: List<MatrixConsoleFact> = emptyList()) {
        MatrixConsoleVisuals.renderShutdown(
            branding = branding,
            details = details
        )
    }
}
