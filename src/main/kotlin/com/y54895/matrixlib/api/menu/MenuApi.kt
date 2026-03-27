package com.y54895.matrixlib.api.menu

import com.y54895.matrixlib.api.action.ActionContext
import com.y54895.matrixlib.api.action.ActionExecutor
import com.y54895.matrixlib.api.item.MatrixItemHooks
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import java.io.File
import java.util.UUID
import java.util.function.BiConsumer
import java.util.function.Consumer

data class MenuDefinition(
    val title: List<String>,
    val layout: List<String>,
    val icons: Map<Char, MenuIcon>,
    val template: MenuTemplate = MenuTemplate("&f{name}", emptyList())
)

data class MenuIcon(
    val material: String,
    val name: String = " ",
    val lore: List<String> = emptyList(),
    val amount: Int = 1,
    val mode: String? = null,
    val actions: Map<String, List<String>> = emptyMap()
)

data class MenuTemplate(
    val name: String,
    val lore: List<String>
)

class MatrixMenuHolder(
    private val owner: UUID,
    val backAction: Runnable? = null
) : InventoryHolder {

    lateinit var backingInventory: Inventory
    val handlers = HashMap<Int, Consumer<InventoryClickEvent>>()
    var closeHandler: Runnable? = null

    override fun getInventory(): Inventory {
        return backingInventory
    }

    fun isViewer(player: Player): Boolean {
        return owner == player.uniqueId
    }
}

object MenuLoader {

    fun load(
        file: File,
        defaultTitle: List<String> = listOf("&8Matrix"),
        defaultTemplate: MenuTemplate = MenuTemplate("&f{name}", emptyList())
    ): MenuDefinition {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val title = when {
            yaml.isList("Title") -> yaml.getStringList("Title").ifEmpty { defaultTitle }
            yaml.contains("Title") -> listOf(yaml.getString("Title").orEmpty()).ifEmpty { defaultTitle }
            else -> defaultTitle
        }
        val layout = yaml.getStringList("layout")
        val icons = loadIcons(yaml.getConfigurationSection("icons"))
        val templateSection = yaml.getConfigurationSection("template")
        val template = MenuTemplate(
            templateSection?.getString("name", defaultTemplate.name).orEmpty(),
            templateSection?.getStringList("lore")?.ifEmpty { defaultTemplate.lore } ?: defaultTemplate.lore
        )
        return MenuDefinition(title, layout, icons, template)
    }

    private fun loadIcons(section: ConfigurationSection?): Map<Char, MenuIcon> {
        if (section == null) {
            return emptyMap()
        }
        return section.getKeys(false).associate { key ->
            val child = section.getConfigurationSection(key)!!
            key.first() to MenuIcon(
                material = child.getString("material", "STONE").orEmpty(),
                name = child.getString("name", " ").orEmpty(),
                lore = child.getStringList("lore"),
                amount = child.getInt("amount", 1),
                mode = child.getString("mode"),
                actions = loadActions(child.getConfigurationSection("actions"))
            )
        }
    }

    private fun loadActions(section: ConfigurationSection?): Map<String, List<String>> {
        if (section == null) {
            return emptyMap()
        }
        return section.getKeys(false).associateWith { key ->
            when {
                section.isList(key) -> section.getStringList(key)
                section.contains(key) -> listOf(section.getString(key).orEmpty())
                else -> emptyList()
            }
        }
    }
}

object MenuRenderer {

    fun open(
        player: Player,
        definition: MenuDefinition,
        placeholders: Map<String, String>,
        backAction: Runnable? = null,
        goodsRenderer: BiConsumer<MatrixMenuHolder, List<Int>>? = null,
        closeHandler: Runnable? = null,
        actionExecutor: BiConsumer<ActionContext, List<String>> = BiConsumer { context, actions ->
            ActionExecutor.execute(context, actions)
        }
    ) {
        val title = MatrixText.apply(definition.title.firstOrNull().orEmpty(), placeholders)
        val holder = MatrixMenuHolder(player.uniqueId, backAction)
        val inventory = Bukkit.createInventory(holder, definition.layout.size * 9, MatrixText.color(title))
        holder.backingInventory = inventory
        holder.closeHandler = closeHandler
        val goodsSlots = ArrayList<Int>()
        definition.layout.forEachIndexed { row, line ->
            line.toCharArray().forEachIndexed { column, symbol ->
                val slot = row * 9 + column
                val icon = definition.icons[symbol] ?: return@forEachIndexed
                if (!icon.mode.isNullOrBlank()) {
                    goodsSlots += slot
                    return@forEachIndexed
                }
                inventory.setItem(slot, buildIcon(icon, placeholders))
                if (icon.actions.isNotEmpty()) {
                    holder.handlers[slot] = Consumer { event ->
                        actionExecutor.accept(
                            ActionContext(player, placeholders, holder.backAction),
                            icon.actions[actionKey(event.click)].orEmpty()
                        )
                    }
                }
            }
        }
        goodsRenderer?.accept(holder, goodsSlots)
        player.openInventory(inventory)
    }

    fun buildIcon(icon: MenuIcon, placeholders: Map<String, String>): ItemStack {
        val stack = MatrixItemHooks.resolveItemStack(icon.material, icon.amount, quiet = true)
            ?: ItemStack(Material.STONE, icon.amount.coerceAtLeast(1))
        val meta = stack.itemMeta
        if (meta != null) {
            decorate(meta, MatrixText.apply(icon.name, placeholders), MatrixText.apply(icon.lore, placeholders))
            stack.itemMeta = meta
        }
        return stack
    }

    fun decorate(meta: ItemMeta, name: String, lore: List<String>) {
        meta.setDisplayName(MatrixText.color(name))
        meta.lore = MatrixText.apply(lore)
        meta.addItemFlags(*ItemFlag.values())
    }

    private fun actionKey(click: ClickType): String {
        return when (click) {
            ClickType.RIGHT -> "right"
            ClickType.SHIFT_LEFT -> "shift_left"
            ClickType.SHIFT_RIGHT -> "shift_right"
            ClickType.MIDDLE -> "middle"
            else -> "left"
        }
    }
}

@Awake
object MenuListener {

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MatrixMenuHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (!holder.isViewer(player)) {
            event.isCancelled = true
            return
        }
        if (event.clickedInventory == null || event.clickedInventory != event.view.topInventory) {
            return
        }
        event.isCancelled = true
        holder.handlers[event.rawSlot]?.accept(event)
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? MatrixMenuHolder ?: return
        holder.closeHandler?.run()
    }
}
