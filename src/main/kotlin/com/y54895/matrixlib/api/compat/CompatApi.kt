package com.y54895.matrixlib.api.compat

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.material.Directional
import org.bukkit.material.Hopper as LegacyHopper
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

enum class ServerFamily {
    FOLIA,
    PAPER,
    SPIGOT,
    BUKKIT,
    HYBRID,
    UNKNOWN
}

data class ServerRuntime(
    val family: ServerFamily,
    val flavor: String,
    val serverName: String,
    val versionText: String,
    val implementationClass: String
)

object ServerCompat {

    private data class HybridPattern(val flavor: String, val markers: List<String>)

    private val hybridPatterns = listOf(
        HybridPattern("Mohist", listOf("com.mohistmc.", "mohist")),
        HybridPattern("Arclight", listOf("io.izzel.arclight.", "arclight")),
        HybridPattern("CatServer", listOf("catserver.", "catserver")),
        HybridPattern("Magma", listOf("org.magmafoundation.", "magma")),
        HybridPattern("Banner", listOf("com.mohistmc.banner", "banner"))
    )

    val runtime: ServerRuntime by lazy {
        val server = Bukkit.getServer()
        val implementationClass = server.javaClass.name
        val serverName = runCatching { server.name }.getOrElse { Bukkit.getName() }
        val versionText = Bukkit.getVersion()
        val hasFoliaSchedulers =
            serverHasMethod(server, "getGlobalRegionScheduler") &&
                serverHasMethod(server, "getRegionScheduler") &&
                serverHasMethod(server, "getAsyncScheduler")
        val composite = listOf(serverName, versionText, implementationClass)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        detectHybrid(composite)?.let { flavor ->
            return@lazy ServerRuntime(ServerFamily.HYBRID, flavor, serverName, versionText, implementationClass)
        }

        when {
            classExists("io.papermc.paper.threadedregions.RegionizedServer") ||
                composite.contains("folia") ||
                hasFoliaSchedulers -> {
                ServerRuntime(ServerFamily.FOLIA, "Folia", serverName, versionText, implementationClass)
            }
            classExists("io.papermc.paper.configuration.Configuration") || composite.contains("paper") ||
                composite.contains("purpur") || composite.contains("pufferfish") || composite.contains("leaves") -> {
                val flavor = when {
                    composite.contains("purpur") -> "Purpur"
                    composite.contains("pufferfish") -> "Pufferfish"
                    composite.contains("leaves") -> "Leaves"
                    else -> "Paper"
                }
                ServerRuntime(ServerFamily.PAPER, flavor, serverName, versionText, implementationClass)
            }
            classExists("org.spigotmc.SpigotConfig") || composite.contains("spigot") -> {
                ServerRuntime(ServerFamily.SPIGOT, "Spigot", serverName, versionText, implementationClass)
            }
            composite.contains("craftbukkit") || composite.contains("bukkit") -> {
                ServerRuntime(ServerFamily.BUKKIT, "Bukkit", serverName, versionText, implementationClass)
            }
            else -> {
                ServerRuntime(ServerFamily.UNKNOWN, "Unknown", serverName, versionText, implementationClass)
            }
        }
    }

    val isFolia: Boolean
        get() = runtime.family == ServerFamily.FOLIA

    val isPaperLike: Boolean
        get() = runtime.family == ServerFamily.FOLIA || runtime.family == ServerFamily.PAPER

    val isHybrid: Boolean
        get() = runtime.family == ServerFamily.HYBRID

    fun describe(): String {
        return buildString {
            append(runtime.flavor)
            append(" / ")
            append(runtime.family.name.lowercase(Locale.ROOT))
            append(" / ")
            append(runtime.serverName)
        }
    }

    private fun detectHybrid(composite: String): String? {
        return hybridPatterns.firstOrNull { pattern ->
            pattern.markers.any { marker ->
                composite.contains(marker)
            }
        }?.flavor
    }

    private fun classExists(className: String): Boolean {
        return runCatching {
            Class.forName(className, false, Bukkit.getServer().javaClass.classLoader)
        }.isSuccess || runCatching {
            Class.forName(className)
        }.isSuccess
    }

    private fun serverHasMethod(server: Any, name: String): Boolean {
        return runCatching {
            server.javaClass.getMethod(name)
        }.isSuccess
    }
}

object BukkitCompat {

    data class HopperState(val enabled: Boolean, val facing: BlockFace?)

    private data class LegacyItemAlias(val material: String, val durability: Short = 0)

    private val getBlockDataMethod: Method? = Block::class.java.methods.firstOrNull {
        it.name == "getBlockData" && it.parameterCount == 0
    }

    private val setBlockDataMethod: Method? = Block::class.java.methods.firstOrNull {
        it.name == "setBlockData" && it.parameterCount == 1
    }

    private val setTypeWithPhysicsMethod: Method? = Block::class.java.methods.firstOrNull {
        it.name == "setType" &&
            it.parameterCount == 2 &&
            it.parameterTypes[0] == Material::class.java &&
            it.parameterTypes[1] == Boolean::class.javaPrimitiveType
    }

    private val setDropItemsMethod: Method? = BlockBreakEvent::class.java.methods.firstOrNull {
        it.name == "setDropItems" && it.parameterCount == 1
    }

    private val itemMetaPersistentDataMethod: Method? = ItemMeta::class.java.methods.firstOrNull {
        it.name == "getPersistentDataContainer" && it.parameterCount == 0
    }

    private val persistentDataGetMethod: Method? = itemMetaPersistentDataMethod?.returnType?.methods?.firstOrNull {
        it.name == "get" && it.parameterCount == 2
    }

    private val persistentDataSetMethod: Method? = itemMetaPersistentDataMethod?.returnType?.methods?.firstOrNull {
        it.name == "set" && it.parameterCount == 3
    }

    private val namespacedKeyConstructor by lazy {
        runCatching {
            Class.forName("org.bukkit.NamespacedKey")
                .getConstructor(Plugin::class.java, String::class.java)
        }.getOrNull()
    }

    private val persistentStringType by lazy {
        runCatching {
            Class.forName("org.bukkit.persistence.PersistentDataType")
                .getField("STRING")
                .get(null)
        }.getOrNull()
    }

    private val hasCustomModelDataMethod: Method? = ItemMeta::class.java.methods.firstOrNull {
        it.name == "hasCustomModelData" && it.parameterCount == 0
    }

    private val getCustomModelDataMethod: Method? = ItemMeta::class.java.methods.firstOrNull {
        it.name == "getCustomModelData" && it.parameterCount == 0
    }

    private val setCustomModelDataMethod: Method? = ItemMeta::class.java.methods.firstOrNull {
        it.name == "setCustomModelData" &&
            it.parameterCount == 1 &&
            it.parameterTypes[0] == Integer.TYPE
    }

    private val legacyMaterialAliases = mapOf(
        "LIME_STAINED_GLASS_PANE" to LegacyItemAlias("STAINED_GLASS_PANE", 5),
        "GRAY_STAINED_GLASS_PANE" to LegacyItemAlias("STAINED_GLASS_PANE", 7),
        "BLACK_STAINED_GLASS_PANE" to LegacyItemAlias("STAINED_GLASS_PANE", 15),
        "SPECTRAL_ARROW" to LegacyItemAlias("ARROW")
    )

    private val legacyItemAliases = legacyMaterialAliases + mapOf(
        "CAULDRON" to LegacyItemAlias("CAULDRON_ITEM"),
        "PLAYER_HEAD" to LegacyItemAlias("SKULL_ITEM", 3)
    )

    fun matchMaterial(vararg candidates: String): Material? {
        return candidates.firstNotNullOfOrNull { name ->
            resolveDirectMaterial(name)
                ?: legacyMaterialAliases[name.uppercase()]?.let { alias ->
                    resolveDirectMaterial(alias.material)
                }
        }
    }

    fun isAir(material: Material): Boolean {
        return material.name == "AIR" || material.name.endsWith("_AIR")
    }

    fun isEmpty(item: ItemStack?): Boolean {
        return item == null || isAir(item.type)
    }

    fun isSameItem(left: ItemStack?, right: ItemStack?): Boolean {
        val leftEmpty = isEmpty(left)
        val rightEmpty = isEmpty(right)
        if (leftEmpty || rightEmpty) {
            return leftEmpty == rightEmpty
        }
        return left!!.amount == right!!.amount && left.isSimilar(right)
    }

    fun createItemStack(vararg candidates: String, amount: Int = 1): ItemStack? {
        return candidates.firstNotNullOfOrNull { name ->
            resolveDirectMaterial(name)?.let { material ->
                return@firstNotNullOfOrNull ItemStack(material, amount)
            }

            legacyItemAliases[name.uppercase()]?.let { alias ->
                val material = resolveDirectMaterial(alias.material) ?: return@let null
                return@firstNotNullOfOrNull ItemStack(material, amount).apply {
                    durability = alias.durability
                }
            }

            null
        }
    }

    fun isLocationLoaded(location: Location): Boolean {
        val world = location.world ?: return false
        return world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)
    }

    fun setBlockType(block: Block, material: Material) {
        if (setTypeWithPhysicsMethod != null) {
            setTypeWithPhysicsMethod.invoke(block, material, false)
        } else {
            block.type = material
        }
    }

    fun getBlockFacing(block: Block): BlockFace? {
        val blockData = getModernBlockData(block)
        if (blockData != null) {
            val facing = invokeBlockFace(blockData, "getFacing")
            if (facing != null) {
                return facing
            }
        }

        val legacyData = block.state.data
        return if (legacyData is Directional) legacyData.facing else null
    }

    fun applyFacing(block: Block, facing: BlockFace): Boolean {
        val blockData = getModernBlockData(block)
        if (blockData != null) {
            val faces = invokeFaces(blockData)
            val setFacingMethod = blockData.javaClass.methods.firstOrNull {
                it.name == "setFacing" && it.parameterCount == 1
            }
            if (setFacingMethod != null && (faces == null || faces.contains(facing))) {
                setFacingMethod.invoke(blockData, facing)
                setModernBlockData(block, blockData)
                return true
            }
        }

        val state = block.state
        val legacyData = state.data
        if (legacyData is Directional) {
            legacyData.setFacingDirection(facing)
            state.data = legacyData
            state.update(true, false)
            return true
        }
        return false
    }

    fun setLit(block: Block, lit: Boolean): Boolean {
        val blockData = getModernBlockData(block)
        if (blockData != null) {
            val setLitMethod = blockData.javaClass.methods.firstOrNull {
                it.name == "setLit" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            if (setLitMethod != null) {
                setLitMethod.invoke(blockData, lit)
                setModernBlockData(block, blockData)
                return true
            }
        }

        val targetMaterial = when {
            lit && block.type.name == "FURNACE" -> matchMaterial("BURNING_FURNACE")
            !lit && block.type.name == "BURNING_FURNACE" -> matchMaterial("FURNACE")
            else -> null
        } ?: return false

        setBlockType(block, targetMaterial)
        return true
    }

    fun getHopperState(block: Block): HopperState? {
        val blockData = getModernBlockData(block)
        if (blockData != null) {
            val enabled = invokeBoolean(blockData, "isEnabled") ?: true
            val facing = invokeBlockFace(blockData, "getFacing")
            return HopperState(enabled, facing)
        }

        val legacyData = block.state.data as? LegacyHopper ?: return null
        return HopperState(legacyData.isActive, legacyData.facing)
    }

    fun disableBlockDrops(event: BlockBreakEvent) {
        setDropItemsMethod?.invoke(event, false)
    }

    fun getPersistentString(plugin: Plugin, meta: ItemMeta?, key: String): String? {
        if (meta == null || itemMetaPersistentDataMethod == null) {
            return null
        }
        val namespacedKey = createNamespacedKey(plugin, key) ?: return null
        val stringType = persistentStringType ?: return null
        val container = itemMetaPersistentDataMethod.invoke(meta) ?: return null
        return persistentDataGetMethod?.invoke(container, namespacedKey, stringType) as? String
    }

    fun setPersistentString(plugin: Plugin, meta: ItemMeta, key: String, value: String): Boolean {
        if (itemMetaPersistentDataMethod == null) {
            return false
        }
        val namespacedKey = createNamespacedKey(plugin, key) ?: return false
        val stringType = persistentStringType ?: return false
        val container = itemMetaPersistentDataMethod.invoke(meta) ?: return false
        persistentDataSetMethod?.invoke(container, namespacedKey, stringType, value)
        return true
    }

    fun getCustomModelData(meta: ItemMeta?): Int? {
        if (meta == null || hasCustomModelDataMethod == null || getCustomModelDataMethod == null) {
            return null
        }
        val hasCustomModelData = hasCustomModelDataMethod.invoke(meta) as? Boolean ?: return null
        if (!hasCustomModelData) {
            return null
        }
        return getCustomModelDataMethod.invoke(meta) as? Int
    }

    fun setCustomModelData(meta: ItemMeta, value: Int): Boolean {
        if (setCustomModelDataMethod == null) {
            return false
        }
        setCustomModelDataMethod.invoke(meta, value)
        return true
    }

    fun itemSignature(item: ItemStack): String {
        val meta = item.itemMeta
        val displayName = meta?.takeIf { it.hasDisplayName() }?.displayName.orEmpty()
        val lore = meta?.takeIf { it.hasLore() }?.lore?.joinToString("\u0000").orEmpty()
        val localizedName = meta?.takeIf { it.hasLocalizedName() }?.localizedName.orEmpty()
        val customModelData = getCustomModelData(meta) ?: 0
        return listOf(
            item.type.name,
            item.durability.toString(),
            displayName,
            lore,
            localizedName,
            customModelData.toString()
        ).joinToString("|")
    }

    fun itemKey(item: ItemStack?): String {
        return if (isEmpty(item)) "" else "${itemSignature(item!!)}:${item.amount}"
    }

    private fun createNamespacedKey(plugin: Plugin, key: String): Any? {
        return namespacedKeyConstructor?.newInstance(plugin, key)
    }

    private fun resolveDirectMaterial(name: String): Material? {
        return Material.matchMaterial(name) ?: runCatching { Material.valueOf(name) }.getOrNull()
    }

    private fun getModernBlockData(block: Block): Any? {
        return getBlockDataMethod?.invoke(block)
    }

    private fun setModernBlockData(block: Block, blockData: Any) {
        setBlockDataMethod?.invoke(block, blockData)
    }

    private fun invokeBlockFace(instance: Any, methodName: String): BlockFace? {
        val method = instance.javaClass.methods.firstOrNull {
            it.name == methodName &&
                it.parameterCount == 0 &&
                it.returnType == BlockFace::class.java
        } ?: return null
        return method.invoke(instance) as? BlockFace
    }

    private fun invokeBoolean(instance: Any, methodName: String): Boolean? {
        val method = instance.javaClass.methods.firstOrNull {
            it.name == methodName &&
                it.parameterCount == 0 &&
                it.returnType == Boolean::class.javaPrimitiveType
        } ?: return null
        return method.invoke(instance) as? Boolean
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeFaces(instance: Any): Set<BlockFace>? {
        val method = instance.javaClass.methods.firstOrNull {
            it.name == "getFaces" && it.parameterCount == 0
        } ?: return null
        return method.invoke(instance) as? Set<BlockFace>
    }
}

object SchedulerBridge {

    fun runLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        FoliaUtil.runLater(plugin, delayTicks, task)
    }

    fun runAsync(plugin: Plugin, task: Runnable) {
        FoliaUtil.runAsync(plugin, task)
    }

    fun runPlayerTaskLater(plugin: Plugin, player: Player, delayTicks: Long, task: Runnable) {
        try {
            val getScheduler = player.javaClass.methods.firstOrNull {
                it.name == "getScheduler" && it.parameterCount == 0
            }
            if (getScheduler != null) {
                val scheduler = getScheduler.invoke(player)
                val runDelayed = scheduler.javaClass.methods.firstOrNull {
                    it.name == "runDelayed" && it.parameterCount == 4
                }
                if (runDelayed != null) {
                    val consumerType = runDelayed.parameterTypes[1]
                    val consumer = java.lang.reflect.Proxy.newProxyInstance(
                        consumerType.classLoader,
                        arrayOf(consumerType)
                    ) { _, method, _ ->
                        if (method.name == "accept") {
                            task.run()
                        }
                        null
                    }
                    runDelayed.invoke(scheduler, plugin, consumer, null, delayTicks)
                    return
                }
            }
        } catch (_: Throwable) {
        }
        runLater(plugin, delayTicks, task)
    }
}

object FoliaUtil {

    class TaskHandle internal constructor(private val cancelAction: (() -> Unit)?) {
        fun cancel() {
            cancelAction?.invoke()
        }
    }

    val isFolia: Boolean
        get() = ServerCompat.isFolia

    private val globalRegionScheduler by lazy {
        if (!isFolia) {
            null
        } else {
            runCatching {
                Bukkit.getServer().javaClass.getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer())
            }.getOrNull()
        }
    }

    private val globalRunDelayedMethod by lazy {
        globalRegionScheduler?.javaClass?.methods?.firstOrNull {
            it.name == "runDelayed" && it.parameterCount == 3
        }
    }

    private val globalRunFixedRateMethod by lazy {
        globalRegionScheduler?.javaClass?.methods?.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 4
        }
    }

    private val asyncScheduler by lazy {
        if (!isFolia) {
            null
        } else {
            runCatching {
                Bukkit.getServer().javaClass.getMethod("getAsyncScheduler").invoke(Bukkit.getServer())
            }.getOrNull()
        }
    }

    private val asyncRunNowMethod by lazy {
        asyncScheduler?.javaClass?.methods?.firstOrNull {
            it.name == "runNow" && it.parameterCount == 2
        }
    }

    private val regionScheduler by lazy {
        if (!isFolia) {
            null
        } else {
            runCatching {
                Bukkit.getServer().javaClass.getMethod("getRegionScheduler").invoke(Bukkit.getServer())
            }.getOrNull()
        }
    }

    private val regionExecuteMethod by lazy {
        regionScheduler?.javaClass?.methods?.firstOrNull {
            it.name == "execute" && it.parameterCount == 3
        }
    }

    private val entitySchedulerGetter by lazy {
        Entity::class.java.methods.firstOrNull {
            it.name == "getScheduler" && it.parameterCount == 0
        }
    }

    private val entityExecuteMethods = ConcurrentHashMap<Class<*>, Method?>()
    private val scheduledTaskCancelMethods = ConcurrentHashMap<Class<*>, Method?>()

    fun runLater(plugin: Plugin, delayTicks: Long, action: Runnable): TaskHandle {
        if (!isFolia) {
            val task = Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks)
            return TaskHandle { task.cancel() }
        }

        var scheduledTask: Any? = null
        val consumer = Consumer<Any?> { task ->
            if (scheduledTask == null && task != null) {
                scheduledTask = task
            }
            action.run()
        }

        val returnedTask = invokeGlobal(globalRunDelayedMethod, plugin, consumer, delayTicks)
        if (returnedTask != null) {
            scheduledTask = returnedTask
        }
        if (scheduledTask != null) {
            return TaskHandle { cancelScheduledTask(scheduledTask) }
        }

        val task = Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks)
        return TaskHandle { task.cancel() }
    }

    fun runRepeating(plugin: Plugin, initialDelay: Long, period: Long, action: Runnable): TaskHandle {
        if (!isFolia) {
            val task = Bukkit.getScheduler().runTaskTimer(plugin, action, initialDelay, period)
            return TaskHandle { task.cancel() }
        }

        var scheduledTask: Any? = null
        val consumer = Consumer<Any?> { task ->
            if (scheduledTask == null && task != null) {
                scheduledTask = task
            }
            action.run()
        }

        val returnedTask = invokeGlobal(globalRunFixedRateMethod, plugin, consumer, initialDelay, period)
        if (returnedTask != null) {
            scheduledTask = returnedTask
        }
        if (scheduledTask != null) {
            return TaskHandle { cancelScheduledTask(scheduledTask) }
        }

        val task = Bukkit.getScheduler().runTaskTimer(plugin, action, initialDelay, period)
        return TaskHandle { task.cancel() }
    }

    fun runAsync(plugin: Plugin, action: Runnable): TaskHandle {
        if (!isFolia) {
            val task = Bukkit.getScheduler().runTaskAsynchronously(plugin, action)
            return TaskHandle { task.cancel() }
        }

        var scheduledTask: Any? = null
        val consumer = Consumer<Any?> { task ->
            if (scheduledTask == null && task != null) {
                scheduledTask = task
            }
            action.run()
        }

        val returnedTask = invokeAsync(asyncRunNowMethod, plugin, consumer)
        if (returnedTask != null) {
            scheduledTask = returnedTask
        }
        if (scheduledTask != null) {
            return TaskHandle { cancelScheduledTask(scheduledTask) }
        }

        val future = CompletableFuture.runAsync(action)
        return TaskHandle { future.cancel(true) }
    }

    fun runAtLocation(plugin: Plugin, location: Location, action: Runnable) {
        if (!isFolia) {
            action.run()
            return
        }

        val scheduler = regionScheduler
        val executeMethod = regionExecuteMethod
        if (scheduler == null || executeMethod == null) {
            action.run()
            return
        }

        runCatching {
            executeMethod.invoke(scheduler, plugin, location, action)
        }.onFailure {
            action.run()
        }
    }

    fun runAtEntity(plugin: Plugin, entity: Entity, action: Runnable) {
        if (!isFolia) {
            action.run()
            return
        }

        val getter = entitySchedulerGetter
        if (getter == null) {
            action.run()
            return
        }

        val scheduler = runCatching { getter.invoke(entity) }.getOrNull()
        if (scheduler == null) {
            action.run()
            return
        }

        val executeMethod = entityExecuteMethods.computeIfAbsent(scheduler.javaClass) { schedulerClass ->
            schedulerClass.methods.firstOrNull {
                it.name == "execute" && it.parameterCount == 4
            }
        }

        if (executeMethod == null) {
            action.run()
            return
        }

        runCatching {
            executeMethod.invoke(scheduler, plugin, action, null, 1L)
        }.onFailure {
            action.run()
        }
    }

    private fun invokeGlobal(method: Method?, vararg args: Any?): Any? {
        val scheduler = globalRegionScheduler ?: return null
        val targetMethod = method ?: return null
        return runCatching {
            targetMethod.invoke(scheduler, *args)
        }.getOrNull()
    }

    private fun invokeAsync(method: Method?, vararg args: Any?): Any? {
        val scheduler = asyncScheduler ?: return null
        val targetMethod = method ?: return null
        return runCatching {
            targetMethod.invoke(scheduler, *args)
        }.getOrNull()
    }

    private fun cancelScheduledTask(task: Any?) {
        val scheduledTask = task ?: return
        val cancelMethod = scheduledTaskCancelMethods.computeIfAbsent(scheduledTask.javaClass) { taskClass ->
            (taskClass.declaredMethods.asSequence() + taskClass.methods.asSequence())
                .firstOrNull {
                    it.name == "cancel" && it.parameterCount == 0
                }
                ?.apply {
                    runCatching { trySetAccessible() }
                }
        }
        runCatching {
            cancelMethod?.invoke(scheduledTask)
        }
    }
}
