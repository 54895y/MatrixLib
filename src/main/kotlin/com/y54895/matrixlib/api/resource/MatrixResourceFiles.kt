package com.y54895.matrixlib.api.resource

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Minimal shared resource helper for Matrix plugins.
 */
object MatrixResourceFiles {

    /**
     * Resolve a data file under the plugin data folder.
     */
    fun dataFile(plugin: JavaPlugin, path: String): File {
        return File(plugin.dataFolder, path)
    }

    /**
     * Copy a bundled resource into the data folder only when the file is absent.
     */
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
