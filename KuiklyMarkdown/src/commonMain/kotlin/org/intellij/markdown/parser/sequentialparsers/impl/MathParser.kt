package org.intellij.markdown.parser.sequentialparsers.impl

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

/**
 * `$...$` / `$$...$$` 配对。
 *
 * 只做配对、不做 LaTeX 校验，但会拒绝明显不是公式的场景，避免把正文吞进公式：
 * - 起始 `$` 右侧紧邻空白（或到结尾）—— `$$` 后紧跟换行除外，那是多行块级公式
 * - 起始 `$` 右侧是纯数字且数字后面是空白/结尾 —— 货币写法（`$5 美元`、`$100 + $200`）
 * - 结束 `$` 左侧紧邻空白 —— `$$` 前紧跟换行除外，同上
 *
 * 行内公式不允许跨行；块级公式允许写成
 * ```
 * $$
 * x^2
 * $$
 * ```
 */
class MathParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            if (iterator.type == GFMTokenTypes.DOLLAR && isMathOpener(tokens, iterator)) {

                val endIterator = findMathCloser(tokens, iterator.advance(), iterator.length)

                if (endIterator != null) {
                    if (iterator.length == 1) {
                        result.withNode(SequentialParser.Node(iterator.index..endIterator.index + 1, GFMElementTypes.INLINE_MATH))
                    } else {
                        result.withNode(SequentialParser.Node(iterator.index..endIterator.index + 1, GFMElementTypes.BLOCK_MATH))
                    }
                    iterator = endIterator.advance()
                    continue
                }
            }
            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    private fun findMathCloser(
        tokens: TokensCache,
        it: TokensCache.Iterator,
        length: Int,
    ): TokensCache.Iterator? {
        var iterator = it
        while (iterator.type != null) {
            if (length == 1 && iterator.type == MarkdownTokenTypes.EOL) {
                return null
            }
            if (iterator.type == GFMTokenTypes.DOLLAR &&
                iterator.length == length &&
                isMathCloser(tokens, iterator)
            ) {
                return iterator
            }

            iterator = iterator.advance()
        }
        return null
    }

    /** 起始 `$`：右侧不能是空白/结尾，也不能是货币写法 */
    private fun isMathOpener(tokens: TokensCache, it: TokensCache.Iterator): Boolean {
        var pos = it.end
        val first = tokens.getRawCharAt(pos)
        if (first == END) return false
        if (first.isWhitespace()) {
            // 仅块级公式可以跨行：`$$` 后紧跟换行
            return it.length == 2 && first == '\n'
        }
        // 货币：$5、$100 后面直接跟空白或结尾时按普通文本处理
        if (first.isDigit()) {
            while (tokens.getRawCharAt(pos).isDigit()) pos++
            val after = tokens.getRawCharAt(pos)
            if (after == END || after.isWhitespace()) return false
        }
        return true
    }

    /** 结束 `$`：左侧不能是空白/开头 */
    private fun isMathCloser(tokens: TokensCache, it: TokensCache.Iterator): Boolean {
        val prev = tokens.getRawCharAt(it.start - 1)
        if (prev == END) return false
        if (!prev.isWhitespace()) return true
        // 仅块级公式可以跨行：`$$` 前紧跟换行（即 `$$` 独占一行）
        return it.length == 2 && prev == '\n'
    }

    private companion object {
        const val END = '\u0000'
    }
}
