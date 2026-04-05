package com.y54895.matrixlib.api.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Static registration descriptor for one Matrix plugin managed by the shared updater.
 */
data class MatrixManagedPlugin(
    val plugin: JavaPlugin,
    val displayName: String,
    val repoOwner: String,
    val repoName: String,
    val assetNamePattern: Regex,
    val commandHint: String
) {

    val key: String
        get() = displayName.lowercase(Locale.ROOT)

    val repoFullName: String
        get() = "$repoOwner/$repoName"

    fun installedVersion(): String {
        return plugin.description.version
    }

    fun installedJarFile(): File? {
        return runCatching {
            File(plugin.javaClass.protectionDomain.codeSource.location.toURI())
        }.getOrNull()?.takeIf(File::exists)
    }
}

/**
 * Release asset metadata selected from a GitHub release.
 */
data class MatrixReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long
)

/**
 * Reduced latest-release payload used by the Matrix updater.
 */
data class MatrixReleaseInfo(
    val version: String,
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val publishedAt: String,
    val asset: MatrixReleaseAsset
)

/**
 * Pending update candidate discovered by a latest-release check.
 */
data class MatrixUpdateCandidate(
    val managedPlugin: MatrixManagedPlugin,
    val installedVersion: String,
    val release: MatrixReleaseInfo
)

/**
 * Shared updater configuration loaded from `plugins/MatrixLib/Update/config.yml`.
 */
data class MatrixUpdateSettings(
    val enabled: Boolean,
    val checkOnStartup: Boolean,
    val notifyConsole: Boolean,
    val notifyOpsOnJoin: Boolean,
    val requireApproval: Boolean,
    val checkIntervalMinutes: Long,
    val apiBase: String,
    val userAgent: String,
    val token: String,
    val timeoutSeconds: Int
)

/**
 * Shared GitHub Releases updater used by the Matrix plugin series.
 *
 * The updater only downloads jars into `plugins/update/`. Replacement is left to
 * the server restart flow so the currently running jar is never overwritten.
 */
object MatrixPluginUpdates {

    private val registrations = ConcurrentHashMap<String, MatrixManagedPlugin>()
    private val candidates = ConcurrentHashMap<String, MatrixUpdateCandidate>()
    private val notifiedOps = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    private var bootstrapped = false

    @Volatile
    private var repeatingTaskId = -1

    lateinit var settings: MatrixUpdateSettings
        private set

    lateinit var branding: MatrixBranding
        private set

    /**
     * Initialize updater state and listeners.
     */
    fun bootstrap(branding: MatrixBranding) {
        if (bootstrapped) {
            return
        }
        this.branding = branding
        settings = loadSettings()
        registerListener()
        schedulePeriodicChecks()
        bootstrapped = true
    }

    /**
     * Register one managed plugin in the shared updater.
     */
    fun register(
        plugin: JavaPlugin,
        displayName: String,
        repoOwner: String,
        repoName: String,
        assetNamePattern: String,
        commandHint: String
    ) {
        if (!bootstrapped) {
            bootstrap(
                MatrixBranding(
                    displayName = "MatrixLib",
                    rootCommand = "/matrixlib",
                    runtimeName = "MatrixLib 共享运行时"
                )
            )
        }
        val managed = MatrixManagedPlugin(
            plugin = plugin,
            displayName = displayName,
            repoOwner = repoOwner,
            repoName = repoName,
            assetNamePattern = Regex(assetNamePattern, RegexOption.IGNORE_CASE),
            commandHint = commandHint
        )
        registrations[managed.key] = managed
        if (settings.enabled && settings.checkOnStartup) {
            checkAsync(managed)
        }
    }

    /**
     * List all pending update candidates.
     */
    fun listCandidates(): List<MatrixUpdateCandidate> {
        return candidates.values.sortedBy { it.managedPlugin.displayName.lowercase(Locale.ROOT) }
    }

    /**
     * Return all known managed plugin display names.
     */
    fun knownPlugins(): List<String> {
        return registrations.values
            .map { it.displayName }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    /**
     * Check GitHub Releases for all registered plugins asynchronously.
     */
    fun checkAllAsync(sender: CommandSender? = null) {
        if (!settings.enabled) {
            sender?.sendUpdaterMessage("&e自动更新已在配置中禁用。")
            return
        }
        runAsync {
            registrations.values.sortedBy { it.displayName.lowercase(Locale.ROOT) }.forEach {
                checkInternal(it, sender)
            }
            sender?.sendUpdaterMessage("&aGitHub Releases 检查完成。当前待批准更新数: &f${candidates.size}")
        }
    }

    /**
     * Check GitHub Releases for a single managed plugin asynchronously.
     */
    fun checkAsync(displayName: String, sender: CommandSender? = null) {
        val managed = registrations[displayName.lowercase(Locale.ROOT)]
        if (managed == null) {
            sender?.sendUpdaterMessage("&c未找到插件: &f$displayName")
            return
        }
        checkAsync(managed, sender)
    }

    /**
     * Return the pending update candidate for a plugin display name.
     */
    fun candidate(displayName: String): MatrixUpdateCandidate? {
        return candidates[displayName.lowercase(Locale.ROOT)]
    }

    /**
     * Approve and download a pending update into `plugins/update/`.
     */
    fun approveDownload(displayName: String, sender: CommandSender): Boolean {
        val candidate = candidate(displayName)
        if (candidate == null) {
            sender.sendUpdaterMessage("&e当前没有待批准更新: &f$displayName")
            return false
        }
        val downloaded = runCatching { downloadCandidate(candidate) }.getOrElse {
            sender.sendUpdaterMessage("&c下载失败: &f${it.message ?: it.javaClass.simpleName}")
            warning("Matrix updater failed to download ${candidate.managedPlugin.displayName}: ${it.message ?: it.javaClass.simpleName}")
            return false
        }
        candidates.remove(candidate.managedPlugin.key)
        sender.sendUpdaterMessage(
            "&a已下载 &f${candidate.managedPlugin.displayName} &a${candidate.release.version} &a到更新目录: &f${downloaded.absolutePath}"
        )
        sender.sendUpdaterMessage("&7重启服务器后会自动替换当前插件文件。")
        return true
    }

    /**
     * Approve and download all pending updates.
     */
    fun approveAll(sender: CommandSender): Int {
        val current = listCandidates()
        current.forEach { approveDownload(it.managedPlugin.displayName, sender) }
        return current.size
    }

    /**
     * Return a short preview of the selected release notes.
     */
    fun notesPreview(displayName: String): List<String> {
        val candidate = candidate(displayName) ?: return emptyList()
        return releaseNotesPreview(candidate.release.body)
    }

    private fun checkAsync(managed: MatrixManagedPlugin, sender: CommandSender? = null) {
        if (!settings.enabled) {
            return
        }
        runAsync {
            checkInternal(managed, sender)
        }
    }

    private fun checkInternal(managed: MatrixManagedPlugin, sender: CommandSender?) {
        val release = runCatching { fetchLatestRelease(managed) }.getOrElse {
            sender?.sendUpdaterMessage("&c检查 ${managed.displayName} 更新失败: &f${it.message ?: it.javaClass.simpleName}")
            warning("Matrix updater failed to check ${managed.displayName}: ${it.message ?: it.javaClass.simpleName}")
            return
        }

        if (release == null) {
            candidates.remove(managed.key)
            sender?.sendUpdaterMessage("&7${managed.displayName} 当前没有可用 GitHub Release。")
            return
        }

        val installedVersion = managed.installedVersion()
        if (!isNewerVersion(release.version, installedVersion)) {
            candidates.remove(managed.key)
            sender?.sendUpdaterMessage("&a${managed.displayName} 已是最新版本: &f$installedVersion")
            return
        }

        val candidate = MatrixUpdateCandidate(
            managedPlugin = managed,
            installedVersion = installedVersion,
            release = release
        )
        candidates[managed.key] = candidate
        notifiedOps.clear()

        if (settings.notifyConsole) {
            notifyConsole(candidate)
        }
        sender?.sendUpdaterMessage(
            "&e发现更新: &f${managed.displayName} &7$installedVersion &8-> &a${release.version}"
        )
        if (!settings.requireApproval) {
            runCatching { downloadCandidate(candidate) }
                .onSuccess {
                    candidates.remove(managed.key)
                    sender?.sendUpdaterMessage("&a已自动下载到更新目录: &f${it.name}")
                }
                .onFailure {
                    sender?.sendUpdaterMessage("&c自动下载失败: &f${it.message ?: it.javaClass.simpleName}")
                }
        }
    }

    private fun fetchLatestRelease(managed: MatrixManagedPlugin): MatrixReleaseInfo? {
        val endpoint = "${settings.apiBase.trimEnd('/')}/repos/${managed.repoOwner}/${managed.repoName}/releases/latest"
        val connection = openConnection(endpoint)
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            return null
        }
        if (code != HttpURLConnection.HTTP_OK) {
            val message = readText(connection.errorStream)
            throw IllegalStateException("GitHub API returned HTTP $code for ${managed.repoFullName}: ${message.ifBlank { "no body" }}")
        }
        InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
            val root = JsonParser.parseReader(reader).asJsonObject
            return parseRelease(root, managed)
        }
    }

    private fun parseRelease(root: JsonObject, managed: MatrixManagedPlugin): MatrixReleaseInfo? {
        val tagName = root.string("tag_name")
        val version = normalizeVersion(tagName)
        if (version.isBlank()) {
            return null
        }
        val assets = root.getAsJsonArray("assets") ?: return null
        val asset = assets
            .mapNotNull { element ->
                val child = element.asJsonObject
                val name = child.string("name")
                if (name.endsWith("-sources.jar", ignoreCase = true) || !managed.assetNamePattern.matches(name)) {
                    return@mapNotNull null
                }
                MatrixReleaseAsset(
                    name = name,
                    browserDownloadUrl = child.string("browser_download_url"),
                    size = child.number("size").toLong()
                )
            }
            .firstOrNull() ?: return null

        return MatrixReleaseInfo(
            version = version,
            tagName = tagName,
            htmlUrl = root.string("html_url"),
            body = root.string("body"),
            publishedAt = root.string("published_at"),
            asset = asset
        )
    }

    private fun downloadCandidate(candidate: MatrixUpdateCandidate): File {
        val managed = candidate.managedPlugin
        val updateDir = Bukkit.getUpdateFolderFile().also { it.mkdirs() }
        val currentJarName = managed.installedJarFile()?.name ?: candidate.release.asset.name
        val targetFile = File(updateDir, currentJarName)
        val tempFile = File(updateDir, "$currentJarName.download")

        val connection = openConnection(candidate.release.asset.browserDownloadUrl)
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val message = readText(connection.errorStream)
            throw IllegalStateException("GitHub asset download returned HTTP $code: ${message.ifBlank { "no body" }}")
        }

        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw IllegalStateException("Unable to replace existing update file: ${targetFile.absolutePath}")
        }
        if (!tempFile.renameTo(targetFile)) {
            throw IllegalStateException("Unable to move downloaded update file to ${targetFile.absolutePath}")
        }
        return targetFile
    }

    private fun notifyConsole(candidate: MatrixUpdateCandidate) {
        val plugin = BukkitPlugin.getInstance()
        plugin.logger.info(
            "[MatrixUpdater] ${candidate.managedPlugin.displayName} ${candidate.release.version} is available " +
                "(current ${candidate.installedVersion})."
        )
        plugin.logger.info("[MatrixUpdater] Approve download with /matrixlib update approve ${candidate.managedPlugin.displayName}")
        plugin.logger.info("[MatrixUpdater] Release: ${candidate.release.htmlUrl}")
        releaseNotesPreview(candidate.release.body).forEach { line ->
            plugin.logger.info("[MatrixUpdater] $line")
        }
    }

    private fun registerListener() {
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                if (!settings.notifyOpsOnJoin) {
                    return
                }
                val player = event.player
                if (!player.isOp && !player.hasPermission("matrixlib.update.manage")) {
                    return
                }
                val pending = listCandidates()
                if (pending.isEmpty()) {
                    return
                }
                val marker = "${player.uniqueId}:${pending.joinToString(",") { it.managedPlugin.displayName + ":" + it.release.version }}"
                if (!notifiedOps.add(marker)) {
                    return
                }
                player.sendUpdaterMessage("&e检测到 ${pending.size} 个待批准更新。")
                pending.forEach { candidate ->
                    player.sendUpdaterMessage(
                        "&f${candidate.managedPlugin.displayName} &7${candidate.installedVersion} &8-> &a${candidate.release.version}"
                    )
                }
                player.sendUpdaterMessage("&7使用 &f/matrixlib update list &7查看详情，或用 &f/matrixlib update approve <插件名> &7下载。")
            }
        }, BukkitPlugin.getInstance())
    }

    private fun schedulePeriodicChecks() {
        if (settings.checkIntervalMinutes <= 0L) {
            return
        }
        val periodTicks = settings.checkIntervalMinutes * 60L * 20L
        repeatingTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
            BukkitPlugin.getInstance(),
            Runnable { checkAllAsync() },
            periodTicks,
            periodTicks
        ).taskId
    }

    private fun runAsync(block: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(BukkitPlugin.getInstance(), Runnable(block))
    }

    private fun loadSettings(): MatrixUpdateSettings {
        val plugin = BukkitPlugin.getInstance()
        MatrixResourceFiles.saveResourceIfAbsent(plugin, "Update/config.yml")
        val yaml = YamlConfiguration.loadConfiguration(MatrixResourceFiles.dataFile(plugin, "Update/config.yml"))
        return MatrixUpdateSettings(
            enabled = yaml.getBoolean("enabled", true),
            checkOnStartup = yaml.getBoolean("check-on-startup", true),
            notifyConsole = yaml.getBoolean("notify-console", true),
            notifyOpsOnJoin = yaml.getBoolean("notify-ops-on-join", true),
            requireApproval = yaml.getBoolean("require-approval", true),
            checkIntervalMinutes = yaml.getLong("check-interval-minutes", 360L).coerceAtLeast(0L),
            apiBase = yaml.getString("github.api-base", "https://api.github.com").orEmpty(),
            userAgent = yaml.getString("github.user-agent", "MatrixLib-Updater").orEmpty(),
            token = yaml.getString("github.token", "").orEmpty().trim(),
            timeoutSeconds = yaml.getInt("github.timeout-seconds", 15).coerceAtLeast(5)
        )
    }

    private fun openConnection(rawUrl: String): HttpURLConnection {
        return (URI.create(rawUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = settings.timeoutSeconds * 1000
            readTimeout = settings.timeoutSeconds * 1000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "${settings.userAgent}/${BukkitPlugin.getInstance().description.version}")
            if (settings.token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${settings.token}")
            }
        }
    }

    private fun readText(stream: java.io.InputStream?): String {
        if (stream == null) {
            return ""
        }
        return stream.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
        }.trim()
    }

    private fun normalizeVersion(value: String): String {
        return value.trim().removePrefix("v").removePrefix("V")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val left = numericVersionParts(normalizeVersion(latest))
        val right = numericVersionParts(normalizeVersion(current))
        val size = maxOf(left.size, right.size)
        repeat(size) { index ->
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) {
                return l > r
            }
        }
        return false
    }

    private fun numericVersionParts(version: String): List<Int> {
        return version.split('.', '-', '_')
            .mapNotNull { part ->
                part.takeWhile { it.isDigit() }.takeIf(String::isNotBlank)?.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
    }

    private fun releaseNotesPreview(body: String): List<String> {
        return body.lines()
            .map { it.trim() }
            .filter(String::isNotBlank)
            .map { it.removePrefix("- ").removePrefix("* ").removePrefix("## ").removePrefix("# ") }
            .take(3)
    }

    private fun JsonObject.string(key: String): String {
        return get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    }

    private fun JsonObject.number(key: String): Number {
        return get(key)?.takeUnless { it.isJsonNull }?.asNumber ?: 0
    }
}

private fun CommandSender.sendUpdaterMessage(text: String) {
    sendMessage(MatrixText.color("&8[&bMatrixUpdater&8] &7" + text.removePrefix("&7")))
}

fun CommandSender.sendMatrixUpdaterMessage(text: String) {
    sendUpdaterMessage(text)
}
