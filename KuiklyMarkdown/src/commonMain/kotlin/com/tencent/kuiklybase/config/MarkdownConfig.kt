package com.tencent.kuiklybase.config

import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.event.ClickParams
import com.tencent.kuikly.core.base.event.LongPressParams

/**
 * Markdown 渲染统一配置
 * 替代 Compose 的 CompositionLocal 机制，通过参数传递配置
 */
class MarkdownConfig(
    val colors: MarkdownColors = MarkdownColors(),
    val typography: MarkdownTypography = MarkdownTypography(),
    val dimens: MarkdownDimens = MarkdownDimens(),
    val padding: MarkdownPadding = MarkdownPadding(),
    /** 链接点击处理器 */
    val onLinkClick: ((url: String, params: ClickParams) -> Unit)? = null,
    val onLinkLongPress: ((url: String, params: LongPressParams) -> Unit)? = null,
    /**
     * 事件代理 ViewRef
     *  配置了 `onLinkClick/onLongPress` 后，链接所在的富文本会消费点击/长按事件
     *  外层 markdown 无法直接收到点击/长按事件，补发一份事件到proxyView。
     */
    val eventProxyViewRef: ViewRef<*>? = null,
    /** 图片加载 URL 变换（可用于添加代理、CDN 前缀等） */
    val imageUrlTransformer: ((url: String) -> String)? = null,
    /** 有序列表标记生成器 */
    val orderedListBullet: (index: Int, listNumber: Int, depth: Int) -> String = { index, listNumber, _ ->
        "${listNumber + index}. "
    },
    /** 无序列表标记生成器 */
    val unorderedListBullet: (index: Int, depth: Int) -> String = { _, depth ->
        when (depth % 3) {
            0 -> "• "
            1 -> "◦ "
            else -> "▪ "
        }
    },
    /** 是否将 EOL 视为换行 */
    val eolAsNewLine: Boolean = false,
    /** 代码高亮配置 */
    val codeHighlightEnabled: Boolean = true,
    /** 代码高亮是否使用暗色主题 */
    val codeHighlightDarkTheme: Boolean = false,
) {

    constructor(
        colors: MarkdownColors = MarkdownColors(),
        typography: MarkdownTypography = MarkdownTypography(),
        dimens: MarkdownDimens = MarkdownDimens(),
        padding: MarkdownPadding = MarkdownPadding(),
        onLinkClick: ((url: String) -> Unit)? = null,
        imageUrlTransformer: ((url: String) -> String)? = null,
        orderedListBullet: (index: Int, listNumber: Int, depth: Int) -> String = { index, listNumber, _ ->
            "${listNumber + index}. "
        },
        unorderedListBullet: (index: Int, depth: Int) -> String = { _, depth ->
            when (depth % 3) {
                0 -> "• "
                1 -> "◦ "
                else -> "▪ "
            }
        },
        eolAsNewLine: Boolean = false,
        codeHighlightEnabled: Boolean = true,
        codeHighlightDarkTheme: Boolean = false,
    ) : this(
        colors = colors,
        typography = typography,
        dimens = dimens,
        padding = padding,
        onLinkClick = if (onLinkClick != null) ({ url: String, _: ClickParams -> onLinkClick(url) }) else null,
        onLinkLongPress = null,
        eventProxyViewRef = null,
        imageUrlTransformer = imageUrlTransformer,
        orderedListBullet = orderedListBullet,
        unorderedListBullet = unorderedListBullet,
        eolAsNewLine = eolAsNewLine,
        codeHighlightEnabled = codeHighlightEnabled,
        codeHighlightDarkTheme = codeHighlightDarkTheme,
    )

    companion object {
        /** 默认配置 */
        val Default = MarkdownConfig()
    }
}
