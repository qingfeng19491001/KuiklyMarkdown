package com.tencent.kuiklybase.elements

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.ClickParams
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.isVirtualView
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.views.ImageSpan
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdownRootView
import com.tencent.kuiklybase.annotator.StyledTextSegment
import com.tencent.kuiklybase.annotator.buildStyledTextSegments
import com.tencent.kuiklybase.config.FontStyle
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.math.inlineMathLineSpacing
import com.tencent.kuiklybase.model.ReferenceLinkHandler
import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType

private fun ClickParams.toMarkdownRelativeParams(frame: Frame): ClickParams = ClickParams(
    x = frame.x,
    y = frame.y,
    pageX = pageX,
    pageY = pageY,
    params = params,
)

private fun ClickParams.toMarkdownRelativeEventJSONObject(frame: Frame): JSONObject = JSONObject().apply {
    put("x", frame.x)
    put("y", frame.y)
    put("pageX", pageX)
    put("pageY", pageY)
    params?.let { put("params", it) }
}

private fun LongPressParams.toMarkdownRelativeParams(frame: Frame): LongPressParams = LongPressParams(
    x = frame.x,
    y = frame.y,
    pageX = pageX,
    pageY = pageY,
    state = state,
    isCancel = isCancel,
    params = params,
)

private fun LongPressParams.toMarkdownRelativeEventJSONObject(frame: Frame): JSONObject = JSONObject().apply {
    put("x", frame.x)
    put("y", frame.y)
    put("pageX", pageX)
    put("pageY", pageY)
    put("state", state)
    put("isCancel", isCancel)
    params?.let { put("params", it) }
}

private fun ViewContainer<*, *>.convertFrameToMarkdown(frame: Frame): Frame {
    var targetView: ViewContainer<*, *>? = this

    var resX = frame.x
    var resY = frame.y

    while (targetView != null) {
        if (targetView is KuiklyMarkdownRootView) {
            return Frame(resX, resY, frame.width, frame.height)
        }

        resX += targetView.frame.x
        resY += targetView.frame.y
        targetView = targetView.domParent
        while (targetView?.isVirtualView() == true) {
            targetView = targetView.parent
        }
    }

    return frame
}

private val StyledTextSegment.isDisplayMath: Boolean
    get() = mathDisplay && mathLatex != null

private val StyledTextSegment.hasInlineContent: Boolean
    get() = text.isNotEmpty() || imageUrl != null

private val StyledTextSegment.isPlainText: Boolean
    get() = !bold && !italic && !strikethrough && !isCode &&
        linkUrl == null && imageUrl == null && color == null && fontSize == null &&
        mathLatex == null

private val StyledTextSegment.isInlineMath: Boolean
    get() = mathLatex != null && !mathDisplay

/**
 * 使用 Kuikly RichText 渲染带样式的文本片段列表。
 * 块级公式从段落中切开独立排版；行内公式以 PlaceholderSpan 参与文本排版，再叠加 Native Math View。
 */
fun ViewContainer<*, *>.markdownRichText(
    segments: List<StyledTextSegment>,
    config: MarkdownConfig,
    style: TextStyleConfig,
) {
    if (segments.isEmpty()) return

    if (segments.any { it.isDisplayMath || it.isInlineMath }) {
        val pending = mutableListOf<StyledTextSegment>()
        fun flushInline() {
            if (pending.isEmpty()) return
            renderInlineFlow(pending.toList(), config, style)
            pending.clear()
        }
        segments.forEach { segment ->
            if (segment.isDisplayMath) {
                flushInline()
                val latex = segment.mathLatex.orEmpty()
                markdownBlockMath(
                    latex = latex,
                    style = style,
                    config = config,
                )
            } else {
                pending += segment
            }
        }
        flushInline()
        return
    }

    markdownInlineRichText(segments, config, style)
}

private fun ViewContainer<*, *>.renderInlineFlow(
    segments: List<StyledTextSegment>,
    config: MarkdownConfig,
    style: TextStyleConfig,
) {
    if (segments.none { it.isInlineMath }) {
        if (segments.any { it.hasInlineContent }) {
            markdownInlineRichText(segments, config, style)
        }
        return
    }
    View {
        val slots = segments.mapNotNull { segment ->
            if (segment.isInlineMath) inlineMathSlot(segment.mathLatex.orEmpty(), style) else null
        }
        markdownInlineRichText(segments, config, style, slots)
        slots.forEach { markdownInlineMathOverlay(it, style, config) }
    }
}

private fun ViewContainer<*, *>.markdownInlineRichText(
    segments: List<StyledTextSegment>,
    config: MarkdownConfig,
    style: TextStyleConfig,
    mathSlots: List<InlineMathSlot> = emptyList(),
) {
    if (segments.isEmpty()) return

    // 如果全部 segment 都是纯文本（无特殊样式），使用简单 Text
    val allPlain = segments.all { it.isPlainText }

    if (allPlain) {
        val fullText = segments.joinToString("") { it.text }
        if (fullText.isBlank()) return
        Text {
            attr {
                text(fullText)
                fontSize(style.fontSize)
//                style.fontFamily?.let { fontFamily(it) }
                if (style.fontWeight == FontWeight.Bold) {
                    fontWeightBold()
                }
                if (style.fontStyle == FontStyle.Italic) {
                    fontStyleItalic()
                }
                val textColor = style.color ?: config.colors.text
                color(textColor)
                style.lineHeight?.let { lineHeight(it) }
            }
        }
        return
    }

    // 使用 RichText 渲染
    RichText {
        val spanLineHeight = style.lineHeight?.takeIf { mathSlots.isEmpty() }
        if (mathSlots.isNotEmpty()) {
            style.lineHeight?.let {
                attr { lineSpacing(inlineMathLineSpacing(style)) }
            }
        }
        var mathIndex = 0
        segments.forEach { segment ->
            if (segment.isInlineMath) {
                mathSlots.getOrNull(mathIndex++)?.let { InlineMathPlaceholder(it) }
            } else if (segment.imageUrl != null) {
                // 内联图片
                val transformedUrl = config.imageUrlTransformer?.invoke(segment.imageUrl) ?: segment.imageUrl
                ImageSpan {
                    src(transformedUrl)
                    size(style.fontSize * 1.2f, style.fontSize * 1.2f)
                }
            } else {
                // 文本 Span
                Span {
                    text(segment.text)
                    fontSize(segment.fontSize ?: style.fontSize)
//                    style.fontFamily?.let { fontFamily(it) }
                    spanLineHeight?.let { lineHeight(it) }

                    // 加粗
                    if (segment.bold || style.fontWeight == FontWeight.Bold) {
                        fontWeightBold()
                    }

                    // 斜体
                    if (segment.italic || style.fontStyle == FontStyle.Italic) {
                        fontStyleItalic()
                    }

                    // 删除线
                    if (segment.strikethrough) {
                        textDecorationLineThrough()
                    }

                    // 颜色：segment 覆盖 > 链接色 > style 色 > 默认文本色
                    val effectiveColor = segment.color
                        ?: if (segment.linkUrl != null) config.colors.linkColor
                        else style.color
                        ?: config.colors.text
                    color(effectiveColor)

                    // 行内代码背景
                    if (segment.isCode) {
                        backgroundColor(config.colors.inlineCodeBackground)
                    }

                    // 链接点击
                    if (segment.linkUrl != null) {
                        config.onLinkClick?.let { onClick ->
                            val url = segment.linkUrl
                            click { clickParams ->
                                val convertedFrame = this@markdownInlineRichText.convertFrameToMarkdown(
                                    Frame(clickParams.x, clickParams.y, 0f, 0f),
                                )
                                onClick(url, clickParams.toMarkdownRelativeParams(convertedFrame))
                            }
                        }
                    }
                    if (segment.linkUrl != null) {
                        config.onLinkLongPress?.let { onLongPress ->
                            val url = segment.linkUrl
                            longPress { longPressParams ->
                                val convertedFrame = this@markdownInlineRichText.convertFrameToMarkdown(
                                    Frame(longPressParams.x, longPressParams.y, 0f, 0f),
                                )
                                onLongPress(url, longPressParams.toMarkdownRelativeParams(convertedFrame))
                            }
                        }
                    }
                }
            }
        }
        event {
            if (config.onLinkClick != null) {
                click { clickParams ->
                    config.eventProxyViewRef?.let {
                        val proxyView = config.eventProxyViewRef.view
                        val convertedFrame = this@markdownInlineRichText.convertFrame(Frame(clickParams.x, clickParams.y, 0f, 0f), proxyView as? ViewContainer<*, *>)
                        proxyView?.getViewEvent()?.onFireEvent(
                            EventName.CLICK.value,
                            clickParams.toMarkdownRelativeEventJSONObject(convertedFrame),
                        )
                    }
                }
            }

            if (config.onLinkLongPress != null) {
                longPress { longPressParams ->
                    config.eventProxyViewRef?.let {
                        val proxyView = config.eventProxyViewRef.view
                        val convertedFrame = this@markdownInlineRichText.convertFrame(Frame(longPressParams.x, longPressParams.y, 0f, 0f), proxyView as? ViewContainer<*, *>)
                        proxyView?.getViewEvent()?.onFireEvent(
                            EventName.LONG_PRESS.value,
                            longPressParams.toMarkdownRelativeEventJSONObject(convertedFrame),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 从 AST 节点构建 segments 并渲染为 RichText
 */
fun ViewContainer<*, *>.markdownText(
    content: String,
    node: ASTNode,
    style: TextStyleConfig,
    config: MarkdownConfig,
    referenceLinkHandler: ReferenceLinkHandler? = null,
    contentChildType: IElementType? = null,
) {
    val childNode = contentChildType?.run(node::findChildOfType) ?: node
    val segments = buildStyledTextSegments(
        content = content,
        node = childNode,
        config = config,
        referenceLinkHandler = referenceLinkHandler,
    )
    markdownRichText(segments, config, style)
}

/**
 * 渲染纯文本（无 AST 解析）
 */
fun ViewContainer<*, *>.markdownPlainText(
    text: String,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    if (text.isBlank()) return
    Text {
        attr {
            text(text)
            fontSize(style.fontSize)
//            style.fontFamily?.let { fontFamily(it) }
            if (style.fontWeight == FontWeight.Bold) {
                fontWeightBold()
            }
            if (style.fontStyle == FontStyle.Italic) {
                fontStyleItalic()
            }
            val textColor = style.color ?: config.colors.text
            color(textColor)
            style.lineHeight?.let { lineHeight(it) }
        }
    }
}
