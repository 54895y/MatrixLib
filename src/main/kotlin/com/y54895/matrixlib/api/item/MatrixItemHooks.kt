package com.y54895.matrixlib.api.item

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap

data class MatrixResolvedItem(
    val rawId: String,
    val sourceId: String,
    val sourceName: String,
    val itemStack: ItemStack?,
    val resolved: Boolean,
    val failureReason: String? = null
)

interface MatrixItemHook {

    val id: String

    val aliases: List<String>
        get() = emptyList()

    val sourceName: String
        get() = id

    fun isAvailable(): Boolean = true

    fun resolve(entry: String, player: Player? = null): ItemStack?
}

object MatrixItemHooks {

    private val hooks = ConcurrentHashMap<String, MatrixItemHook>()
    private val aliasMap = ConcurrentHashMap<String, String>()

    init {
        register(object : MatrixItemHook {
            override val id: String = "minecraft"
            override val aliases: List<String> = listOf("mc")
            override val sourceName: String = "Minecraft"

            override fun resolve(entry: String, player: Player?): ItemStack? {
                val material = Material.matchMaterial(entry)
                    ?: runCatching { Material.valueOf(entry.uppercase()) }.getOrNull()
                    ?: return null
                return ItemStack(material)
            }
        })
    }

    fun register(hook: MatrixItemHook) {
        hooks[hook.id.lowercase()] = hook
        hook.aliases.forEach { alias ->
            aliasMap[alias.lowercase()] = hook.id.lowercase()
        }
    }

    fun unregister(id: String) {
        val normalized = id.lowercase()
        hooks.remove(normalized)
        aliasMap.entries.removeIf { it.value == normalized }
    }

    fun clearCustom() {
        val keep = setOf("minecraft")
        hooks.keys.removeIf { it !in keep }
        aliasMap.entries.removeIf { it.value !in keep }
    }

    fun resolve(rawId: String, player: Player? = null): MatrixResolvedItem {
        return resolveInternal(rawId, player, quiet = false)
    }

    fun resolveQuiet(rawId: String, player: Player? = null): MatrixResolvedItem {
        return resolveInternal(rawId, player, quiet = true)
    }

    fun isResolvable(rawId: String): Boolean {
        val parsed = parse(rawId)
        val hook = findHook(parsed.sourceId) ?: return false
        return hook.isAvailable()
    }

    fun resolveItemStack(rawId: String, amount: Int = 1, player: Player? = null, quiet: Boolean = false): ItemStack? {
        val resolved = if (quiet) resolveQuiet(rawId, player) else resolve(rawId, player)
        val base = resolved.itemStack ?: return null
        return base.clone().apply {
            this.amount = amount.coerceAtLeast(1)
        }
    }

    private fun resolveInternal(rawId: String, player: Player?, quiet: Boolean): MatrixResolvedItem {
        val parsed = parse(rawId)
        val hook = findHook(parsed.sourceId)
        if (hook == null) {
            val reason = "Item source '${parsed.sourceId}' is not registered"
            if (!quiet) {
                warning(reason)
            }
            return MatrixResolvedItem(rawId, parsed.sourceId, parsed.sourceId, null, false, reason)
        }
        if (!hook.isAvailable()) {
            val reason = "Item source '${parsed.sourceId}' is not available"
            if (!quiet) {
                warning(reason)
            }
            return MatrixResolvedItem(rawId, hook.id, hook.sourceName, null, false, reason)
        }
        val item = runCatching {
            hook.resolve(parsed.entry, player)
        }.onFailure {
            if (!quiet) {
                warning("Failed to resolve item '$rawId' with source '${hook.id}': ${it.message}")
            }
        }.getOrNull()
        if (item == null) {
            val reason = "Item '$rawId' could not be resolved"
            if (!quiet) {
                warning(reason)
            }
            return MatrixResolvedItem(rawId, hook.id, hook.sourceName, null, false, reason)
        }
        return MatrixResolvedItem(rawId, hook.id, hook.sourceName, item, true, null)
    }

    private fun parse(rawId: String): ParsedItemId {
        val split = rawId.split(":", limit = 2)
        if (split.size == 1) {
            return ParsedItemId("minecraft", rawId)
        }
        return ParsedItemId(split[0].lowercase(), split[1])
    }

    private fun findHook(sourceId: String): MatrixItemHook? {
        return hooks[sourceId]
            ?: aliasMap[sourceId]?.let(hooks::get)
    }

    private data class ParsedItemId(
        val sourceId: String,
        val entry: String
    )
}
