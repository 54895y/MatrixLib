package com.y54895.matrixlib.api.metrics

import org.bstats.bukkit.Metrics
import org.bstats.charts.AdvancedPie
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.plugin.Plugin
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Strongly typed chart descriptor used by [MatrixBStats].
 */
sealed class MatrixMetricsChart(val chartId: String) {

    class SimplePieChart internal constructor(
        chartId: String,
        val supplier: Supplier<String>
    ) : MatrixMetricsChart(chartId)

    class SingleLineChartMetric internal constructor(
        chartId: String,
        val supplier: IntSupplier
    ) : MatrixMetricsChart(chartId)

    class AdvancedPieChart internal constructor(
        chartId: String,
        val supplier: Supplier<Map<String, Int>>
    ) : MatrixMetricsChart(chartId)
}

/**
 * Shared `bStats` registration facade used by Matrix plugins.
 *
 * MatrixLib owns the shaded `bStats` runtime so downstream plugins only need to
 * describe charts and plugin metadata.
 */
object MatrixBStats {

    /**
     * Register one or more charts for a plugin.
     */
    fun initialize(plugin: Plugin, pluginId: Int, vararg charts: MatrixMetricsChart) {
        return initialize(plugin, pluginId, charts.asList())
    }

    /**
     * Register one or more charts for a plugin.
     */
    fun initialize(plugin: Plugin, pluginId: Int, charts: Iterable<MatrixMetricsChart>) {
        Metrics(plugin, pluginId).apply {
            charts.forEach { chart ->
                when (chart) {
                    is MatrixMetricsChart.SimplePieChart -> {
                        addCustomChart(SimplePie(chart.chartId) {
                            runCatching { chart.supplier.get() }
                                .getOrNull()
                                ?.trim()
                                ?.takeIf(String::isNotBlank)
                                ?: "unknown"
                        })
                    }

                    is MatrixMetricsChart.SingleLineChartMetric -> {
                        addCustomChart(SingleLineChart(chart.chartId) {
                            runCatching { chart.supplier.asInt }
                                .getOrDefault(0)
                                .coerceAtLeast(0)
                        })
                    }

                    is MatrixMetricsChart.AdvancedPieChart -> {
                        addCustomChart(AdvancedPie(chart.chartId) {
                            runCatching { chart.supplier.get() }
                                .getOrDefault(emptyMap())
                                .mapNotNull { (key, value) ->
                                    key.trim()
                                        .takeIf(String::isNotBlank)
                                        ?.let { sanitized ->
                                            sanitized to value.coerceAtLeast(0)
                                        }
                                }
                                .filter { (_, value) -> value > 0 }
                                .toMap()
                        })
                    }
                }
            }
        }
    }

    /**
     * Create a `SimplePie` chart descriptor.
     */
    fun simplePie(chartId: String, supplier: Supplier<String>): MatrixMetricsChart {
        return MatrixMetricsChart.SimplePieChart(chartId, supplier)
    }

    /**
     * Create a `SingleLineChart` descriptor.
     */
    fun singleLine(chartId: String, supplier: IntSupplier): MatrixMetricsChart {
        return MatrixMetricsChart.SingleLineChartMetric(chartId, supplier)
    }

    /**
     * Create an `AdvancedPie` chart descriptor.
     */
    fun advancedPie(chartId: String, supplier: Supplier<Map<String, Int>>): MatrixMetricsChart {
        return MatrixMetricsChart.AdvancedPieChart(chartId, supplier)
    }
}
