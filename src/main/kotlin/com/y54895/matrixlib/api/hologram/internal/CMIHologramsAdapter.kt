package com.y54895.matrixlib.api.hologram.internal

import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.warning
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal class CMIHologramsAdapter private constructor(
    private val instanceMethod: Method,
    private val managerGetter: Method,
    private val addHologramMethod: Method,
    private val getByNameMethod: Method,
    private val removeByManagerMethod: Method?,
    private val hologramConstructor: Constructor<*>,
    private val setLinesMethod: Method,
    private val setLocationMethod: Method,
    private val updateMethod: Method,
    private val removeMethod: Method?,
    private val setSaveToFileMethod: Method?
) : MatrixHologramAdapter {

    override val name: String = "CMI"

    private val holograms = ConcurrentHashMap<String, Any>()

    override fun createOrUpdate(entry: MatrixRenderedHologram) {
        if (entry.lines.isEmpty()) {
            remove(entry.qualifiedId)
            return
        }

        val existing = holograms[entry.qualifiedId] ?: findHologram(entry.qualifiedId)
        if (existing != null) {
            runCatching {
                setLinesMethod.invoke(existing, entry.lines)
                setLocationMethod.invoke(existing, entry.location)
                updateMethod.invoke(existing)
                holograms[entry.qualifiedId] = existing
            }.onFailure {
                warning("MatrixLib failed to update CMI hologram ${entry.qualifiedId}: ${it.message}")
                remove(entry.qualifiedId)
                create(entry)
            }
            return
        }

        create(entry)
    }

    override fun remove(qualifiedId: String) {
        val manager = manager() ?: return
        val hologram = holograms.remove(qualifiedId) ?: findHologram(qualifiedId)
        runCatching {
            when {
                hologram != null && removeByManagerMethod != null -> removeByManagerMethod.invoke(manager, hologram)
                hologram != null && removeMethod != null -> removeMethod.invoke(hologram)
            }
        }.onFailure {
            warning("MatrixLib failed to remove CMI hologram $qualifiedId: ${it.message}")
        }
    }

    override fun cleanup() {
        holograms.keys.toList().forEach(::remove)
        holograms.clear()
    }

    private fun create(entry: MatrixRenderedHologram) {
        val manager = manager() ?: return
        runCatching {
            val hologram = hologramConstructor.newInstance(entry.qualifiedId, entry.location)
            setLinesMethod.invoke(hologram, entry.lines)
            setSaveToFileMethod?.invoke(hologram, false)
            addHologramMethod.invoke(manager, hologram)
            updateMethod.invoke(hologram)
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to create CMI hologram ${entry.qualifiedId}: ${it.message}")
        }
    }

    private fun manager(): Any? {
        return runCatching {
            val instance = instanceMethod.invoke(null)
            managerGetter.invoke(instance)
        }.getOrNull()
    }

    private fun findHologram(id: String): Any? {
        val manager = manager() ?: return null
        return runCatching {
            getByNameMethod.invoke(manager, id)
        }.getOrNull()
    }

    companion object {

        fun createOrNull(): CMIHologramsAdapter? {
            val plugin = Bukkit.getPluginManager().getPlugin("CMI")
                ?.takeIf { it.isEnabled } ?: return null
            val loader = plugin.javaClass.classLoader

            return runCatching {
                val cmiClass = Class.forName("com.Zrips.CMI.CMI", false, loader)
                val hologramClass = Class.forName("com.Zrips.CMI.Modules.Holograms.CMIHologram", false, loader)

                val instanceMethod = cmiClass.getMethod("getInstance")
                val managerGetter = cmiClass.methods.first { method ->
                    method.name == "getHologramManager" && method.parameterCount == 0
                }
                val managerClass = managerGetter.returnType

                val addHologramMethod = managerClass.methods.first { method ->
                    method.name == "addHologram" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass)
                }
                val getByNameMethod = managerClass.methods.first { method ->
                    method.name == "getByName" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                val removeByManagerMethod = managerClass.methods.firstOrNull { method ->
                    method.name == "removeHolo" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(hologramClass)
                }

                val hologramConstructor = hologramClass.getConstructor(String::class.java, Location::class.java)
                val setLinesMethod = hologramClass.methods.first { method ->
                    method.name == "setLines" && method.parameterCount == 1
                }
                val setLocationMethod = hologramClass.methods.first { method ->
                    method.name == "setLoc" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Location::class.java
                }
                val updateMethod = hologramClass.methods.first { method ->
                    method.name == "update" && method.parameterCount == 0
                }
                val removeMethod = hologramClass.methods.firstOrNull { method ->
                    method.name == "remove" && method.parameterCount == 0
                }
                val setSaveToFileMethod = hologramClass.methods.firstOrNull { method ->
                    method.name == "setSaveToFile" &&
                        method.parameterCount == 1 &&
                        isBooleanParameter(method)
                }

                CMIHologramsAdapter(
                    instanceMethod = instanceMethod,
                    managerGetter = managerGetter,
                    addHologramMethod = addHologramMethod,
                    getByNameMethod = getByNameMethod,
                    removeByManagerMethod = removeByManagerMethod,
                    hologramConstructor = hologramConstructor,
                    setLinesMethod = setLinesMethod,
                    setLocationMethod = setLocationMethod,
                    updateMethod = updateMethod,
                    removeMethod = removeMethod,
                    setSaveToFileMethod = setSaveToFileMethod
                )
            }.onFailure {
                warning("MatrixLib could not bootstrap CMI hologram support: ${it.message}")
            }.getOrNull()
        }

        private fun isBooleanParameter(method: Method): Boolean {
            val parameterType = method.parameterTypes.singleOrNull() ?: return false
            return parameterType == Boolean::class.javaPrimitiveType || parameterType == Boolean::class.javaObjectType
        }
    }
}
