package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.LinkAnnotation
import com.tencent.kuikly.compose.ui.text.LinkInteractionListener
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuikly.compose.ui.text.font.FontFamily
import com.tencent.kuikly.compose.ui.text.font.FontStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextDecoration
import com.tencent.kuikly.compose.ui.text.withStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.base.event.ClickParams
import com.tencent.kuiklybase.annotator.StyledTextSegment
import com.tencent.kuiklybase.components.MarkdownComponents
import com.tencent.kuiklybase.components.markdownComponents
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import com.tencent.kuiklybase.config.FontStyle as MarkdownFontStyle
import com.tencent.kuiklybase.config.FontWeight as MarkdownFontWeight

/**
 * Kuikly Compose-native Markdown renderer.
 *
 * [components] is retained for source and binary compatibility with earlier releases. Traditional
 * Kuikly DSL component lambdas cannot be mounted safely in a Compose tree, so this entry point
 * always uses the Compose renderer. Use `ViewContainer.KuiklyMarkdown` when custom traditional DSL
 * renderers are required.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MarkdownView(
    modifier: Modifier = Modifier,
    content: String,
    config: MarkdownConfig = MarkdownConfig.Default,
    components: MarkdownComponents = markdownComponents(),
    flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
    lookupLinks: Boolean = true,
) {
    val blocks =
        remember(content, config, flavour, lookupLinks) {
            buildMarkdownRenderModel(content, config, flavour, lookupLinks)
        }
    Column(modifier = modifier) {
        blocks.forEach { block -> MarkdownBlock(block, config) }
    }
}

@Composable
private fun MarkdownBlock(
    block: MarkdownRenderBlock,
    config: MarkdownConfig,
) {
    when (block) {
        is MarkdownRenderBlock.RichText -> MarkdownRichText(block.segments, block.style, config)
        is MarkdownRenderBlock.Code -> MarkdownCode(block, config)
        is MarkdownRenderBlock.ListBlock -> MarkdownList(block, config)
        is MarkdownRenderBlock.Quote -> MarkdownQuote(block, config)
        is MarkdownRenderBlock.Image -> MarkdownImage(block)
        MarkdownRenderBlock.Divider -> MarkdownDivider(config)
        is MarkdownRenderBlock.Table -> MarkdownTable(block, config)
    }
}

@Composable
private fun MarkdownRichText(
    segments: List<StyledTextSegment>,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    val text = remember(segments, style, config) { segments.toAnnotatedString(style, config) }
    Text(
        text = text,
        color = Color(style.color ?: config.colors.text),
        fontSize = style.fontSize.sp,
        fontWeight = style.fontWeight.toCompose(),
        fontStyle = style.fontStyle.toCompose(),
        fontFamily = style.fontFamily.toComposeFontFamily(),
        lineHeight = style.lineHeight?.sp ?: com.tencent.kuikly.compose.ui.unit.TextUnit.Unspecified,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = config.padding.block.dp),
    )
}

private fun List<StyledTextSegment>.toAnnotatedString(
    style: TextStyleConfig,
    config: MarkdownConfig,
): AnnotatedString =
    buildAnnotatedString {
        forEach { segment ->
            if (segment.imageUrl != null) return@forEach
            val start = length
            withStyle(
                SpanStyle(
                    color =
                        Color(segment.color ?: if (segment.linkUrl != null) config.colors.linkColor else style.color ?: config.colors.text),
                    fontSize = (segment.fontSize ?: style.fontSize).sp,
                    fontWeight = if (segment.bold) FontWeight.Bold else null,
                    fontStyle = if (segment.italic) FontStyle.Italic else null,
                    fontFamily = if (segment.isCode) FontFamily.Monospace else null,
                    background = if (segment.isCode) Color(config.colors.inlineCodeBackground) else Color.Unspecified,
                    textDecoration =
                        when {
                            segment.strikethrough -> TextDecoration.LineThrough
                            segment.linkUrl != null -> TextDecoration.Underline
                            else -> null
                        },
                ),
            ) {
                append(segment.text)
            }
            val url = segment.linkUrl
            if (url != null && length > start) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = url,
                        styles =
                            TextLinkStyles(
                                style =
                                    SpanStyle(
                                        color = Color(config.colors.linkColor),
                                        textDecoration = TextDecoration.Underline,
                                    ),
                            ),
                        linkInteractionListener =
                            LinkInteractionListener {
                                config.onLinkClick?.invoke(url, emptyClickParams())
                            },
                    ),
                    start,
                    length,
                )
            }
        }
    }

private fun emptyClickParams() =
    ClickParams(
        x = 0f,
        y = 0f,
        pageX = 0f,
        pageY = 0f,
        params = null,
    )

@Composable
private fun MarkdownCode(
    block: MarkdownRenderBlock.Code,
    config: MarkdownConfig,
) {
    val style = config.typography.code
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = config.padding.block.dp)
                .clip(RoundedCornerShape(config.dimens.codeBackgroundCornerSize.dp))
                .background(Color(config.colors.codeBackground))
                .padding(config.padding.codeBlock.dp),
    ) {
        block.language?.let { language ->
            Text(
                text = language,
                color = Color(config.colors.codeText),
                fontSize = (style.fontSize * 0.85f).sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(config.dimens.dividerThickness.dp)
                    .background(Color(config.colors.dividerColor)),
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = block.code,
            color = Color(style.color ?: config.colors.codeText),
            fontSize = style.fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = style.lineHeight?.sp ?: com.tencent.kuikly.compose.ui.unit.TextUnit.Unspecified,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MarkdownList(
    block: MarkdownRenderBlock.ListBlock,
    config: MarkdownConfig,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = (config.padding.listIndent * block.depth).dp,
                    top = config.padding.list.dp,
                    bottom = config.padding.list.dp,
                ),
    ) {
        block.items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = config.padding.listItemTop.dp,
                            bottom = config.padding.listItemBottom.dp,
                        ),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.marker,
                    color = Color(config.colors.text),
                    fontSize = config.typography.list.fontSize.sp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    item.children.forEach { MarkdownBlock(it, config) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownQuote(
    block: MarkdownRenderBlock.Quote,
    config: MarkdownConfig,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = config.padding.block.dp)
                .clip(RoundedCornerShape(config.dimens.blockQuoteCornerSize.dp))
                .background(Color(config.colors.blockQuoteBackground)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(config.dimens.blockQuoteThickness.dp)
                .heightIn(min = (config.typography.quote.fontSize + config.padding.blockQuoteTextVertical * 2).dp)
                .background(Color(config.colors.blockQuoteBar)),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = config.padding.blockQuotePaddingLeft.dp,
                        end = config.padding.blockQuotePaddingLeft.dp,
                        top = config.padding.blockQuoteTextVertical.dp,
                        bottom = config.padding.blockQuoteTextVertical.dp,
                    ),
        ) {
            block.children.forEach { MarkdownBlock(it, config) }
        }
    }
}

@Composable
private fun MarkdownImage(block: MarkdownRenderBlock.Image) {
    Image(
        painter = rememberAsyncImagePainter(block.url),
        contentDescription = block.description,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(vertical = 4.dp),
    )
}

@Composable
private fun MarkdownDivider(config: MarkdownConfig) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = config.padding.block.dp)
            .height(config.dimens.dividerThickness.dp)
            .background(Color(config.colors.dividerColor)),
    )
}

@Composable
private fun MarkdownTable(
    block: MarkdownRenderBlock.Table,
    config: MarkdownConfig,
) {
    val shape = RoundedCornerShape(config.dimens.tableCornerSize.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = config.padding.block.dp)
                .clip(shape)
                .background(Color(config.colors.tableBackground)),
    ) {
        MarkdownTableRow(block.header, config, header = true)
        block.rows.forEach { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(config.dimens.dividerThickness.dp)
                    .background(Color(config.colors.dividerColor)),
            )
            MarkdownTableRow(row, config, header = false)
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<List<StyledTextSegment>>,
    config: MarkdownConfig,
    header: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { segments ->
            val style =
                if (header) {
                    config.typography.table.copy(fontWeight = MarkdownFontWeight.Bold)
                } else {
                    config.typography.table
                }
            val text = remember(segments, style, config) { segments.toAnnotatedString(style, config) }
            Text(
                text = text,
                color = Color(style.color ?: config.colors.text),
                fontSize = style.fontSize.sp,
                fontWeight = style.fontWeight.toCompose(),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(config.dimens.tableCellPadding.dp),
            )
        }
    }
}

private fun MarkdownFontWeight.toCompose(): FontWeight =
    when (this) {
        MarkdownFontWeight.Normal -> FontWeight.Normal
        MarkdownFontWeight.Medium -> FontWeight.Medium
        MarkdownFontWeight.SemiBold -> FontWeight.SemiBold
        MarkdownFontWeight.Bold -> FontWeight.Bold
    }

private fun MarkdownFontStyle.toCompose(): FontStyle =
    when (this) {
        MarkdownFontStyle.Normal -> FontStyle.Normal
        MarkdownFontStyle.Italic -> FontStyle.Italic
    }

private fun String?.toComposeFontFamily(): FontFamily? =
    when (this?.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "sans-serif", "sans" -> FontFamily.SansSerif
        else -> null
    }
