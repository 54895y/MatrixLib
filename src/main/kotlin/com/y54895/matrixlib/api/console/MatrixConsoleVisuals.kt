package com.y54895.matrixlib.api.console

import com.y54895.matrixlib.api.brand.MatrixBranding
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

data class MatrixConsoleFact(
    val label: String,
    val value: String
)

object MatrixConsoleVisuals {

    private const val MIN_BORDER_WIDTH = 66
    private val colorCodePattern = Regex("&[0-9a-fk-orA-FK-OR]")

    fun renderBoot(
        branding: MatrixBranding,
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "LOAD",
            headline = headline,
            details = defaultDetails(branding) + details,
            includeLogo = true,
            leadingBlank = true
        )
    }

    fun renderStage(
        branding: MatrixBranding,
        stage: String,
        headline: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = stage,
            headline = headline,
            details = details
        )
    }

    fun renderReady(
        branding: MatrixBranding,
        version: String,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "READY",
            headline = "${branding.runtimeName} 已就绪",
            details = listOf(MatrixConsoleFact("版本", version)) + defaultDetails(branding) + details,
            trailingBlank = true
        )
    }

    fun renderFailure(
        branding: MatrixBranding,
        reason: String
    ) {
        renderBlock(
            branding = branding,
            stage = "FAIL",
            headline = "${branding.runtimeName} 启动失败",
            details = listOf(MatrixConsoleFact("原因", reason))
        )
    }

    fun renderShutdown(
        branding: MatrixBranding,
        details: List<MatrixConsoleFact> = emptyList()
    ) {
        renderBlock(
            branding = branding,
            stage = "STOP",
            headline = "${branding.runtimeName} 正在卸载",
            details = details,
            leadingBlank = true
        )
    }

    private fun renderBlock(
        branding: MatrixBranding,
        stage: String,
        headline: String,
        details: List<MatrixConsoleFact>,
        includeLogo: Boolean = false,
        leadingBlank: Boolean = false,
        trailingBlank: Boolean = false
    ) {
        val contentLines = mutableListOf<String>()
        if (includeLogo) {
            contentLines += MatrixAsciiBanner.render(branding.bannerTitle)
            contentLines += stageLine(branding, "INFO", branding.runtimeName)
        }
        contentLines += stageLine(branding, stage, headline)
        details.forEach { contentLines += detailLine(branding, it) }

        val border = border(contentLines)
        val lines = mutableListOf<String>()
        if (leadingBlank) {
            lines += ""
        }
        lines += border
        contentLines.forEach { lines += it }
        lines += border
        if (trailingBlank) {
            lines += ""
        }
        send(lines)
    }

    private fun defaultDetails(branding: MatrixBranding): List<MatrixConsoleFact> {
        val details = mutableListOf(MatrixConsoleFact("主命令", branding.rootCommand))
        branding.adminCommand?.let { details += MatrixConsoleFact("管理命令", it) }
        return details
    }

    private fun stageLine(branding: MatrixBranding, stage: String, headline: String): String {
        val color = when (stage.uppercase()) {
            "LOAD", "INIT" -> "&e"
            "READY" -> "&a"
            "FAIL", "STOP" -> "&c"
            else -> branding.accentColor
        }
        return "${prefix(branding)}$color[${stageLabel(stage)}] &f$headline"
    }

    private fun detailLine(branding: MatrixBranding, fact: MatrixConsoleFact): String {
        return "${prefix(branding)}&f${fact.label} &8>> ${branding.accentColor}${fact.value}"
    }

    private fun prefix(branding: MatrixBranding): String {
        return "&8[${branding.accentColor}${branding.displayName}&8] "
    }

    private fun stageLabel(stage: String): String {
        return when (stage.uppercase()) {
            "INFO" -> "信息"
            "LOAD" -> "加载"
            "INIT" -> "初始化"
            "READY" -> "就绪"
            "FAIL" -> "失败"
            "STOP" -> "卸载"
            else -> stage
        }
    }

    private fun border(lines: List<String>): String {
        val width = maxOf(MIN_BORDER_WIDTH, lines.maxOfOrNull(::visibleLength) ?: MIN_BORDER_WIDTH)
        return "&8&m+${"-".repeat(width)}+"
    }

    private fun visibleLength(line: String): Int {
        return colorCodePattern.replace(line, "").length
    }

    private fun send(lines: List<String>) {
        lines.forEach { line ->
            val rendered = MatrixText.color(line)
            runCatching {
                Bukkit.getConsoleSender().sendMessage(rendered)
            }.getOrElse {
                info(rendered)
            }
        }
    }
}

private object MatrixAsciiBanner {

    private val rowColors = listOf("&3", "&b", "&b", "&9", "&9")

    private val glyphs = mapOf(
        ' ' to listOf("     ", "     ", "     ", "     ", "     "),
        '?' to listOf("?????", "   ? ", "  ?  ", "     ", "  ?  "),
        'A' to listOf("  A  ", " A A ", "AAAAA", "A   A", "A   A"),
        'B' to listOf("BBBB ", "B   B", "BBBB ", "B   B", "BBBB "),
        'C' to listOf(" CCC ", "C   C", "C    ", "C   C", " CCC "),
        'H' to listOf("H   H", "H   H", "HHHHH", "H   H", "H   H"),
        'I' to listOf("IIIII", "  I  ", "  I  ", "  I  ", "IIIII"),
        'K' to listOf("K   K", "K  K ", "KKK  ", "K  K ", "K   K"),
        'L' to listOf("L    ", "L    ", "L    ", "L    ", "LLLLL"),
        'M' to listOf("M   M", "MM MM", "M M M", "M   M", "M   M"),
        'N' to listOf("N   N", "NN  N", "N N N", "N  NN", "N   N"),
        'O' to listOf(" OOO ", "O   O", "O   O", "O   O", " OOO "),
        'P' to listOf("PPPP ", "P   P", "PPPP ", "P    ", "P    "),
        'R' to listOf("RRRR ", "R   R", "RRRR ", "R  R ", "R   R"),
        'S' to listOf(" SSS ", "S    ", " SSS ", "    S", " SSS "),
        'T' to listOf("TTTTT", "  T  ", "  T  ", "  T  ", "  T  "),
        'U' to listOf("U   U", "U   U", "U   U", "U   U", " UUU "),
        'X' to listOf("X   X", " X X ", "  X  ", " X X ", "X   X")
    )

    fun render(text: String): List<String> {
        val normalized = text.uppercase().ifBlank { "MATRIX" }
        val rows = List(rowColors.size) { StringBuilder() }
        normalized.forEachIndexed { index, char ->
            val glyph = glyphs[char] ?: glyphs.getValue('?')
            glyph.forEachIndexed { rowIndex, row ->
                rows[rowIndex].append(row)
                if (index < normalized.lastIndex) {
                    rows[rowIndex].append(' ')
                }
            }
        }
        return rows.mapIndexed { index, builder ->
            rowColors[index] + builder.toString().trimEnd()
        }
    }
}
