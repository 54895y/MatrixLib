package com.y54895.matrixlib.api.text

import com.y54895.matrixlib.api.brand.MatrixBranding
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

object MatrixText {

    fun color(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text).replace("\\n", "\n")
    }

    fun prefix(branding: MatrixBranding): String {
        return "&8[${branding.accentColor}${branding.displayName}&8] ${branding.neutralColor}"
    }

    fun prefixed(branding: MatrixBranding, text: String): String {
        return color(prefix(branding) + text)
    }

    fun raw(text: String, placeholders: Map<String, String> = emptyMap()): String {
        var rendered = text
        placeholders.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return color(rendered)
    }

    fun send(sender: CommandSender, branding: MatrixBranding, text: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(prefixed(branding, apply(text, placeholders)))
    }

    fun sendRaw(sender: CommandSender, text: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(raw(text, placeholders))
    }

    fun apply(text: String, placeholders: Map<String, String> = emptyMap()): String {
        var rendered = text
        placeholders.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value)
        }
        return rendered
    }

    fun apply(lines: List<String>, placeholders: Map<String, String> = emptyMap()): List<String> {
        return lines.map { raw(it, placeholders) }
    }
}
