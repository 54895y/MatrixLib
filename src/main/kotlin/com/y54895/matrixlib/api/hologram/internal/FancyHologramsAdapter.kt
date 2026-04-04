package com.y54895.matrixlib.api.hologram.internal

import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.data.TextHologramData
import de.oliver.fancyholograms.api.hologram.Hologram
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap

internal class FancyHologramsAdapter : MatrixHologramAdapter {

    override val name: String = "FancyHolograms"

    private val holograms = ConcurrentHashMap<String, Hologram>()
    private val hologramManager = FancyHologramsPlugin.get().hologramManager

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
            val hologramData = hologram.data
            hologramData.setLocation(entry.location)
            if (hologramData is TextHologramData) {
                hologramData.setText(entry.lines)
            }
            hologram.forceUpdate()
            hologram.queueUpdate()
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to update FancyHolograms hologram ${entry.qualifiedId}: ${it.message}")
            remove(entry.qualifiedId)
            create(entry)
        }
    }

    override fun remove(qualifiedId: String) {
        val hologram = holograms.remove(qualifiedId) ?: findHologram(qualifiedId)
        runCatching {
            if (hologram != null) {
                hologramManager.removeHologram(hologram)
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
        runCatching {
            val hologramData = TextHologramData(entry.qualifiedId, entry.location)
            hologramData.setText(entry.lines)

            val hologram = hologramManager.create(hologramData) ?: return@runCatching
            hologramManager.addHologram(hologram)
            hologram.forceUpdate()
            hologram.queueUpdate()
            holograms[entry.qualifiedId] = hologram
        }.onFailure {
            warning("MatrixLib failed to create FancyHolograms hologram ${entry.qualifiedId}: ${it.message}")
        }
    }

    private fun findHologram(id: String): Hologram? {
        return runCatching {
            hologramManager.getHologram(id).orElse(null)
        }.getOrNull()
    }

    companion object {

        fun createOrNull(): FancyHologramsAdapter? {
            val plugin = Bukkit.getPluginManager().getPlugin("FancyHolograms")
                ?.takeIf { it.isEnabled } ?: return null

            return runCatching {
                FancyHologramsAdapter()
            }.onFailure {
                warning("MatrixLib FancyHolograms bootstrap failed: ${it.message}")
            }.getOrNull()
        }
    }
}