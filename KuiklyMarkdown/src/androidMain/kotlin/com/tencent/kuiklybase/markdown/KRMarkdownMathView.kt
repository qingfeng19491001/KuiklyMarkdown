package com.tencent.kuiklybase.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.ImageView
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import ru.noties.jlatexmath.JLatexMathDrawable
import com.tencent.kuiklybase.math.containsUnsafeLatexFileCommand
import com.tencent.kuiklybase.math.inlineMathBaselinePadding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class KRMarkdownMathView(context: Context) : ImageView(context), IKuiklyRenderViewExport {

    private var latex: String = ""
    private var display: Boolean = false
    private var fontSizeDp: Float = 16f
    private var colorArgb: Int = 0xFF333333.toInt()
    private var onMeasured: KuiklyRenderCallback? = null
    private var lastReportedW = -1f
    private var lastReportedH = -1f
    private val renderGeneration = AtomicInteger(0)
    private var lastRenderKey: String = ""
    private var fallbackText: String? = null
    private var fallbackBaseline = 0f
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
    }
    private val renderRunnable = Runnable { render() }

    init {
        scaleType = ScaleType.FIT_CENTER
        adjustViewBounds = true
        setBackgroundColor(0x00000000)
    }

    override fun setProp(propKey: String, propValue: Any): Boolean {
        return when (propKey) {
            "latex" -> {
                latex = propValue.toString()
                scheduleRender()
                true
            }
            "display" -> {
                display = toBoolean(propValue)
                scheduleRender()
                true
            }
            "fontSize" -> {
                fontSizeDp = toFloat(propValue)
                scheduleRender()
                true
            }
            "color" -> {
                colorArgb = toColorInt(propValue)
                scheduleRender()
                true
            }
            "onMeasured" -> {
                onMeasured = propValue as? KuiklyRenderCallback
                if (lastReportedW > 0f && lastReportedH > 0f) {
                    onMeasured?.invoke(
                        mapOf(
                            "width" to lastReportedW,
                            "height" to lastReportedH,
                        ),
                    )
                }
                true
            }
            else -> super.setProp(propKey, propValue)
        }
    }

    /** Coalesce latex/display/fontSize/color updates into one render per frame. */
    private fun scheduleRender() {
        removeCallbacks(renderRunnable)
        post(renderRunnable)
    }

    private fun render() {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            fontSizeDp,
            resources.displayMetrics,
        )
        val source = latex.take(MAX_LATEX_CHARS)
        val renderKey = "$source|${latex.length}|$display|$fontSizeDp|$colorArgb"
        if (renderKey == lastRenderKey) return
        lastRenderKey = renderKey
        val generation = renderGeneration.incrementAndGet()
        if (source.isBlank()) {
            showFallbackOnUi("", textSizePx, generation)
            return
        }
        if (latex.length > MAX_LATEX_CHARS) {
            showFallbackOnUi(source.take(FALLBACK_TEXT_CHARS) + "…", textSizePx, generation)
            return
        }
        // JLaTeXMath 支持 \includegraphics 等可触达本地文件的宏；不可信 Markdown 公式禁止进入引擎。
        if (containsUnsafeLatexFileCommand(source)) {
            showFallbackOnUi(source.take(FALLBACK_TEXT_CHARS) + "…", textSizePx, generation)
            return
        }
        renderExecutor.execute {
            try {
                // JLatexMathDrawable 固定以 STYLE_DISPLAY 排版，行内公式需显式切回 text style。
                val drawable = JLatexMathDrawable.builder(if (display) source else "\\textstyle $source")
                    .textSize(textSizePx)
                    .color(colorArgb)
                    .align(
                        if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT,
                    )
                    .build()
                val content: Drawable = if (display) drawable else baselineAligned(drawable, textSizePx)
                mainHandler.post {
                    if (generation != renderGeneration.get()) return@post
                    fallbackText = null
                    setImageDrawable(content)
                    reportSize(content.intrinsicWidth.toFloat(), content.intrinsicHeight.toFloat())
                    invalidate()
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (generation != renderGeneration.get()) return@post
                    showFallback(source, textSizePx)
                }
            }
        }
    }

    private fun baselineAligned(drawable: JLatexMathDrawable, textSizePx: Float): Drawable {
        val depth = drawable.icon().iconDepth.toFloat()
        val ascent = drawable.intrinsicHeight - depth
        val metrics = Paint().apply { textSize = textSizePx }.fontMetrics
        val lineCenter = -(metrics.ascent + metrics.descent) / 2f
        val (top, bottom) = inlineMathBaselinePadding(ascent, depth, lineCenter)
        return VerticalPaddedDrawable(drawable, top.roundToInt(), bottom.roundToInt())
    }

    /** JLatexMathDrawable 绘制时忽略 bounds 的 left/top，InsetDrawable 的偏移不生效，需自行平移画布。 */
    private class VerticalPaddedDrawable(
        private val inner: Drawable,
        private val top: Int,
        private val bottom: Int,
    ) : Drawable() {
        override fun getIntrinsicWidth(): Int = inner.intrinsicWidth

        override fun getIntrinsicHeight(): Int = inner.intrinsicHeight + top + bottom

        override fun onBoundsChange(bounds: Rect) {
            inner.setBounds(0, 0, bounds.width(), (bounds.height() - top - bottom).coerceAtLeast(0))
        }

        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            canvas.translate(bounds.left.toFloat(), (bounds.top + top).toFloat())
            inner.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            inner.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun showFallbackOnUi(text: String, textSizePx: Float, generation: Int) {
        mainHandler.post {
            if (generation != renderGeneration.get()) return@post
            showFallback(text, textSizePx)
        }
    }

    private fun showFallback(text: String, textSizePx: Float) {
        fallbackText = text
        setImageDrawable(null)
        fallbackPaint.color = colorArgb
        fallbackPaint.textSize = textSizePx
        val width = fallbackPaint.measureText(text).coerceAtLeast(1f)
        val fontMetrics = fallbackPaint.fontMetrics
        fallbackBaseline = -fontMetrics.top
        val height = (fontMetrics.bottom - fontMetrics.top).coerceAtLeast(1f)
        reportSize(width, height)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val text = fallbackText
        if (text == null) {
            super.onDraw(canvas)
            return
        }
        canvas.drawText(text, 0f, fallbackBaseline, fallbackPaint)
    }

    private fun reportSize(widthPx: Float, heightPx: Float) {
        val density = resources.displayMetrics.density.coerceAtLeast(0.5f)
        val widthDp = (widthPx / density).coerceAtLeast(1f)
        val heightDp = (heightPx / density).coerceAtLeast(1f)
        if (widthDp == lastReportedW && heightDp == lastReportedH) return
        lastReportedW = widthDp
        lastReportedH = heightDp
        post {
            onMeasured?.invoke(
                mapOf(
                    "width" to widthDp,
                    "height" to heightDp,
                ),
            )
        }
    }

    private fun toBoolean(value: Any): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "1" || value.equals("true", ignoreCase = true)
        else -> false
    }

    private fun toFloat(value: Any): Float = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: fontSizeDp
        else -> fontSizeDp
    }

    private fun toColorInt(value: Any): Int = when (value) {
        is Number -> value.toLong().toInt()
        is String -> value.toLongOrNull()?.toInt() ?: colorArgb
        else -> colorArgb
    }

    companion object {
        const val VIEW_NAME = "KRMarkdownMathView"
        private const val MAX_LATEX_CHARS = 4096
        private const val FALLBACK_TEXT_CHARS = 128
        private val mainHandler = Handler(Looper.getMainLooper())
        private val renderExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kr-markdown-math").apply { isDaemon = true }
        }
    }
}
