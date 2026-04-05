package com.y54895.matrixlib.api.text

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Lightweight YAML-backed message bundle with Matrix prefix handling.
 */
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

    /**
     * Reload the bundle from disk and restore defaults from the bundled resource.
     */
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

    /**
     * Resolve a prefixed message by path.
     */
    fun message(path: String): String {
        return prefix + raw(path)
    }

    /**
     * Resolve a prefixed message by path with placeholders.
     */
    fun message(path: String, placeholders: Map<String, String>): String {
        return prefix + raw(path, placeholders)
    }

    /**
     * Resolve a raw message by path.
     */
    fun raw(path: String): String {
        return MatrixText.raw(config.getString(path, "&c[Missing message: $path]").orEmpty())
    }

    /**
     * Resolve a raw message by path with placeholders.
     */
    fun raw(path: String, placeholders: Map<String, String>): String {
        return MatrixText.raw(config.getString(path, "&c[Missing message: $path]").orEmpty(), placeholders)
    }
}
