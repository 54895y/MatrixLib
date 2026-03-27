package com.y54895.matrixlib.api.runtime

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.compat.FoliaUtil
import com.y54895.matrixlib.api.compat.SchedulerBridge
import com.y54895.matrixlib.api.console.MatrixConsoleFact
import com.y54895.matrixlib.api.console.MatrixConsoleVisuals
import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import com.y54895.matrixlib.api.text.MatrixText
import com.y54895.matrixlib.api.text.MatrixYamlBundle
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.function.Supplier

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

open class MatrixPluginRuntime(
    private val pluginProvider: Supplier<JavaPlugin>,
    protected val runtimeBranding: MatrixBranding
) {

    private val text = MatrixTextChannel(runtimeBranding)
    private val console = MatrixConsoleChannel(runtimeBranding)

    protected fun plugin(): JavaPlugin {
        return pluginProvider.get()
    }

    fun color(text: String): String {
        return this.text.color(text)
    }

    fun prefixed(text: String): String {
        return this.text.prefixed(text)
    }

    fun send(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        this.text.send(sender, text, placeholders)
    }

    fun sendRaw(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        this.text.sendRaw(sender, text, placeholders)
    }

    fun raw(text: String, placeholders: Map<String, String> = emptyMap()): String {
        return this.text.raw(text, placeholders)
    }

    fun apply(template: String, placeholders: Map<String, String> = emptyMap()): String {
        return this.text.apply(template, placeholders)
    }

    fun apply(lines: List<String>, placeholders: Map<String, String> = emptyMap()): List<String> {
        return this.text.apply(lines, placeholders)
    }

    fun renderBoot(headline: String, details: List<MatrixConsoleFact> = emptyList()) {
        console.renderBoot(headline, details)
    }

    fun renderStage(stage: String, headline: String, details: List<MatrixConsoleFact> = emptyList()) {
        console.renderStage(stage, headline, details)
    }

    fun renderReady(version: String, details: List<MatrixConsoleFact> = emptyList()) {
        console.renderReady(version, details)
    }

    fun renderFailure(reason: String) {
        console.renderFailure(reason)
    }

    fun renderShutdown(details: List<MatrixConsoleFact> = emptyList()) {
        console.renderShutdown(details)
    }

    fun dataFile(path: String): File {
        return MatrixResourceFiles.dataFile(plugin(), path)
    }

    fun saveResourceIfAbsent(path: String) {
        MatrixResourceFiles.saveResourceIfAbsent(plugin(), path)
    }

    fun createYamlBundle(resourcePath: String): MatrixYamlBundle {
        return MatrixYamlBundle(
            plugin = plugin(),
            branding = runtimeBranding,
            resourcePath = resourcePath
        )
    }

    fun runLater(delayTicks: Long, task: Runnable) {
        SchedulerBridge.runLater(plugin(), delayTicks, task)
    }

    fun runAsync(task: Runnable) {
        SchedulerBridge.runAsync(plugin(), task)
    }

    fun runPlayerTaskLater(player: Player, delayTicks: Long, task: Runnable) {
        SchedulerBridge.runPlayerTaskLater(plugin(), player, delayTicks, task)
    }

    val isFolia: Boolean
        get() = FoliaUtil.isFolia

    fun runFoliaLater(delayTicks: Long, task: Runnable): FoliaUtil.TaskHandle {
        return FoliaUtil.runLater(plugin(), delayTicks, task)
    }

    fun runFoliaRepeating(initialDelay: Long, period: Long, task: Runnable): FoliaUtil.TaskHandle {
        return FoliaUtil.runRepeating(plugin(), initialDelay, period, task)
    }

    fun runAtLocation(location: Location, task: Runnable) {
        FoliaUtil.runAtLocation(plugin(), location, task)
    }

    fun runAtEntity(entity: Entity, task: Runnable) {
        FoliaUtil.runAtEntity(plugin(), entity, task)
    }
}
