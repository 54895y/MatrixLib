package com.y54895.matrixlib.api.resource

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object MatrixResourceFiles {

    fun dataFile(plugin: JavaPlugin, path: String): File {
        return File(plugin.dataFolder, path)
    }

    fun saveResourceIfAbsent(plugin: JavaPlugin, path: String) {
        val target = dataFile(plugin, path)
        if (target.exists()) {
            return
        }
        target.parentFile?.mkdirs()
        val stream = plugin.getResource(path) ?: return
        stream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
