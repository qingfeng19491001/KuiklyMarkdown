package com.tencent.kuiklybase.math

import com.tencent.kuiklybase.annotator.buildStyledTextSegments
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.parser.parseMarkdown
import com.tencent.kuiklybase.streaming.MarkdownStreamingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownMathTest {

    @Test
    fun detectsUnsafeLatexFileCommands() {
        assertTrue(containsUnsafeLatexFileCommand("\\includegraphics{/sdcard/secret.png}"))
        assertTrue(containsUnsafeLatexFileCommand("x=\\INPUT{a.tex}"))
        assertFalse(containsUnsafeLatexFileCommand("E=mc^2"))
        assertFalse(containsUnsafeLatexFileCommand("\\frac{a}{b}"))
    }

    @Test
    fun layoutsAcceptanceSamples() {
        assertEquals("E=mc²", SimpleMathRenderer.tryLayout("E=mc^2"))
        assertEquals("x²+y²=z²", SimpleMathRenderer.tryLayout("x^2+y^2=z^2"))
        assertEquals("E=mc²", SimpleMathRenderer.tryLayout("E = mc^2"))
        assertEquals("x²", SimpleMathRenderer.tryLayout("x^{2}"))
    }

    @Test
    fun layoutsGreekFractionsAndMatrices() {
        assertEquals("α+1", SimpleMathRenderer.tryLayout("\\alpha+1"))
        assertEquals("(a/b)", SimpleMathRenderer.tryLayout("\\frac{a}{b}"))
        assertEquals("√x", SimpleMathRenderer.tryLayout("\\sqrt{x}"))
        val matrix = parseMathLayout("\\begin{pmatrix}a & b \\\\ c & d\\end{pmatrix}")
        assertTrue(matrix is MathNode.Matrix)
        val matrixNode = matrix as MathNode.Matrix
        assertEquals("pmatrix", matrixNode.environment)
        assertEquals(listOf("a", "b"), matrixNode.rows[0].map { it.flatten() })
        assertEquals(listOf("c", "d"), matrixNode.rows[1].map { it.flatten() })
        assertEquals("α+(1/2)", SimpleMathRenderer.tryLayout("\\alpha + \\frac{1}{2}"))
        assertEquals("√(xᵢ²)", SimpleMathRenderer.tryLayout("\\sqrt{x_i^2}"))
        val layout = parseMathLayout("\\alpha + \\frac{1}{2} = \\sqrt{x_i^2}")
        val children = (layout as MathNode.Row).children
        assertTrue(children.any { it is MathNode.Frac })
        assertTrue(children.any { it is MathNode.Sqrt })
        val sum = parseMathLayout("\\sum_{i=1}^{n} x_i", display = true)
        assertTrue(sum is MathNode.Operator || sum is MathNode.Row || sum is MathNode.Scripted)
        assertTrue(SimpleMathRenderer.tryLayout("\\sum_{i=1}^{n} x_i", display = true)!!.contains("∑"))
        assertTrue(SimpleMathRenderer.tryLayout("\\left(\\frac{a}{b}\\right)")!!.contains("a/b"))
        assertEquals("ℝ", SimpleMathRenderer.tryLayout("\\mathbb{R}"))
        assertEquals("\$\$x^2\$\$", normalizeMathDelimiters("\\[x^2\\]"))
        assertEquals("\$x^2\$", normalizeMathDelimiters("\\(x^2\\)"))
    }

    @Test
    fun displayMatrixFromMarkdownSource() {
        val segments = segmentsOf("\$\$\\begin{bmatrix}1 & 2 & 3 \\\\ 4 & 5 & 6\\end{bmatrix}\$\$")
        val math = segments.first { it.mathDisplay }
        val parsed = parseMathLayout(math.mathLatex!!, display = true)
        assertTrue(parsed is MathNode.Matrix)
        val matrix = parsed as MathNode.Matrix
        assertEquals("bmatrix", matrix.environment)
        assertEquals(listOf("1", "2", "3"), matrix.rows[0].map { it.flatten() })
        assertEquals(listOf("4", "5", "6"), matrix.rows[1].map { it.flatten() })
        val visible = visibleMathText(math.mathLatex!!, display = true)
        assertTrue(visible.contains("\n"))
        assertTrue(visible.contains("1"))
        assertTrue(visible.contains("4"))
    }

    @Test
    fun unknownCommandStaysReadable() {
        assertEquals("\\notacommand", visibleMathText("\\notacommand"))
        assertEquals("a*b", SimpleMathRenderer.tryLayout("a*b"))
    }

    @Test
    fun inlineMathIsNotDropped() {
        val segments = segmentsOf("The formula is \$E=mc^2\$.")
        val math = segments.first { it.mathLatex != null }
        assertEquals("E=mc^2", math.mathLatex)
        assertFalse(math.mathDisplay)
        assertEquals("E=mc²", math.text)
        assertTrue(segments.joinToString("") { it.text }.contains("E=mc²"))
    }

    @Test
    fun displayMathIsIndependentSegment() {
        val segments = segmentsOf("\$\$x^2+y^2=z^2\$\$")
        val math = segments.single { it.mathLatex != null }
        assertEquals("x^2+y^2=z^2", math.mathLatex)
        assertTrue(math.mathDisplay)
        assertEquals("x²+y²=z²", math.text)
    }

    @Test
    fun unclosedDisplayDollarKeepsBothDollars() {
        val text = segmentsOf("\$\$x^2").joinToString("") { it.text }
        assertTrue(text.contains("\$\$x^2"), "实际输出: $text")
        assertTrue(segmentsOf("\$\$x^2").none { it.mathLatex != null })
    }

    @Test
    fun currencyDollarIsNotMath() {
        val segments = segmentsOf("价格是 \$5 美元")
        assertTrue(segments.none { it.mathLatex != null })
        assertTrue(segments.joinToString("") { it.text }.contains("\$5 美元"))
    }

    @Test
    fun currencyPairsDoNotSwallowText() {
        val segments = segmentsOf("成本 \$100 + \$200 共 \$300 元")
        assertTrue(segments.none { it.mathLatex != null })
        val text = segments.joinToString("") { it.text }
        assertTrue(text.contains("\$100"), "实际输出: $text")
        assertTrue(text.contains("\$300"), "实际输出: $text")
    }

    @Test
    fun currencyDollarDoesNotBreakFollowingMath() {
        val segments = segmentsOf("价格是 \$5 美元，变量 \$x\$ 是未知数")
        assertEquals("x", segments.first { it.mathLatex != null }.mathLatex)
        val text = segments.joinToString("") { it.text }
        assertTrue(text.contains("\$5 美元"), "实际输出: $text")
        assertTrue(text.contains("未知数"), "实际输出: $text")
    }

    @Test
    fun dollarInCodeSpanIsNotMath() {
        val segments = segmentsOf("`\$E=mc^2\$` 是代码")
        assertTrue(segments.none { it.mathLatex != null })
        assertTrue(segments.joinToString("") { it.text }.contains("\$E=mc^2\$"))
    }

    @Test
    fun unclosedDollarStaysPlainText() {
        val segments = segmentsOf("\$E=mc")
        assertTrue(segments.none { it.mathLatex != null })
        assertTrue(segments.joinToString("") { it.text }.contains("\$E=mc"))
    }

    @Test
    fun invalidClosedMathKeepsLatexSourceAndVisibleLayout() {
        val segments = segmentsOf("\$\\alpha+1\$")
        val math = segments.first { it.mathLatex != null }
        assertEquals("\\alpha+1", math.mathLatex)
        assertEquals("α+1", math.text)
    }

    @Test
    fun streamingChunksDoNotDropClosedMath() {
        val state = MarkdownStreamingState()
        val first = "The formula is \$E="
        val second = "The formula is \$E=mc^2\$"

        val firstBlocks = assertNotNull(state.update(first))
        assertEquals(first, state.parseResult?.content)
        assertTrue(firstBlocks.joinToString("") { it.blockContent }.contains("\$E="))
        val openSegments = segmentsOf(state.parseResult!!.content, state.parseResult!!.node)
        assertTrue(openSegments.none { it.mathLatex != null })
        assertTrue(openSegments.joinToString("") { it.text }.contains("\$E="))

        val secondBlocks = assertNotNull(state.update(second))
        assertEquals(second, state.parseResult?.content)
        assertTrue(secondBlocks.isNotEmpty())
        val closedSegments = segmentsOf(state.parseResult!!.content, state.parseResult!!.node)
        val math = closedSegments.first { it.mathLatex != null }
        assertEquals("E=mc^2", math.mathLatex)
        assertFalse(math.mathDisplay)
    }

    @Test
    fun streamingDisplayMathAcrossChunks() {
        val state = MarkdownStreamingState()
        val first = "\$\$x^2"
        val second = "\$\$x^2+y^2=z^2\$\$"

        assertNotNull(state.update(first))
        assertEquals(first, state.parseResult?.content)
        val openSegments = segmentsOf(state.parseResult!!.content, state.parseResult!!.node)
        assertTrue(openSegments.none { it.mathLatex != null && it.mathDisplay })
        assertTrue(openSegments.joinToString("") { it.text }.contains("x^2"))

        assertNotNull(state.update(second, force = true))
        val closed = segmentsOf(state.parseResult!!.content, state.parseResult!!.node)
        val math = closed.first { it.mathDisplay }
        assertEquals("x^2+y^2=z^2", math.mathLatex)
    }

    @Test
    fun streamingMixedWithCodeFenceKeepsBoth() {
        val state = MarkdownStreamingState()
        val markdown =
            """
            ```kotlin
            val answer = 42
            ```

            Energy is ${'$'}E=mc^2${'$'}
            """.trimIndent()

        val prefix = markdown.substring(0, markdown.indexOf("E=mc") + 2)
        assertNotNull(state.update(prefix))
        assertTrue(state.parseResult!!.content.contains("val answer = 42"))
        assertTrue(state.parseResult!!.content.contains("\$E="))

        assertNotNull(state.update(markdown, force = true))
        assertTrue(state.parseResult!!.content.contains("val answer = 42"))
        val math = segmentsOf(state.parseResult!!.content, state.parseResult!!.node)
            .first { it.mathLatex != null }
        assertEquals("E=mc^2", math.mathLatex)
    }

    @Test
    fun estimateMathViewSizeIsPositive() {
        val (w, h) = estimateMathViewSize("\\frac{a}{b}", 16f, display = true)
        assertTrue(w > 8f)
        assertTrue(h > 16f)
    }

    @Test
    fun estimateUsesVisibleTextNotRawLatexPadding() {
        val longCommand = estimateMathViewSize("\\operatorname{abcdefghijklmnopqrstuvwxyz}", 16f, display = false)
        val shortVisible = estimateMathViewSize("E=mc^2", 16f, display = false)
        assertTrue(shortVisible.first >= 16f)
        assertTrue(longCommand.first > shortVisible.first)
        val frac = estimateMathViewSize("\\frac{a}{b}", 16f, display = true)
        val inline = estimateMathViewSize("E=mc^2", 16f, display = false)
        assertTrue(frac.second > inline.second)
    }

    @Test
    fun estimateInlineFracIsNotFlattenStringWidth() {
        val latex = "\\alpha = \\frac{v}{c}"
        val estimated = estimateMathViewSize(latex, 16f, display = false)
        val flattenWidth = visibleMathText(latex, display = false).length * 16f * 0.72f
        assertTrue(estimated.first < flattenWidth)
        assertTrue(estimated.first > 16f)
    }

    @Test
    fun readMathMeasuredSizeAcceptsMap() {
        val fromMap = readMathMeasuredSize(mapOf("width" to 40, "height" to 18))
        assertEquals(40f, fromMap.first)
        assertEquals(18f, fromMap.second)
    }

    @Test
    fun repeatedOrInvalidNativeMeasurementDoesNotTriggerLayoutUpdate() {
        assertEquals(null, resolveMathMeasuredSize(40f, 18f, 40.2f, 18.1f))
        assertEquals(null, resolveMathMeasuredSize(40f, 18f, 0f, 18f))
        assertEquals(null, resolveMathMeasuredSize(40f, 18f, Float.NaN, 18f))
        assertEquals(52f to 24f, resolveMathMeasuredSize(40f, 18f, 52f, 24f))
    }

    @Test
    fun inlineMathPaddingPutsFormulaBaselineOnTextBaseline() {
        val lineCenter = 6f
        listOf(
            12f to 0f, // E=mc^2：上标抬高，几乎没有下深
            9f to 7f, // 分式：上下都伸出
            4f to 0f, // 单个小写字母
            20f to 18f, // 高矩阵
        ).forEach { (ascent, descent) ->
            val (top, bottom) = inlineMathBaselinePadding(ascent, descent, lineCenter)
            val height = top + ascent + descent + bottom
            assertTrue(top >= 0f && bottom >= 0f)
            assertEquals(height / 2f + lineCenter, top + ascent, 0.001f)
        }
    }

    @Test
    fun inlineMathLineSpacingReplacesFixedLineHeight() {
        assertEquals(8.8f, inlineMathLineSpacing(28f, 16f), 0.001f)
        assertEquals(0f, inlineMathLineSpacing(16f, 16f))
    }

    @Test
    fun garbageLatexDoesNotThrow() {
        assertEquals("", visibleMathText(""))
        val messy = visibleMathText("\\frac{a{b")
        assertTrue(messy.isNotEmpty())
    }

    @Test
    fun inlineLinkKeepsClickableDestination() {
        val segments = segmentsOf("见 [Kuikly](https://kuikly.tencent.com) 文档")
        val link = segments.first { it.linkUrl != null }
        assertEquals("https://kuikly.tencent.com", link.linkUrl)
        assertEquals("Kuikly", link.text)
    }

    @Test
    fun unsafeIntentLinkIsNotClickable() {
        val segments = segmentsOf("见 [pay](intent://scan/#Intent;end) 文档")
        val link = segments.first { it.text == "pay" }
        assertEquals(null, link.linkUrl)
    }

    @Test
    fun httpLinkStaysBrowsable() {
        assertTrue(org.intellij.markdown.html.isSafeBrowsableUrl("https://kuikly.tencent.com"))
        assertFalse(org.intellij.markdown.html.isSafeBrowsableUrl("intent://scan/#Intent;end"))
        assertFalse(org.intellij.markdown.html.isSafeBrowsableUrl("javascript:alert(1)"))
    }

    @Test
    fun parseNormalizesBracketMathDelimiters() {
        val result = parseMarkdown("\\[x^2\\]")
        assertEquals("\$\$x^2\$\$", result.content)
    }

    @Test
    fun unclosedBracketDelimiterStaysLiteral() {
        assertEquals("\\[x^2", normalizeMathDelimiters("\\[x^2"))
        assertEquals("range [0,1) and \\[ leftover", normalizeMathDelimiters("range [0,1) and \\[ leftover"))
        assertEquals("hello \\[ world\n\n\$\$x^2\$\$", normalizeMathDelimiters("hello \\[ world\n\n\$\$x^2\$\$"))
    }

    @Test
    fun unclosedParenDelimiterStaysLiteral() {
        assertEquals("\\(x^2", normalizeMathDelimiters("\\(x^2"))
        assertEquals("\$x^2\$ and \\(open", normalizeMathDelimiters("\\(x^2\\) and \\(open"))
    }

    @Test
    fun unmatchedCloseDelimiterStaysLiteral() {
        assertEquals("plain \\] text", normalizeMathDelimiters("plain \\] text"))
        assertEquals("plain \\) text", normalizeMathDelimiters("plain \\) text"))
    }

    @Test
    fun unmatchedInlineBacktickDoesNotBlockDelimiterRewrite() {
        assertEquals("`oops \$\$x^2\$\$", normalizeMathDelimiters("`oops \\[x^2\\]"))
        assertEquals("`\\[x^2\\]`", normalizeMathDelimiters("`\\[x^2\\]`"))
        assertEquals("``a \\[x\\] b`` \$\$y\$\$", normalizeMathDelimiters("``a \\[x\\] b`` \\[y\\]"))
    }

    @Test
    fun strayOpenBracketDoesNotSwallowFollowingDisplayMath() {
        val segments = segmentsOf("intro \\[ incomplete\n\n\$\$E=mc^2\$\$")
        assertTrue(segments.none { it.mathLatex == " incomplete" })
        val math = segments.first { it.mathDisplay }
        assertEquals("E=mc^2", math.mathLatex)
    }

    private fun segmentsOf(content: String) =
        segmentsOf(content, parseMarkdown(content).node)

    private fun segmentsOf(content: String, node: org.intellij.markdown.ast.ASTNode) =
        buildStyledTextSegments(
            content = content,
            node = node,
            config = MarkdownConfig.Default,
            referenceLinkHandler = null,
        )
}
