package com.tencent.kuiklybase.math

import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 公式绘制交给各端 Native 公式引擎。viewName 必须与宿主注册名一致。
 */
class MarkdownMathView : DeclarativeBaseView<MarkdownMathAttr, MarkdownMathEvent>() {

    override fun createAttr(): MarkdownMathAttr = MarkdownMathAttr()

    override fun createEvent(): MarkdownMathEvent = MarkdownMathEvent()

    override fun viewName(): String = VIEW_NAME

    companion object {
        const val VIEW_NAME = "KRMarkdownMathView"
    }
}

class MarkdownMathAttr : Attr() {
    fun latex(value: String): MarkdownMathAttr {
        "latex" with value
        return this
    }

    fun display(value: Boolean): MarkdownMathAttr {
        "display" with if (value) 1 else 0
        return this
    }

    fun fontSize(value: Float): MarkdownMathAttr {
        "fontSize" with value
        return this
    }

    fun color(value: Long): MarkdownMathAttr {
        "color" with value
        return this
    }
}

class MarkdownMathEvent : Event() {
    fun onMeasured(handler: (width: Float, height: Float) -> Unit) {
        register("onMeasured") { params ->
            val (width, height) = readMathMeasuredSize(params)
            handler(width, height)
        }
    }
}

/** Native 回传 `{width,height}`；宿主侧一般为 Map / JSONObject。 */
internal fun readMathMeasuredSize(params: Any?): Pair<Float, Float> = when (params) {
    is Map<*, *> -> readNumber(params["width"]) to readNumber(params["height"])
    is JSONObject -> params.optDouble("width").toFloat() to params.optDouble("height").toFloat()
    else -> 0f to 0f
}

private fun readNumber(value: Any?): Float = when (value) {
    is Number -> value.toFloat()
    is String -> value.toFloatOrNull() ?: 0f
    else -> 0f
}

/**
 * Accepts a native measurement only when it is valid and materially different.
 * Native formula engines can report the same fractional size more than once while laying out;
 * filtering those callbacks prevents a measure -> state update -> measure feedback loop.
 */
fun resolveMathMeasuredSize(
    currentWidth: Float,
    currentHeight: Float,
    measuredWidth: Float,
    measuredHeight: Float,
    tolerance: Float = 0.5f,
): Pair<Float, Float>? {
    if (!measuredWidth.isFinite() || !measuredHeight.isFinite()) return null
    if (measuredWidth <= 0f || measuredHeight <= 0f) return null
    if (
        kotlin.math.abs(measuredWidth - currentWidth) < tolerance &&
        kotlin.math.abs(measuredHeight - currentHeight) < tolerance
    ) {
        return null
    }
    return measuredWidth to measuredHeight
}

fun ViewContainer<*, *>.MarkdownMath(init: MarkdownMathView.() -> Unit) {
    addChild(MarkdownMathView(), init)
}
