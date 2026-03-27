package com.y54895.matrixlib.api.item

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object MatrixCommonItemHooks {

    fun registerCommon(owner: String) {
        registerCraftEngine(owner)
        registerItemsAdder(owner)
        registerOraxen(owner)
    }

    private fun registerCraftEngine(owner: String) {
        MatrixItemHooks.register(owner, object : MatrixItemHook {
            override val id: String = "craftengine"
            override val aliases: List<String> = listOf("ce")
            override val sourceName: String = "CraftEngine"

            override fun isAvailable(): Boolean {
                return Bukkit.getPluginManager().isPluginEnabled("CraftEngine")
            }

            override fun resolve(entry: String, player: Player?): ItemStack? {
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) {
                    return null
                }
                val keyClass = Class.forName("net.momirealms.craftengine.core.util.Key")
                val key = keyClass.getConstructor(String::class.java, String::class.java)
                    .newInstance(parts[0], parts[1])
                val craftEngineItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
                val customItem = craftEngineItemsClass.getMethod("byId", keyClass).invoke(null, key) ?: return null

                val built = if (player != null) {
                    val engineClass = Class.forName("net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine")
                    val engine = engineClass.getMethod("instance").invoke(null)
                    val craftPlayer = engine.javaClass.getMethod("adapt", Player::class.java).invoke(engine, player)
                    customItem.javaClass.methods.firstOrNull { it.name == "buildItemStack" && it.parameterCount == 1 }
                        ?.invoke(customItem, craftPlayer)
                } else {
                    customItem.javaClass.methods.firstOrNull { it.name == "buildItemStack" && it.parameterCount == 0 }
                        ?.invoke(customItem)
                }
                return built as? ItemStack
            }
        })
    }

    private fun registerItemsAdder(owner: String) {
        MatrixItemHooks.register(owner, object : MatrixItemHook {
            override val id: String = "itemsadder"
            override val aliases: List<String> = listOf("ia")
            override val sourceName: String = "ItemsAdder"

            override fun isAvailable(): Boolean {
                return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")
            }

            override fun resolve(entry: String, player: Player?): ItemStack? {
                val customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
                val customStack = customStackClass.getMethod("getInstance", String::class.java).invoke(null, entry)
                    ?: return null
                return customStackClass.getMethod("getItemStack").invoke(customStack) as? ItemStack
            }
        })
    }

    private fun registerOraxen(owner: String) {
        MatrixItemHooks.register(owner, object : MatrixItemHook {
            override val id: String = "oraxen"
            override val aliases: List<String> = listOf("ox")
            override val sourceName: String = "Oraxen"

            override fun isAvailable(): Boolean {
                return Bukkit.getPluginManager().isPluginEnabled("Oraxen")
            }

            override fun resolve(entry: String, player: Player?): ItemStack? {
                val oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
                val builder = oraxenItemsClass.methods.firstOrNull {
                    (it.name == "getItemById" || it.name == "getItemByItemId") &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == String::class.java
                }?.invoke(null, entry) ?: return null
                val buildMethod = builder.javaClass.methods.firstOrNull {
                    (it.name == "build" || it.name == "buildItemStack") && it.parameterCount == 0
                } ?: return null
                return buildMethod.invoke(builder) as? ItemStack
            }
        })
    }
}
