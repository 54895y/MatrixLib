package com.y54895.matrixlib.api.hologram.internal

import org.bukkit.Location

internal data class MatrixRenderedHologram(
    val qualifiedId: String,
    val location: Location,
    val lines: List<String>
)

internal interface MatrixHologramAdapter {

    val name: String

    fun createOrUpdate(entry: MatrixRenderedHologram)

    fun remove(qualifiedId: String)

    fun cleanup()
}
