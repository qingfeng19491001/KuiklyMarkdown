package com.tencent.kuiklybase.markdown.compose

import com.tencent.kuiklybase.annotator.StyledTextSegment
import com.tencent.kuiklybase.annotator.buildStyledTextSegments
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.parser.MarkdownParseResult
import com.tencent.kuiklybase.parser.parseMarkdown
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

internal sealed interface MarkdownRenderBlock {
    data class RichText(
        val segments: List<StyledTextSegment>,
        val style: TextStyleConfig,
    ) : MarkdownRenderBlock

    data class Code(
        val code: String,
        val language: String?,
    ) : MarkdownRenderBlock

    data class ListBlock(
        val items: List<ListItem>,
        val depth: Int,
    ) : MarkdownRenderBlock

    data class Quote(
        val children: List<MarkdownRenderBlock>,
    ) : MarkdownRenderBlock

    data class Image(
        val url: String,
        val description: String,
    ) : MarkdownRenderBlock

    data object Divider : MarkdownRenderBlock

    data class Table(
        val header: List<List<StyledTextSegment>>,
        val rows: List<List<List<StyledTextSegment>>>,
    ) : MarkdownRenderBlock
}

internal data class ListItem(
    val marker: String,
    val checked: Boolean?,
    val children: List<MarkdownRenderBlock>,
)

internal fun buildMarkdownRenderModel(
    content: String,
    config: MarkdownConfig,
    flavour: MarkdownFlavourDescriptor,
    lookupLinks: Boolean,
): List<MarkdownRenderBlock> {
    if (content.isBlank()) return emptyList()
    val parsed = parseMarkdown(content, flavour, lookupLinks)
    return parsed.node.children.flatMap { child ->
        child.toRenderBlocks(parsed, config, depth = 0)
    }
}

private fun ASTNode.toRenderBlocks(
    parsed: MarkdownParseResult,
    config: MarkdownConfig,
    depth: Int,
): List<MarkdownRenderBlock> =
    when (type) {
        MarkdownElementTypes.ATX_1 -> richTextBlocks(parsed, config, config.typography.h1, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.ATX_2 -> richTextBlocks(parsed, config, config.typography.h2, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.ATX_3 -> richTextBlocks(parsed, config, config.typography.h3, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.ATX_4 -> richTextBlocks(parsed, config, config.typography.h4, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.ATX_5 -> richTextBlocks(parsed, config, config.typography.h5, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.ATX_6 -> richTextBlocks(parsed, config, config.typography.h6, MarkdownTokenTypes.ATX_CONTENT)
        MarkdownElementTypes.SETEXT_1 -> richTextBlocks(parsed, config, config.typography.h1, MarkdownTokenTypes.SETEXT_CONTENT)
        MarkdownElementTypes.SETEXT_2 -> richTextBlocks(parsed, config, config.typography.h2, MarkdownTokenTypes.SETEXT_CONTENT)
        MarkdownElementTypes.PARAGRAPH -> richTextBlocks(parsed, config, config.typography.paragraph)
        MarkdownElementTypes.CODE_FENCE -> listOf(codeFenceBlock(parsed.content))
        MarkdownElementTypes.CODE_BLOCK -> listOf(codeBlock(parsed.content))
        MarkdownElementTypes.ORDERED_LIST -> listOf(listBlock(parsed, config, depth, ordered = true))
        MarkdownElementTypes.UNORDERED_LIST -> listOf(listBlock(parsed, config, depth, ordered = false))
        MarkdownElementTypes.BLOCK_QUOTE -> {
            val quoteChildren = children.flatMap { it.toRenderBlocks(parsed, config, depth) }
            if (quoteChildren.isEmpty()) emptyList() else listOf(MarkdownRenderBlock.Quote(quoteChildren))
        }
        MarkdownElementTypes.IMAGE -> imageBlock(parsed.content, config)?.let(::listOf) ?: emptyList()
        MarkdownTokenTypes.HORIZONTAL_RULE -> listOf(MarkdownRenderBlock.Divider)
        GFMElementTypes.TABLE -> tableBlock(parsed, config)?.let(::listOf) ?: emptyList()
        MarkdownTokenTypes.TEXT -> richTextBlocks(parsed, config, config.typography.text)
        else -> children.flatMap { it.toRenderBlocks(parsed, config, depth) }
    }

private fun ASTNode.richTextBlocks(
    parsed: MarkdownParseResult,
    config: MarkdownConfig,
    style: TextStyleConfig,
    contentChildType: org.intellij.markdown.IElementType? = null,
): List<MarkdownRenderBlock> {
    val textNode = contentChildType?.let(::findChildOfType) ?: this
    val segments =
        buildStyledTextSegments(
            content = parsed.content,
            node = textNode,
            config = config,
            referenceLinkHandler = parsed.referenceLinkHandler,
        )
    if (segments.isEmpty()) return emptyList()

    val blocks = mutableListOf<MarkdownRenderBlock>()
    val pendingText = mutableListOf<StyledTextSegment>()

    fun flushText() {
        if (pendingText.any { it.text.isNotEmpty() }) {
            blocks += MarkdownRenderBlock.RichText(pendingText.toList(), style)
        }
        pendingText.clear()
    }

    segments.forEach { segment ->
        val imageUrl = segment.imageUrl
        if (imageUrl == null) {
            pendingText += segment
        } else {
            flushText()
            val transformed = config.imageUrlTransformer?.invoke(imageUrl) ?: imageUrl
            blocks += MarkdownRenderBlock.Image(transformed, description = "Markdown image")
        }
    }
    flushText()
    return blocks
}

private fun ASTNode.codeFenceBlock(content: String): MarkdownRenderBlock.Code {
    val language =
        findChildOfType(MarkdownTokenTypes.FENCE_LANG)
            ?.getTextInNode(content)
            ?.toString()
            ?.trim()
            ?.ifEmpty { null }
    val codeNodes = children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    val code =
        if (codeNodes.isNotEmpty()) {
            codeNodes.joinToString("\n") { it.getTextInNode(content).toString() }
        } else {
            getTextInNode(content)
                .toString()
                .lines()
                .drop(1)
                .dropLast(1)
                .joinToString("\n")
        }
    return MarkdownRenderBlock.Code(code.trimEnd(), language)
}

private fun ASTNode.codeBlock(content: String): MarkdownRenderBlock.Code {
    val code = getTextInNode(content).toString().replaceIndent().trimEnd()
    return MarkdownRenderBlock.Code(code, language = null)
}

private fun ASTNode.listBlock(
    parsed: MarkdownParseResult,
    config: MarkdownConfig,
    depth: Int,
    ordered: Boolean,
): MarkdownRenderBlock.ListBlock {
    val listItems = children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
    val initialNumber =
        listItems
            .firstOrNull()
            ?.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)
            ?.getTextInNode(parsed.content)
            ?.toString()
            ?.takeWhile(Char::isDigit)
            ?.toIntOrNull()
            ?: 1

    return MarkdownRenderBlock.ListBlock(
        depth = depth,
        items =
            listItems.mapIndexed { index, itemNode ->
                val checkbox =
                    itemNode.findChildOfType(GFMTokenTypes.CHECK_BOX)?.let {
                        it.getTextInNode(parsed.content).toString().contains("[x]", ignoreCase = true)
                    }
                val marker =
                    when {
                        checkbox == true -> "☑ "
                        checkbox == false -> "☐ "
                        ordered -> config.orderedListBullet(index, initialNumber, depth)
                        else -> config.unorderedListBullet(index, depth)
                    }
                val itemChildren =
                    itemNode.children.flatMap { child ->
                        when (child.type) {
                            MarkdownTokenTypes.LIST_BULLET,
                            MarkdownTokenTypes.LIST_NUMBER,
                            GFMTokenTypes.CHECK_BOX,
                            MarkdownTokenTypes.WHITE_SPACE,
                            MarkdownTokenTypes.EOL,
                            -> emptyList()
                            else -> child.toRenderBlocks(parsed, config, depth + 1)
                        }
                    }
                ListItem(marker, checkbox, itemChildren)
            },
    )
}

private fun ASTNode.imageBlock(
    content: String,
    config: MarkdownConfig,
): MarkdownRenderBlock.Image? {
    val destination =
        findDescendant(MarkdownElementTypes.LINK_DESTINATION)
            ?.getTextInNode(content)
            ?.toString()
            ?: return null
    val description =
        findDescendant(MarkdownElementTypes.LINK_TEXT)
            ?.getTextInNode(content)
            ?.toString()
            ?.trim('[', ']')
            ?.ifBlank { "Markdown image" }
            ?: "Markdown image"
    val url = config.imageUrlTransformer?.invoke(destination) ?: destination
    return MarkdownRenderBlock.Image(url, description)
}

private fun ASTNode.tableBlock(
    parsed: MarkdownParseResult,
    config: MarkdownConfig,
): MarkdownRenderBlock.Table? {
    fun ASTNode.cells(): List<List<StyledTextSegment>> =
        children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { cell ->
                buildStyledTextSegments(
                    content = parsed.content,
                    node = cell,
                    config = config,
                    referenceLinkHandler = parsed.referenceLinkHandler,
                )
            }

    val header = children.firstOrNull { it.type == GFMElementTypes.HEADER }?.cells().orEmpty()
    if (header.isEmpty()) return null
    val rows = children.filter { it.type == GFMElementTypes.ROW }.map { it.cells() }
    return MarkdownRenderBlock.Table(header, rows)
}

private fun ASTNode.findDescendant(target: org.intellij.markdown.IElementType): ASTNode? {
    if (type == target) return this
    children.forEach { child ->
        child.findDescendant(target)?.let { return it }
    }
    return null
}
