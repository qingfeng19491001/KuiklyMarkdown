package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.math.MarkdownMathAttr
import com.tencent.kuiklybase.math.MarkdownMathView
import com.tencent.kuiklybase.math.estimateMathViewSize
import com.tencent.kuiklybase.math.resolveMathMeasuredSize

@Stable
internal class MathSizeState(estimatedWidth: Float, estimatedHeight: Float) {
    var width by mutableStateOf(estimatedWidth)
        private set
    var height by mutableStateOf(estimatedHeight)
        private set

    fun accept(measuredWidth: Float, measuredHeight: Float) {
        val accepted = resolveMathMeasuredSize(width, height, measuredWidth, measuredHeight) ?: return
        width = accepted.first
        height = accepted.second
    }
}

internal fun mathSizeStateOf(latex: String, fontSize: Float, display: Boolean): MathSizeState {
    val (width, height) = estimateMathViewSize(latex, fontSize, display)
    return MathSizeState(width, height)
}

@Composable
internal fun MarkdownNativeMath(
    latex: String,
    display: Boolean,
    style: TextStyleConfig,
    config: MarkdownConfig,
    modifier: Modifier = Modifier,
    sizeState: MathSizeState = remember(latex, style.fontSize, display) {
        mathSizeStateOf(latex, style.fontSize, display)
    },
) {
    val width = sizeState.width
    val height = sizeState.height
    val color = style.color ?: config.colors.text
    val applyMathAttrs: MarkdownMathAttr.() -> Unit = {
        size(width, height)
        latex(latex)
        display(display)
        fontSize(style.fontSize)
        color(color)
    }
    MakeKuiklyComposeNode<MarkdownMathView>(
        factory = { MarkdownMathView() },
        modifier = modifier.size(width.dp, height.dp),
        viewInit = {
            getViewAttr().applyMathAttrs()
            getViewEvent().onMeasured { measuredWidth, measuredHeight ->
                sizeState.accept(measuredWidth, measuredHeight)
            }
        },
        viewUpdate = {
            it.getViewAttr().applyMathAttrs()
        },
    )
}
