package com.y54895.matrixlib.api.text

import com.y54895.matrixlib.api.brand.MatrixBranding
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

/**
 * Shared text formatting and placeholder interpolation helpers.
 */
object MatrixText {

    /**
     * Translate `&` color codes and escaped line breaks.
     */
    fun color(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text).replace("\\n", "\n")
    }

    /**
     * Create the standard Matrix prefix string for a branding descriptor.
     */
    fun prefix(branding: MatrixBranding): String {
        return "&8[${branding.accentColor}${branding.displayName}&8] ${branding.neutralColor}"
    }

    /**
     * Apply the Matrix prefix and colorize the result.
     */
    fun prefixed(branding: MatrixBranding, text: String): String {
        return color(prefix(branding) + text)
    }

    /**
     * Resolve placeholders and colorize the result without a Matrix prefix.
     */
    fun raw(text: String, placeholders: Map<String, String> = emptyMap()): String {
        var rendered = text
        placeholders.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return color(rendered)
    }

    /**
     * Send a prefixed message to a command sender.
     */
    fun send(sender: CommandSender, branding: MatrixBranding, text: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(prefixed(branding, apply(text, placeholders)))
    }

    /**
     * Send a raw colorized message to a command sender.
     */
    fun sendRaw(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(raw(text, placeholders))
    }

    /**
     * Resolve placeholders without color translation.
     */
    fun apply(text: String, placeholders: Map<String, String> = emptyMap()): String {
        var rendered = text
        placeholders.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return rendered
    }

    /**
     * Resolve placeholders for multiple lines and colorize each line.
     */
    fun apply(lines: List<String>, placeholders: Map<String, String> = emptyMap()): List<String> {
        return lines.map { raw(it, placeholders) }
    }
}
