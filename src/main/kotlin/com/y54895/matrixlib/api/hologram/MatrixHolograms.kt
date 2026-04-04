package com.y54895.matrixlib.api.hologram

import com.y54895.matrixlib.api.hologram.internal.CMIHologramsAdapter
import com.y54895.matrixlib.api.hologram.internal.DecentHologramsAdapter
import com.y54895.matrixlib.api.hologram.internal.FancyHologramsAdapter
import com.y54895.matrixlib.api.hologram.internal.MatrixHologramAdapter
import com.y54895.matrixlib.api.hologram.internal.MatrixRenderedHologram
import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MatrixHolograms {

    private const val resourcePath = "Hologram/config.yml"

    private val providerSpecs = listOf(
        ProviderSpec(
            pluginName = "DecentHolograms",
            aliases = setOf("decentholograms", "decent"),
            factory = { DecentHologramsAdapter.createOrNull() }
        ),
        ProviderSpec(
            pluginName = "FancyHolograms",
            aliases = setOf("fancyholograms", "fancy"),
            factory = { FancyHologramsAdapter.createOrNull() }
        ),
        ProviderSpec(
            pluginName = "CMI",
            aliases = setOf("cmi"),
            factory = { CMIHologramsAdapter.createOrNull() }
        )
    )

    private val requests = ConcurrentHashMap<String, MatrixHologramRequest>()

    @Volatile
    private var settings = HologramSettings(
        providerOrder = providerSpecs.map(ProviderSpec::pluginName)
    )

    @Volatile
    private var adapter: MatrixHologramAdapter? = null

    @Volatile
    private var adapterName: String? = null

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        adapter?.cleanup()
        adapter = null
        adapterName = null
        requests.clear()
    }

    fun reload() {
        settings = loadSettings()
        refreshAdapter(rebuildCached = true, force = true)
    }

    fun configFile(): File {
        val plugin = BukkitPlugin.getInstance()
        MatrixResourceFiles.saveResourceIfAbsent(plugin, resourcePath)
        return MatrixResourceFiles.dataFile(plugin, resourcePath)
    }

    fun createOrUpdate(request: MatrixHologramRequest) {
        val qualifiedId = request.qualifiedId()
        if (request.lines.isEmpty()) {
            remove(request.namespace, request.id)
            return
        }
        requests[qualifiedId] = request
        ensureAdapter()
        adapter?.createOrUpdate(render(request))
    }

    fun remove(namespace: String, id: String) {
        val qualifiedId = qualifiedId(namespace, id)
        requests.remove(qualifiedId)
        adapter?.remove(qualifiedId)
    }

    fun clearNamespace(namespace: String) {
        val prefix = "${namespace.trim().ifBlank { "matrix" }.lowercase()}_"
        val keys = requests.keys.filter { it.startsWith(prefix) }
        keys.forEach { qualifiedId ->
            requests.remove(qualifiedId)
            adapter?.remove(qualifiedId)
        }
    }

    fun defaultHeight(): Double {
        return settings.defaultHeight
    }

    fun providerSummary(): String {
        return if (!settings.enabled) {
            "disabled"
        } else {
            adapterName ?: "none"
        }
    }

    fun isEnabled(): Boolean {
        return settings.enabled
    }

    @SubscribeEvent
    fun onPluginEnable(event: PluginEnableEvent) {
        if (providerSpecs.any { it.pluginName.equals(event.plugin.name, ignoreCase = true) }) {
            refreshAdapter(rebuildCached = true)
        }
    }

    @SubscribeEvent
    fun onPluginDisable(event: PluginDisableEvent) {
        if (providerSpecs.any { it.pluginName.equals(event.plugin.name, ignoreCase = true) }) {
            refreshAdapter(rebuildCached = true, force = true)
        }
    }

    private fun ensureAdapter() {
        if (adapter == null && settings.enabled) {
            refreshAdapter(rebuildCached = false)
        }
    }

    private fun refreshAdapter(rebuildCached: Boolean, force: Boolean = false) {
        val previousName = adapterName

        if (!settings.enabled) {
            adapter?.cleanup()
            adapter = null
            adapterName = null
            if (previousName != null || force) {
                info("MatrixLib holograms are disabled in ${configFile().name}.")
            }
            return
        }

        val nextAdapter = selectAdapter()
        val nextName = nextAdapter?.name

        if (!force && previousName == nextName && adapter != null) {
            if (rebuildCached) {
                rebuildCachedRequests(adapter!!)
            }
            return
        }

        adapter?.cleanup()
        adapter = nextAdapter
        adapterName = nextName

        if (previousName != nextName || force) {
            when (nextName) {
                "DecentHolograms" -> info("MatrixLib hologram bridge: DecentHolograms")
                "FancyHolograms" -> info("MatrixLib hologram bridge: FancyHolograms")
                "CMI" -> info("MatrixLib hologram bridge: CMI")
                null -> info("MatrixLib hologram bridge: unavailable")
            }
        }

        if (rebuildCached && nextAdapter != null) {
            rebuildCachedRequests(nextAdapter)
        }
    }

    private fun rebuildCachedRequests(adapter: MatrixHologramAdapter) {
        requests.values.forEach { request ->
            adapter.createOrUpdate(render(request))
        }
    }

    private fun selectAdapter(): MatrixHologramAdapter? {
        val forced = settings.forceProvider
        if (forced != null) {
            val spec = findProviderSpec(forced)
            if (spec == null) {
                warning("MatrixLib hologram force-provider '$forced' is unknown.")
                return null
            }
            if (!isPluginEnabled(spec.pluginName)) {
                warning("MatrixLib hologram force-provider '${spec.pluginName}' is not enabled.")
                return null
            }
            return spec.factory()
        }

        orderedProviderSpecs().forEach { spec ->
            if (!isPluginEnabled(spec.pluginName)) {
                return@forEach
            }
            val selected = spec.factory()
            if (selected != null) {
                return selected
            }
        }
        return null
    }

    private fun orderedProviderSpecs(): List<ProviderSpec> {
        if (settings.providerOrder.isEmpty()) {
            return providerSpecs
        }

        val ordered = mutableListOf<ProviderSpec>()
        settings.providerOrder.forEach { configured ->
            findProviderSpec(configured)?.let { spec ->
                if (ordered.none { it.pluginName == spec.pluginName }) {
                    ordered += spec
                }
            }
        }
        providerSpecs.forEach { spec ->
            if (ordered.none { it.pluginName == spec.pluginName }) {
                ordered += spec
            }
        }
        return ordered
    }

    private fun render(request: MatrixHologramRequest): MatrixRenderedHologram {
        val height = request.heightOverride ?: settings.defaultHeight
        val location = request.baseLocation.clone().add(
            if (request.anchor == MatrixHologramAnchor.BLOCK_CENTER) 0.5 else 0.0,
            height,
            if (request.anchor == MatrixHologramAnchor.BLOCK_CENTER) 0.5 else 0.0
        )
        return MatrixRenderedHologram(
            qualifiedId = request.qualifiedId(),
            location = location,
            lines = request.lines.map(MatrixText::color)
        )
    }

    private fun loadSettings(): HologramSettings {
        val yaml = YamlConfiguration.loadConfiguration(configFile())
        val providerOrder = yaml.getStringList("provider-order")
            .mapNotNull { line -> line?.trim()?.takeIf(String::isNotBlank) }

        return HologramSettings(
            enabled = yaml.getBoolean("enabled", true),
            defaultHeight = yaml.getDouble("default-height", 1.5),
            forceProvider = yaml.getString("force-provider")?.trim()?.takeIf(String::isNotBlank),
            providerOrder = if (providerOrder.isEmpty()) providerSpecs.map(ProviderSpec::pluginName) else providerOrder,
            debug = yaml.getBoolean("debug", false)
        )
    }

    private fun findProviderSpec(name: String): ProviderSpec? {
        val normalized = name.trim().lowercase()
        return providerSpecs.firstOrNull { spec ->
            spec.pluginName.equals(name, ignoreCase = true) || spec.aliases.contains(normalized)
        }
    }

    private fun isPluginEnabled(name: String): Boolean {
        return Bukkit.getPluginManager().getPlugin(name)?.isEnabled == true
    }

    private fun qualifiedId(namespace: String, id: String): String {
        val resolvedNamespace = namespace.trim().ifBlank { "matrix" }.lowercase()
        return "${resolvedNamespace}_${id.trim()}"
    }

    private data class ProviderSpec(
        val pluginName: String,
        val aliases: Set<String>,
        val factory: () -> MatrixHologramAdapter?
    )

    private data class HologramSettings(
        val enabled: Boolean = true,
        val defaultHeight: Double = 1.5,
        val forceProvider: String? = null,
        val providerOrder: List<String> = emptyList(),
        val debug: Boolean = false
    )
}
