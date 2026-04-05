package com.y54895.matrixlib.api.brand

/**
 * Shared branding descriptor used by Matrix console, text, and command surfaces.
 */
data class MatrixBranding(
    val displayName: String,
    val rootCommand: String,
    val adminCommand: String? = null,
    val runtimeName: String = displayName,
    val accentColor: String = "&b",
    val neutralColor: String = "&7",
    val bannerTitle: String = displayName
)
