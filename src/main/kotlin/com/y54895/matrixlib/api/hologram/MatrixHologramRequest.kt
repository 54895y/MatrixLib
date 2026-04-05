package com.y54895.matrixlib.api.hologram

import org.bukkit.Location

/**
 * Anchor mode used when converting a logical hologram request to a rendered location.
 */
enum class MatrixHologramAnchor {
    EXACT,
    BLOCK_CENTER
}

/**
 * Immutable hologram update request consumed by [MatrixHolograms].
 */
data class MatrixHologramRequest(
    val namespace: String,
    val id: String,
    val baseLocation: Location,
    val lines: List<String>,
    val anchor: MatrixHologramAnchor = MatrixHologramAnchor.BLOCK_CENTER,
    val heightOverride: Double? = null
) {

    /**
     * Build the provider-facing hologram id.
     */
    fun qualifiedId(): String {
        val resolvedNamespace = namespace.trim().ifBlank { "matrix" }.lowercase()
        return "${resolvedNamespace}_${id.trim()}"
    }
}
