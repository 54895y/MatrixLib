package com.y54895.matrixlib.api.hologram.internal

import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.warning
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal class DecentHologramsAdapter private constructor(
    private val createMethod: Method,
    private val getHologramMethod: Method,
    private val setLinesMethod: Method,
    private val moveMethod: Method?,
    private val removeByNameMethod: Method?,
    private val deleteMethod: Method,
    private val setLocationMethod: Method?
) : MatrixHologramAdapter {

    override val name: String = "DecentHolograms"

    private val holograms = ConcurrentHashMap<String, Any>()

    override fun createOrUpdate(entry: MatrixRenderedHologram) {
        if (entry.lines.isEmpty()) {
            remove(entry.qualifiedId)
            return
        }

        val existing = holograms[entry.qualifiedId] ?: findHologram(entry.qualifiedId)
        if (existing != null) {
            runCatching {
                move(existing, entry.location)
                setLinesMethod.invoke(null, existing, entry.lines)
                holograms[entry.qualifiedId] = existing
            }.onFailure {
                warning("MatrixLib failed to update DecentHolograms hologram ${entry.qualifiedId}: ${it.message}")
                remove(entry.qualifiedId)
                create(entry)
            }
            return
        }

        create(entry)
    }

    override fun remove(qualifiedId: String) {
        val hologram = holograms.remove(qualifiedId) ?: findHologram(qualifiedId)
        runCatching {
            when {
                removeByNameMethod != null -> removeByNameMethod.invoke(null, qualifiedId)
                hologram != null -> deleteMethod.invoke(hologram)
            }
        }.onFailure {
            warning("MatrixLib failed to remove DecentHolograms hologram $qualifiedId: ${it.message}")
        }
    }

    override fun cleanup() {
        holograms.keys.toList().forEach(::remove)
        holograms.clear()
    }

    private fun create(entry: MatrixRenderedHologram) {
        runCatching {
            val hologram = createMethod.invoke(null, entry.qualifiedId, entry.location, entry.lines) ?: return@runCatching
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to create DecentHolograms hologram ${entry.qualifiedId}: ${it.message}")
        }
    }

    private fun findHologram(id: String): Any? {
        return runCatching {
            getHologramMethod.invoke(null, id)
        }.getOrNull()
    }

    private fun move(hologram: Any, location: Location) {
        when {
            moveMethod != null -> moveMethod.invoke(null, hologram, location)
            setLocationMethod != null -> setLocationMethod.invoke(hologram, location)
        }
    }

    companion object {

        fun createOrNull(): DecentHologramsAdapter? {
            val plugin = Bukkit.getPluginManager().getPlugin("DecentHolograms")
                ?.takeIf { it.isEnabled } ?: return null
            val loader = plugin.javaClass.classLoader

            return runCatching {
                val apiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI", false, loader)
                val hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram", false, loader)

                val createMethod = apiClass.methods.first { method ->
                    method.name == "createHologram" &&
                        method.parameterCount == 3 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == Location::class.java
                }
                val getHologramMethod = apiClass.methods.first { method ->
                    method.name == "getHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                val setLinesMethod = apiClass.methods.first { method ->
                    method.name == "setHologramLines" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass)
                }
                val moveMethod = apiClass.methods.firstOrNull { method ->
                    method.name == "moveHologram" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass) &&
                        method.parameterTypes[1] == Location::class.java
                }
                val removeByNameMethod = apiClass.methods.firstOrNull { method ->
                    method.name == "removeHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                val deleteMethod = hologramClass.methods.first { method ->
                    method.name == "delete" && method.parameterCount == 0
                }
                val setLocationMethod = hologramClass.methods.firstOrNull { method ->
                    method.name == "setLocation" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Location::class.java
                }

                DecentHologramsAdapter(
                    createMethod = createMethod,
                    getHologramMethod = getHologramMethod,
                    setLinesMethod = setLinesMethod,
                    moveMethod = moveMethod,
                    removeByNameMethod = removeByNameMethod,
                    deleteMethod = deleteMethod,
                    setLocationMethod = setLocationMethod
                )
            }.onFailure {
                warning("MatrixLib could not bootstrap DecentHolograms support: ${it.message}")
            }.getOrNull()
        }
    }
}
