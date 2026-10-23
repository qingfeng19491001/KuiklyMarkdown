package com.tencent.kuiklybase.math

import com.tencent.kuiklybase.config.TextStyleConfig
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * 从 GFM `INLINE_MATH` / `BLOCK_MATH` 节点抽取 LaTeX 源。
 * 对齐 HTML MathGeneratingProvider：去掉首尾 `$` token，再去掉成对包裹的 backtick。
 *
 * 不抛异常：节点残缺时回退为去掉 `$` 的原文。
 */
fun extractMathLatex(content: String, node: ASTNode): String {
    val raw = if (node.children.size >= 2) {
        node.children
            .subList(1, node.children.size - 1)
            .joinToString(separator = "") { it.getTextInNode(content).toString() }
    } else {
        node.getTextInNode(content).toString().trim('$')
    }
    return raw.trimSurroundingBackticksAndWhitespaces()
}

private fun String.trimSurroundingBackticksAndWhitespaces(): String {
    var i = 0
    while (length - i > 2 && this[i] == '`' && this[length - 1 - i] == '`') {
        i++
    }
    return substring(i, length - i).trim()
}

enum class MathAccent {
    Overline, Underline, Hat, Bar, Vec, Tilde, Dot, Ddot,
}

sealed class MathNode {
    data class Text(val value: String) : MathNode()
    data class Row(val children: List<MathNode>) : MathNode()
    data class Frac(
        val numerator: MathNode,
        val denominator: MathNode,
        val bar: Boolean = true,
    ) : MathNode()
    data class Sqrt(val index: MathNode?, val body: MathNode) : MathNode()
    data class Matrix(val environment: String, val rows: List<List<MathNode>>) : MathNode()
    data class Scripted(
        val base: MathNode,
        val sub: MathNode? = null,
        val sup: MathNode? = null,
    ) : MathNode()
    data class Operator(
        val symbol: String,
        val under: MathNode? = null,
        val over: MathNode? = null,
    ) : MathNode()
    data class Delimited(
        val left: String,
        val body: MathNode,
        val right: String,
    ) : MathNode()
    data class Accent(val kind: MathAccent, val body: MathNode) : MathNode()
    data class BigOp(val symbol: String, val limits: Boolean) : MathNode()
}

fun MathNode.flatten(): String = when (this) {
    is MathNode.Text -> value
    is MathNode.Row -> children.joinToString("") { it.flatten() }
    is MathNode.Frac -> {
        val inner = "${numerator.flatten()}/${denominator.flatten()}"
        if (bar) "($inner)" else inner
    }
    is MathNode.Sqrt -> {
        val inner = body.flatten()
        val idx = index?.flatten()?.takeIf { it.isNotBlank() }?.let { "[$it]" } ?: ""
        val wrapped = if (inner.length <= 1) inner else "($inner)"
        "√$idx$wrapped"
    }
    is MathNode.Matrix -> flattenMatrix(environment, rows)
    is MathNode.Scripted -> {
        val prefix = base.flatten()
        val subText = sub?.flatten()?.let { flattenScript(it, superscript = false) } ?: ""
        val supText = sup?.flatten()?.let { flattenScript(it, superscript = true) } ?: ""
        "$prefix$subText$supText"
    }
    is MathNode.Operator -> {
        val underText = under?.flatten()?.let { flattenScript(it, superscript = false) } ?: ""
        val overText = over?.flatten()?.let { flattenScript(it, superscript = true) } ?: ""
        "$symbol$underText$overText"
    }
    is MathNode.Delimited -> "$left${body.flatten()}$right"
    is MathNode.BigOp -> symbol
    is MathNode.Accent -> {
        val base = body.flatten()
        when (kind) {
            MathAccent.Overline -> buildString(base.length * 2) {
                for (ch in base) {
                    append(ch)
                    append('\u0305')
                }
            }
            MathAccent.Underline -> buildString(base.length * 2) {
                for (ch in base) {
                    append(ch)
                    append('\u0332')
                }
            }
            MathAccent.Hat -> base + "\u0302"
            MathAccent.Bar -> base + "\u0304"
            MathAccent.Vec -> base + "\u20D7"
            MathAccent.Tilde -> base + "\u0303"
            MathAccent.Dot -> base + "\u0307"
            MathAccent.Ddot -> base + "\u0308"
        }
    }
}

internal fun flattenScript(raw: String, superscript: Boolean): String {
    val mapped = raw.map { ch ->
        if (superscript) ch.toMathSuperscript() else ch.toMathSubscript()
    }
    return if (null !in mapped) {
        mapped.joinToString("")
    } else {
        if (superscript) "^{$raw}" else "_{$raw}"
    }
}

fun parseMathLayout(latex: String, display: Boolean = false): MathNode {
    if (latex.isBlank()) return MathNode.Text("")
    return try {
        LatexLayoutParser(latex, display).parseLayout()
    } catch (_: Exception) {
        MathNode.Text(latex)
    }
}

fun visibleMathText(latex: String, display: Boolean = false): String {
    if (latex.isBlank()) return ""
    return parseMathLayout(latex, display).flatten().ifBlank { latex }
}

/**
 * TeX 文件类宏（如 `\includegraphics`）在部分引擎会读本地路径。
 * Native 渲染前可据此拒绝，避免不可信 Markdown 触达文件系统。
 */
fun containsUnsafeLatexFileCommand(latex: String): Boolean =
    UNSAFE_LATEX_FILE_COMMAND.containsMatchIn(latex)

private val UNSAFE_LATEX_FILE_COMMAND =
    Regex("""\\(?:includegraphics|input|include|write|openout|read|immediate)\b""", RegexOption.IGNORE_CASE)

/** 点击/长按复制公式时的短暂选中高亮（ARGB）。 */
const val MATH_SELECTION_FILL = 0x332196F3L

/** 公式选中高亮持续毫秒数。 */
const val MATH_SELECTION_MS = 1000L

/**
 * Native 公式 View 在测量回调回来前的占位。
 * 宽按可见文本大致给空间，避免格子只有 1em 把引擎裁掉；最终宽高以各端 `onMeasured` 为准。
 */
fun estimateMathViewSize(latex: String, fontSize: Float, display: Boolean): Pair<Float, Float> {
    val size = fontSize.coerceAtLeast(1f)
    val visible = visibleMathText(latex, display)
    val rows = visible.count { it == '\n' } + 1
    val width = size * visible.replace("\n", "").length.coerceIn(1, 48) * 0.5f
    val height = size * when {
        rows > 1 -> (if (display) 1.35f else 1.2f) * rows
        display -> 1.8f
        else -> 1.25f
    }
    return width.coerceAtLeast(size) to height
}

/**
 * 行内公式上下补白，使公式基线落在正文基线上。
 *
 * 三端 RichText 都把 PlaceholderSpan 以正文行中心竖直居中（Android `spanRect` 取行框中线，
 * iOS 附件以 capHeight/2 居中，鸿蒙 `ALIGNMENT_CENTER_OF_ROW_BOX`），因此补白后的盒子
 * 中心要位于基线上方 [lineCenter] 处。返回 (top, bottom)，单位与入参一致。
 */
fun inlineMathBaselinePadding(ascent: Float, descent: Float, lineCenter: Float): Pair<Float, Float> {
    val half = maxOf(ascent - lineCenter, descent + lineCenter, 0f)
    val top = (half + lineCenter - ascent).coerceAtLeast(0f)
    val bottom = (half - lineCenter - descent).coerceAtLeast(0f)
    return top to bottom
}

/** 含行内公式的段落不能用固定行高（会裁掉高公式），改用等效的额外行距。 */
fun inlineMathLineSpacing(lineHeight: Float, fontSize: Float): Float =
    (lineHeight - fontSize * NATURAL_LINE_HEIGHT_RATIO).coerceAtLeast(0f)

/** 段落样式下行内公式段落实际使用的额外行距，未设置行高时为 0。 */
fun inlineMathLineSpacing(style: TextStyleConfig): Float =
    style.lineHeight?.let { inlineMathLineSpacing(it, style.fontSize) } ?: 0f

private const val NATURAL_LINE_HEIGHT_RATIO = 1.2f

fun latexMatrixDelimiters(environment: String): Pair<String, String> =
    when (environment) {
        "pmatrix" -> "(" to ")"
        "bmatrix" -> "[" to "]"
        "vmatrix" -> "|" to "|"
        "Vmatrix" -> "∥" to "∥"
        "Bmatrix" -> "{" to "}"
        "cases" -> "{" to ""
        else -> "" to ""
    }

private fun flattenMatrix(environment: String, rows: List<List<MathNode>>): String {
    val (left, right) = latexMatrixDelimiters(environment)
    val cells = rows.map { row -> row.map { it.flatten() } }
    if (cells.isEmpty()) return "$left$right"
    val colCount = cells.maxOf { it.size }
    val widths = (0 until colCount).map { col ->
        cells.maxOf { row -> row.getOrNull(col)?.length ?: 0 }
    }
    val lines = cells.map { row ->
        (0 until colCount).joinToString("  ") { col ->
            (row.getOrNull(col) ?: "").padEnd(widths[col])
        }
    }
    if (lines.size == 1) {
        return "$left${lines[0].trimEnd()}$right"
    }
    return if (environment == "cases") {
        lines.mapIndexed { index, line ->
            val body = line.trimEnd()
            if (index == 0) "$left $body" else "  $body"
        }.joinToString("\n")
    } else {
        lines.joinToString("\n") { line -> "$left${line.trimEnd()}$right" }
    }
}

/**
 * 把成对的 `\[...\]` / `\(...\)` 归一成 `$$` / `$`，代码块内不改。
 * 未闭合的 `\[` / `\(` 保持原样，避免流式半截或笔误把后面正文吞进块级公式。
 * 未配对的行内 backtick 不当作代码，避免后续公式分隔符整段失效。
 */
fun normalizeMathDelimiters(content: String): String {
    if (content.isEmpty()) return content
    // Fast path: most docs never use \[ / \(
    if (!content.contains("\\[") && !content.contains("\\(")) return content
    val rewrite = BooleanArray(content.length)
    var scan = 0
    var inFence = false
    while (scan < content.length) {
        if (!inFence && content.startsWith("```", scan)) {
            inFence = true
            scan += 3
            continue
        }
        if (inFence && content.startsWith("```", scan)) {
            inFence = false
            scan += 3
            continue
        }
        if (inFence) {
            scan++
            continue
        }
        if (content[scan] == '`') {
            val ticks = countBackticks(content, scan)
            val closeAt = indexOfMatchingBacktickClose(content, scan + ticks, ticks)
            scan = if (closeAt >= 0) closeAt + ticks else scan + ticks
            continue
        }
        if (scan + 1 < content.length && content[scan] == '\\') {
            val closeChar = when (content[scan + 1]) {
                '[' -> ']'
                '(' -> ')'
                else -> null
            }
            if (closeChar != null) {
                val closeAt = indexOfMatchingMathClose(content, scan + 2, closeChar)
                if (closeAt >= 0) {
                    rewrite[scan] = true
                    rewrite[closeAt] = true
                    scan = closeAt + 2
                    continue
                }
            }
        }
        scan++
    }
    val out = StringBuilder(content.length)
    var i = 0
    while (i < content.length) {
        if (rewrite[i] && i + 1 < content.length && content[i] == '\\') {
            when (content[i + 1]) {
                '[', ']' -> {
                    out.append("$$")
                    i += 2
                    continue
                }
                '(', ')' -> {
                    out.append('$')
                    i += 2
                    continue
                }
            }
        }
        out.append(content[i++])
    }
    return out.toString()
}

private fun countBackticks(content: String, from: Int): Int {
    var n = 0
    while (from + n < content.length && content[from + n] == '`') n++
    return n
}

private fun indexOfMatchingBacktickClose(content: String, from: Int, tickCount: Int): Int {
    var i = from
    while (i < content.length) {
        if (content[i] == '`') {
            val n = countBackticks(content, i)
            if (n == tickCount) return i
            i += n
        } else {
            i++
        }
    }
    return -1
}

private fun indexOfMatchingMathClose(content: String, from: Int, closeChar: Char): Int {
    var i = from
    var inFence = false
    while (i < content.length) {
        if (!inFence && content.startsWith("```", i)) {
            inFence = true
            i += 3
            continue
        }
        if (inFence && content.startsWith("```", i)) {
            inFence = false
            i += 3
            continue
        }
        if (inFence) {
            i++
            continue
        }
        if (content[i] == '`') {
            val ticks = countBackticks(content, i)
            val closeAt = indexOfMatchingBacktickClose(content, i + ticks, ticks)
            i = if (closeAt >= 0) closeAt + ticks else i + ticks
            continue
        }
        if (i + 1 < content.length && content[i] == '\\' && content[i + 1] == closeChar) {
            return i
        }
        i++
    }
    return -1
}

internal fun Char.toMathSuperscript(): Char? = when (this) {
    '0' -> '⁰'
    '1' -> '¹'
    '2' -> '²'
    '3' -> '³'
    '4' -> '⁴'
    '5' -> '⁵'
    '6' -> '⁶'
    '7' -> '⁷'
    '8' -> '⁸'
    '9' -> '⁹'
    '+' -> '⁺'
    '-' -> '⁻'
    '=' -> '⁼'
    '(' -> '⁽'
    ')' -> '⁾'
    'n' -> 'ⁿ'
    'i' -> 'ⁱ'
    else -> null
}

internal fun Char.toMathSubscript(): Char? = when (this) {
    '0' -> '₀'
    '1' -> '₁'
    '2' -> '₂'
    '3' -> '₃'
    '4' -> '₄'
    '5' -> '₅'
    '6' -> '₆'
    '7' -> '₇'
    '8' -> '₈'
    '9' -> '₉'
    '+' -> '₊'
    '-' -> '₋'
    '=' -> '₌'
    '(' -> '₍'
    ')' -> '₎'
    'a' -> 'ₐ'
    'e' -> 'ₑ'
    'h' -> 'ₕ'
    'i' -> 'ᵢ'
    'j' -> 'ⱼ'
    'k' -> 'ₖ'
    'l' -> 'ₗ'
    'm' -> 'ₘ'
    'n' -> 'ₙ'
    'o' -> 'ₒ'
    'p' -> 'ₚ'
    'r' -> 'ᵣ'
    's' -> 'ₛ'
    't' -> 'ₜ'
    'u' -> 'ᵤ'
    'v' -> 'ᵥ'
    'x' -> 'ₓ'
    else -> null
}
