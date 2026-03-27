package com.y54895.matrixlib.api.platform

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Field
import java.util.Locale
import java.util.logging.Logger

object MatrixBukkitPlugins {

    fun findPlugin(vararg names: String): Plugin? {
        val manager = Bukkit.getPluginManager()
        return names.firstNotNullOfOrNull { name ->
            name.trim().takeIf { it.isNotBlank() }?.let(manager::getPlugin)
        }
    }

    fun findJavaPlugin(vararg names: String): JavaPlugin? {
        return findPlugin(*names) as? JavaPlugin
    }

    fun requireJavaPlugin(vararg names: String): JavaPlugin {
        return findJavaPlugin(*names)
            ?: error("Unable to resolve plugin instance for ${names.joinToString(" / ")}")
    }

    fun hasPlugin(vararg names: String): Boolean {
        return findPlugin(*names) != null
    }

    fun isPluginEnabled(vararg names: String): Boolean {
        return findPlugin(*names)?.isEnabled == true
    }
}

object MatrixBukkitCommands {

    fun register(command: Command, logger: Logger? = null): Boolean {
        val commandMap = commandMap(logger) ?: return false
        commandMap.register(command.name.lowercase(Locale.ROOT), command)
        return true
    }

    fun commandMap(logger: Logger? = null): CommandMap? {
        return try {
            val server = Bukkit.getServer()
            val field = findCommandMapField(server.javaClass)
            field.isAccessible = true
            field.get(server) as? CommandMap
        } catch (ex: Throwable) {
            logger?.warning("Failed to resolve Bukkit command map: ${ex.message}")
            null
        }
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
}
