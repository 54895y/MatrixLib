package com.y54895.matrixlib.api.text

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class MatrixYamlBundle(
    private val plugin: JavaPlugin,
    private val branding: MatrixBranding,
    private val resourcePath: String
) {

    private lateinit var config: YamlConfiguration
    private var prefix: String = MatrixText.color(MatrixText.prefix(branding))

    init {
        reload()
    }

    fun reload() {
        MatrixResourceFiles.saveResourceIfAbsent(plugin, resourcePath)
        val file = MatrixResourceFiles.dataFile(plugin, resourcePath)
        config = YamlConfiguration.loadConfiguration(file)
        plugin.getResource(resourcePath)?.use { input ->
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
            config.setDefaults(defaults)
        }
        prefix = MatrixText.color(config.getString("prefix", MatrixText.prefix(branding)).orEmpty())
    }

    fun message(path: String): String {
        return prefix + raw(path)
    }

    fun message(path: String, placeholders: Map<String, String>): String {
        return prefix + raw(path, placeholders)
    }

    fun raw(path: String): String {
        return MatrixText.raw(config.getString(path, "&c[Missing message: $path]").orEmpty())
    }

    fun raw(path: String, placeholders: Map<String, String>): String {
        return MatrixText.raw(config.getString(path, "&c[Missing message: $path]").orEmpty(), placeholders)
    }
}
