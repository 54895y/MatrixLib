package com.y54895.matrixlib.api.hologram

import org.bukkit.Location

enum class MatrixHologramAnchor {
    EXACT,
    BLOCK_CENTER
}

data class MatrixHologramRequest(
    val namespace: String,
    val id: String,
    val baseLocation: Location,
    val lines: List<String>,
    val anchor: MatrixHologramAnchor = MatrixHologramAnchor.BLOCK_CENTER,
    val heightOverride: Double? = null
) {

    fun qualifiedId(): String {
        val resolvedNamespace = namespace.trim().ifBlank { "matrix" }.lowercase()
        return "${resolvedNamespace}_${id.trim()}"
    }
}
