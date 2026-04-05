package com.y54895.matrixlib.command

import com.y54895.matrixlib.api.text.MatrixText
import com.y54895.matrixlib.api.update.MatrixPluginUpdates
import com.y54895.matrixlib.api.update.sendMatrixUpdaterMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import java.lang.reflect.Field
import java.util.Locale

object MatrixLibCommands {

    private var registered = false

    fun register() {
        if (registered) {
            return
        }
        val command = MatrixLibRoutingCommand(
            "matrixlib",
            "MatrixLib main command",
            "/matrixlib",
            "matrixlib.update.manage",
            ::handleExecute,
            ::handleTab
        )
        BukkitCommandRegistrar.register(command)
        registered = true
    }

    private fun handleExecute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        if (args[0].equals("update", true).not()) {
            sendHelp(sender)
            return true
        }
        if (!sender.hasPermission("matrixlib.update.manage") && sender !is org.bukkit.command.ConsoleCommandSender && !sender.isOp) {
            sender.sendMatrixUpdaterMessage("&c你没有权限执行更新管理。")
            return true
        }
        when (args.getOrNull(1)?.lowercase(Locale.ROOT) ?: "list") {
            "list", "status" -> {
                val pending = MatrixPluginUpdates.listCandidates()
                if (pending.isEmpty()) {
                    sender.sendMatrixUpdaterMessage("&a当前没有待批准更新。")
                } else {
                    sender.sendMatrixUpdaterMessage("&e当前待批准更新数: &f${pending.size}")
                    pending.forEach {
                        sender.sendMatrixUpdaterMessage(
                            "&f${it.managedPlugin.displayName} &7${it.installedVersion} &8-> &a${it.release.version}"
                        )
                    }
                }
            }

            "check" -> {
                val target = args.getOrNull(2)
                if (target == null || target.equals("all", true)) {
                    sender.sendMatrixUpdaterMessage("&7已开始检查全部 Matrix 插件的 GitHub Releases。")
                    MatrixPluginUpdates.checkAllAsync(sender)
                } else {
                    sender.sendMatrixUpdaterMessage("&7已开始检查插件: &f$target")
                    MatrixPluginUpdates.checkAsync(target, sender)
                }
            }

            "notes" -> {
                val target = args.getOrNull(2)
                if (target == null) {
                    sender.sendMatrixUpdaterMessage("&e用法: &f/matrixlib update notes <插件名>")
                    return true
                }
                val candidate = MatrixPluginUpdates.candidate(target)
                if (candidate == null) {
                    sender.sendMatrixUpdaterMessage("&e当前没有待批准更新: &f$target")
                    return true
                }
                sender.sendMatrixUpdaterMessage(
                    "&f${candidate.managedPlugin.displayName} &7${candidate.installedVersion} &8-> &a${candidate.release.version}"
                )
                sender.sendMatrixUpdaterMessage("&7Release: &f${candidate.release.htmlUrl}")
                val preview = MatrixPluginUpdates.notesPreview(target)
                if (preview.isEmpty()) {
                    sender.sendMatrixUpdaterMessage("&7当前 release 没有可显示的更新摘要。")
                } else {
                    preview.forEach { line ->
                        sender.sendMatrixUpdaterMessage("&f- $line")
                    }
                }
            }

            "approve", "download" -> {
                val target = args.getOrNull(2)
                if (target == null) {
                    sender.sendMatrixUpdaterMessage("&e用法: &f/matrixlib update approve <插件名|all>")
                    return true
                }
                if (target.equals("all", true)) {
                    val count = MatrixPluginUpdates.approveAll(sender)
                    sender.sendMatrixUpdaterMessage("&a本次已处理更新项: &f$count")
                } else {
                    MatrixPluginUpdates.approveDownload(target, sender)
                }
            }

            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleTab(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("matrixlib.update.manage") && sender !is org.bukkit.command.ConsoleCommandSender && !sender.isOp) {
            return emptyList()
        }
        return when (args.size) {
            1 -> filter(listOf("update"), args[0])
            2 -> filter(listOf("list", "status", "check", "notes", "approve"), args[1])
            3 -> when (args[1].lowercase(Locale.ROOT)) {
                "check" -> filter(listOf("all") + MatrixPluginUpdates.knownPlugins(), args[2])
                "notes", "approve" -> filter(listOf("all") + MatrixPluginUpdates.knownPlugins(), args[2])
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(MatrixText.color("&8[&bMatrixLib&8] &f更新管理"))
        sender.sendMessage(MatrixText.color("&7/matrixlib update list"))
        sender.sendMessage(MatrixText.color("&7/matrixlib update check [all|插件名]"))
        sender.sendMessage(MatrixText.color("&7/matrixlib update notes <插件名>"))
        sender.sendMessage(MatrixText.color("&7/matrixlib update approve <插件名|all>"))
    }

    private fun filter(values: List<String>, input: String): List<String> {
        val lowered = input.lowercase(Locale.ROOT)
        return values.filter { it.lowercase(Locale.ROOT).startsWith(lowered) }.sorted()
    }
}

private object BukkitCommandRegistrar {

    fun register(command: Command) {
        commandMap()?.register(command.name.lowercase(Locale.ROOT), command)
        syncCommands()
    }

    private fun commandMap(): CommandMap? {
        return runCatching {
            val server = Bukkit.getServer()
            val field = findCommandMapField(server.javaClass)
            field.isAccessible = true
            field.get(server) as? CommandMap
        }.getOrNull()
    }

    private fun findCommandMapField(type: Class<*>): Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField("commandMap")
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("commandMap")
    }

    private fun syncCommands() {
        runCatching {
            val method = Bukkit.getServer().javaClass.methods.firstOrNull {
                it.name == "syncCommands" && it.parameterCount == 0
            } ?: return
            method.invoke(Bukkit.getServer())
        }
    }
}

private class MatrixLibRoutingCommand(
    name: String,
    description: String,
    usage: String,
    permissionNode: String? = null,
    private val executeHandler: (CommandSender, String, Array<out String>) -> Boolean,
    private val tabHandler: (CommandSender, String, Array<out String>) -> List<String>
) : Command(name) {

    init {
        this.description = description
        this.usageMessage = usage
        this.permission = permissionNode
        this.aliases = listOf("mlib")
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        return executeHandler(sender, commandLabel, args)
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): MutableList<String> {
        return tabHandler(sender, alias, args).toMutableList()
    }
}
