package com.y54895.matrixlib.api.hologram.internal

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import taboolib.common.platform.function.warning
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

internal class FancyHologramsAdapter private constructor(
    private val bridge: ApiBridge
) : MatrixHologramAdapter {

    override val name: String = "FancyHolograms"

    private val holograms = ConcurrentHashMap<String, Any>()

    override fun createOrUpdate(entry: MatrixRenderedHologram) {
        if (entry.lines.isEmpty()) {
            remove(entry.qualifiedId)
            return
        }

        val hologram = holograms[entry.qualifiedId] ?: findHologram(entry.qualifiedId)
        if (hologram == null) {
            create(entry)
            return
        }

        runCatching {
            val hologramData = bridge.getDataMethod.invoke(hologram) ?: return@runCatching
            bridge.setLocationMethod.invoke(hologramData, entry.location)
            bridge.setTextMethod.invoke(hologramData, entry.lines)
            bridge.forceUpdateMethod.invoke(hologram)
            bridge.queueUpdateMethod.invoke(hologram)
            bridge.refreshForViewersMethod?.invoke(hologram)
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to update FancyHolograms hologram ${entry.qualifiedId}: ${it.message}")
            remove(entry.qualifiedId)
            create(entry)
        }
    }

    override fun remove(qualifiedId: String) {
        val manager = manager() ?: return
        val hologram = holograms.remove(qualifiedId) ?: findHologram(qualifiedId)
        runCatching {
            when {
                bridge.removeByNameMethod != null -> bridge.removeByNameMethod.invoke(manager, qualifiedId)
                hologram != null && bridge.removeByHologramMethod != null -> {
                    bridge.removeByHologramMethod.invoke(manager, hologram)
                }
            }
        }.onFailure {
            warning("MatrixLib failed to remove FancyHolograms hologram $qualifiedId: ${it.message}")
        }
    }

    override fun cleanup() {
        holograms.keys.toList().forEach(::remove)
        holograms.clear()
    }

    private fun create(entry: MatrixRenderedHologram) {
        val manager = manager() ?: return
        runCatching {
            val hologramData = bridge.textHologramDataConstructor.newInstance(entry.qualifiedId, entry.location)
            bridge.setTextMethod.invoke(hologramData, entry.lines)

            val hologram = bridge.createMethod.invoke(manager, hologramData) ?: return@runCatching
            bridge.setPersistent(hologram, hologramData)
            bridge.addHologramMethod.invoke(manager, hologram)
            bridge.forceUpdateMethod.invoke(hologram)
            bridge.queueUpdateMethod.invoke(hologram)
            bridge.refreshForViewersMethod?.invoke(hologram)
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to create FancyHolograms hologram ${entry.qualifiedId}: ${it.message}")
        }
    }

    private fun manager(): Any? {
        return runCatching {
            bridge.hologramManagerGetter.invoke(bridge.managerOwner)
        }.getOrNull()
    }

    private fun findHologram(id: String): Any? {
        val manager = manager() ?: return null
        return runCatching {
            when (val result = bridge.getHologramMethod.invoke(manager, id)) {
                is Optional<*> -> result.orElse(null)
                else -> result
            }
        }.getOrNull()
    }

    companion object {

        fun createOrNull(): FancyHologramsAdapter? {
            val plugin = Bukkit.getPluginManager().getPlugin("FancyHolograms")
                ?.takeIf { it.isEnabled } ?: return null

            val bridge = listOf(
                PackageLayout(
                    basePackage = "de.oliver.fancyholograms",
                    apiPackage = "de.oliver.fancyholograms.api"
                ),
                PackageLayout(
                    basePackage = "com.fancyinnovations.fancyholograms",
                    apiPackage = "com.fancyinnovations.fancyholograms.api"
                )
            ).firstNotNullOfOrNull { layout ->
                buildBridge(plugin, layout)
            }

            if (bridge == null) {
                warning("MatrixLib could not resolve a compatible FancyHolograms API bridge.")
                return null
            }
            return FancyHologramsAdapter(bridge)
        }

        private fun buildBridge(plugin: Plugin, layout: PackageLayout): ApiBridge? {
            val loader = plugin.javaClass.classLoader

            return runCatching {
                val textHologramDataClass = Class.forName("${layout.apiPackage}.data.TextHologramData", false, loader)
                val hologramClass = Class.forName("${layout.apiPackage}.hologram.Hologram", false, loader)
                val hologramDataClass = Class.forName("${layout.apiPackage}.data.HologramData", false, loader)

                val managerOwner = resolveManagerOwner(plugin, layout.basePackage, loader)
                val hologramManagerGetter = managerOwner.javaClass.methods.firstOrNull { method ->
                    method.name == "getHologramManager" && method.parameterCount == 0
                } ?: throw IllegalStateException("getHologramManager() is not available")
                val managerClass = hologramManagerGetter.returnType

                val getHologramMethod = managerClass.getMethod("getHologram", String::class.java)
                val createMethod = managerClass.methods.first { method ->
                    method.name == "create" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(hologramDataClass)
                }
                val addHologramMethod = managerClass.methods.first { method ->
                    method.name == "addHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass)
                }
                val removeByNameMethod = managerClass.methods.firstOrNull { method ->
                    method.name == "removeHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                val removeByHologramMethod = managerClass.methods.firstOrNull { method ->
                    method.name == "removeHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass)
                }

                val textHologramDataConstructor = textHologramDataClass.getConstructor(
                    String::class.java,
                    Location::class.java
                )
                val setTextMethod = textHologramDataClass.methods.first { method ->
                    method.name == "setText" && method.parameterCount == 1
                }
                val setLocationMethod = hologramDataClass.getMethod("setLocation", Location::class.java)
                val getDataMethod = hologramClass.getMethod("getData")
                val queueUpdateMethod = hologramClass.getMethod("queueUpdate")
                val forceUpdateMethod = hologramClass.getMethod("forceUpdate")
                val refreshForViewersMethod = hologramClass.methods.firstOrNull { method ->
                    method.name == "refreshForViewers" && method.parameterCount == 0
                }
                val setPersistentOnHologramMethod = hologramClass.methods.firstOrNull { method ->
                    method.name == "setPersistent" &&
                        method.parameterCount == 1 &&
                        isBooleanParameter(method)
                }
                val setPersistentOnDataMethod = hologramDataClass.methods.firstOrNull { method ->
                    method.name == "setPersistent" &&
                        method.parameterCount == 1 &&
                        isBooleanParameter(method)
                }

                ApiBridge(
                    managerOwner = managerOwner,
                    hologramManagerGetter = hologramManagerGetter,
                    getHologramMethod = getHologramMethod,
                    createMethod = createMethod,
                    addHologramMethod = addHologramMethod,
                    removeByNameMethod = removeByNameMethod,
                    removeByHologramMethod = removeByHologramMethod,
                    textHologramDataConstructor = textHologramDataConstructor,
                    setTextMethod = setTextMethod,
                    setLocationMethod = setLocationMethod,
                    getDataMethod = getDataMethod,
                    queueUpdateMethod = queueUpdateMethod,
                    forceUpdateMethod = forceUpdateMethod,
                    refreshForViewersMethod = refreshForViewersMethod,
                    setPersistentOnHologramMethod = setPersistentOnHologramMethod,
                    setPersistentOnDataMethod = setPersistentOnDataMethod
                )
            }.onFailure {
                warning("MatrixLib FancyHolograms bootstrap failed for ${layout.apiPackage}: ${it.message}")
            }.getOrNull()
        }

        private fun resolveManagerOwner(plugin: Plugin, basePackage: String, loader: ClassLoader): Any {
            val pluginSingleton = listOf(
                "$basePackage.FancyHologramsPlugin",
                "$basePackage.FancyHolograms"
            ).firstNotNullOfOrNull { className ->
                runCatching { Class.forName(className, false, loader) }.getOrNull()
                    ?.methods
                    ?.firstOrNull { method ->
                        method.name == "get" &&
                            method.parameterCount == 0 &&
                            Modifier.isStatic(method.modifiers)
                    }
                    ?.let { method -> runCatching { method.invoke(null) }.getOrNull() }
            }
            return pluginSingleton ?: plugin
        }

        private fun isBooleanParameter(method: Method): Boolean {
            val parameterType = method.parameterTypes.singleOrNull() ?: return false
            return parameterType == Boolean::class.javaPrimitiveType || parameterType == Boolean::class.javaObjectType
        }
    }

    private data class PackageLayout(
        val basePackage: String,
        val apiPackage: String
    )

    private data class ApiBridge(
        val managerOwner: Any,
        val hologramManagerGetter: Method,
        val getHologramMethod: Method,
        val createMethod: Method,
        val addHologramMethod: Method,
        val removeByNameMethod: Method?,
        val removeByHologramMethod: Method?,
        val textHologramDataConstructor: Constructor<*>,
        val setTextMethod: Method,
        val setLocationMethod: Method,
        val getDataMethod: Method,
        val queueUpdateMethod: Method,
        val forceUpdateMethod: Method,
        val refreshForViewersMethod: Method?,
        val setPersistentOnHologramMethod: Method?,
        val setPersistentOnDataMethod: Method?
    ) {

        fun setPersistent(hologram: Any, hologramData: Any) {
            when {
                setPersistentOnHologramMethod != null -> setPersistentOnHologramMethod.invoke(hologram, false)
                setPersistentOnDataMethod != null -> setPersistentOnDataMethod.invoke(hologramData, false)
            }
        }
    }
}
