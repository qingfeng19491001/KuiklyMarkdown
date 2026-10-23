package com.tencent.kuiklybase.elements

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.PlaceholderSpan
import com.tencent.kuikly.core.views.RichTextView
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.math.MATH_SELECTION_FILL
import com.tencent.kuiklybase.math.MATH_SELECTION_MS
import com.tencent.kuiklybase.math.MarkdownMath
import com.tencent.kuiklybase.math.estimateMathViewSize
import com.tencent.kuiklybase.math.extractMathLatex
import com.tencent.kuiklybase.math.resolveMathMeasuredSize
import org.intellij.markdown.ast.ASTNode

private val MathSelectionFill = Color(MATH_SELECTION_FILL)

private const val INLINE_MATH_HORIZONTAL_MARGIN = 2f

/**
 * 行内公式在 RichText 中的占位：尺寸驱动 PlaceholderSpan 参与文本测量，
 * spanFrame 由文本排版回传，驱动叠加在其上的 Native Math View 定位。
 */
internal class InlineMathSlot(
    override val pagerId: String,
    val latex: String,
    estimatedWidth: Float,
    estimatedHeight: Float,
) : PagerScope {
    var width by observable(estimatedWidth)
    var height by observable(estimatedHeight)
    var frame by observable(Frame.zero)
}

internal fun ViewContainer<*, *>.inlineMathSlot(latex: String, style: TextStyleConfig): InlineMathSlot {
    val (estW, estH) = estimateMathViewSize(latex, style.fontSize, false)
    return InlineMathSlot(pagerId, latex, estW, estH)
}

internal fun RichTextView.InlineMathPlaceholder(slot: InlineMathSlot) {
    PlaceholderSpan {
        placeholderSize(slot.width + INLINE_MATH_HORIZONTAL_MARGIN * 2, slot.height)
        spanFrameDidChanged { frame -> slot.frame = frame }
    }
}

fun ViewContainer<*, *>.markdownBlockMathFromNode(
    content: String,
    node: ASTNode,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    markdownBlockMath(
        latex = extractMathLatex(content, node),
        style = style,
        config = config,
    )
}

fun ViewContainer<*, *>.markdownBlockMath(
    latex: String,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    View {
        attr {
            flexDirectionRow()
            justifyContentCenter()
            alignItemsCenter()
            marginTop(config.padding.block)
            marginBottom(config.padding.block)
        }
        mathCopyHost(latex, style, config, display = true)
    }
}

/**
 * 叠加在 [slot] 占位上的行内公式，需与承载占位的 RichText 处于同一坐标系（同一父容器的原点）。
 */
internal fun ViewContainer<*, *>.markdownInlineMathOverlay(
    slot: InlineMathSlot,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    mathCopyHost(slot.latex, style, config, display = false, slot = slot)
}

private fun ViewContainer<*, *>.mathCopyHost(
    latex: String,
    style: TextStyleConfig,
    config: MarkdownConfig,
    display: Boolean,
    slot: InlineMathSlot? = null,
) {
    View {
        val host = this
        val (estW, estH) = slot?.let { it.width to it.height }
            ?: estimateMathViewSize(latex, style.fontSize, display)
        attr {
            flexDirectionRow()
            alignItemsCenter()
            if (slot != null) {
                val frame = slot.frame
                absolutePosition(top = frame.y, left = frame.x + INLINE_MATH_HORIZONTAL_MARGIN)
                size(slot.width, slot.height)
                visibility(frame.width > 0f && frame.height > 0f)
            } else {
                alignSelfCenter()
                size(estW, estH)
            }
            borderRadius(2f)
            backgroundColor(Color.TRANSPARENT)
        }
        var selectionGen = 0
        var measuredWidth = estW
        var measuredHeight = estH
        val applySize: (Float, Float) -> Unit = applySize@{ width, height ->
            val accepted = resolveMathMeasuredSize(measuredWidth, measuredHeight, width, height)
                ?: return@applySize
            measuredWidth = accepted.first
            measuredHeight = accepted.second
            if (slot != null) {
                slot.width = accepted.first
                slot.height = accepted.second
            } else {
                host.getViewAttr().size(accepted.first, accepted.second)
            }
        }
        val clearSelection: () -> Unit = {
            host.getViewAttr().backgroundColor(Color.TRANSPARENT)
        }
        val showThenClear: () -> Unit = {
            val gen = ++selectionGen
            host.getViewAttr().backgroundColor(MathSelectionFill)
            setTimeout(MATH_SELECTION_MS.toInt()) {
                if (gen == selectionGen) {
                    clearSelection()
                }
            }
        }
        config.onMathCopy?.let { onCopy ->
            event {
                click {
                    showThenClear()
                    onCopy(latex)
                }
                longPress { params ->
                    if (params.isStart) {
                        showThenClear()
                        onCopy(latex)
                    }
                }
            }
        }
        MarkdownMath {
            val mathView = this
            attr {
                size(estW, estH)
                latex(latex)
                display(display)
                fontSize(style.fontSize)
                color(style.color ?: config.colors.text)
            }
            event {
                onMeasured { width, height ->
                    if (width > 0f && height > 0f) {
                        mathView.getViewAttr().size(width, height)
                        applySize(width, height)
                    }
                }
            }
        }
    }
}
