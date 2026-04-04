package com.y54895.matrixlib.metrics

import com.y54895.matrixlib.api.economy.MatrixEconomy
import com.y54895.matrixlib.api.metrics.MatrixBStats
import taboolib.platform.BukkitPlugin

object BStatsMetrics {

    private const val PLUGIN_ID = 30557

    fun initialize() {
        MatrixBStats.initialize(
            BukkitPlugin.getInstance(),
            PLUGIN_ID,
            MatrixBStats.singleLine("configured_currency_count") {
                MatrixEconomy.configuredCurrencyCount()
            },
            MatrixBStats.advancedPie("currency_modes") {
                MatrixEconomy.currencyModeDistribution()
            },
            MatrixBStats.simplePie("currency_setup") {
                if (MatrixEconomy.configuredCurrencyCount() <= 1) {
                    "single"
                } else {
                    "multi"
                }
            }
        )
    }
}
