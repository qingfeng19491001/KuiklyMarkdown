package com.tencent.kuiklybase.streaming

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验收标准 5：Markdown、公式、代码块混排时，滚动和刷新稳定。
 *
 * 渲染层无法在单测里驱动，稳定性可观测的等价指标落在流式管道上：
 * - 已完成块的 id 不漂移 → diffUpdate 不重建视图 → 列表不跳动
 * - 增量解析不丢块 / 不重复块 → 内容不重影
 * - 同一文本重复 flush / 重新加载，结果幂等 → 刷新不产生差异
 */
class MarkdownMixedStabilityTest {

    @Test
    fun mixedDocumentProducesOnlyRenderableBlocks() {
        val state = MarkdownStreamingState()
        val blocks = assertNotNull(state.update(MIXED_DOC, force = true))

        val blockTypes = state.blockNodes.map { it.type.name }
        assertTrue(blockTypes.contains("ATX_1"), "缺少标题块: $blockTypes")
        assertTrue(blockTypes.contains("CODE_FENCE"), "缺少代码块: $blockTypes")
        assertTrue(blockTypes.contains("PARAGRAPH"), "缺少段落: $blockTypes")
        assertTrue(blockTypes.contains("TABLE"), "缺少表格: $blockTypes")
        assertTrue(blockTypes.contains("UNORDERED_LIST"), "缺少列表: $blockTypes")

        val nestedTypes = collectTypes(state.blockNodes)
        assertTrue(nestedTypes.contains("BLOCK_MATH"), "缺少块级公式节点: $nestedTypes")
        assertTrue(nestedTypes.contains("INLINE_MATH"), "缺少行内公式节点: $nestedTypes")

        // 所有块都必须能被渲染器分发，否则会退化成"递归子节点"的未知布局
        val unknown = state.blockNodes.filter { it.type !in RENDERABLE_BLOCK_TYPES }
        assertEquals(emptyList(), unknown.map { it.type.name }, "存在渲染器无法分发的块级节点")
        assertEquals(state.blockNodes.size, blocks.size)
    }

    @Test
    fun completedBlocksKeepIdsWhileStreaming() {
        val state = MarkdownStreamingState()
        var previous: List<MarkdownBlock> = emptyList()

        feedChunks(state, MIXED_DOC) { current ->
            val carried = previous.dropLast(1)
            val nextIds = current.map { it.id }.toSet()
            val lost = carried.filter { it.id !in nextIds }
            assertEquals(
                emptyList(),
                lost.map { it.blockContent },
                "已完成的块在下一帧丢失（会导致列表跳动/重影）",
            )
            previous = current
        }
        assertTrue(previous.isNotEmpty())
    }

    @Test
    fun churnIsLimitedToTheTypingTail() {
        val state = MarkdownStreamingState()
        var previous: List<MarkdownBlock> = emptyList()
        var maxChanged = 0

        feedChunks(state, MIXED_DOC) { current ->
            val size = minOf(previous.size, current.size)
            var firstChanged = size
            for (i in 0 until size) {
                if (previous[i].id != current[i].id) {
                    firstChanged = minOf(firstChanged, i)
                }
            }
            if (previous.isNotEmpty() && firstChanged < size) {
                // 变化只允许发生在尾部 2 个块以内
                assertTrue(
                    firstChanged >= size - 2,
                    "第 $firstChanged 块发生重建（共 $size 块），超出尾部抖动范围",
                )
                maxChanged = maxOf(maxChanged, size - firstChanged)
            }
            previous = current
        }
        assertTrue(maxChanged <= 2, "单帧重建块数 $maxChanged 超过 2")
    }

    @Test
    fun finalSnapshotMatchesOneShotParse() {
        val streamed = MarkdownStreamingState()
        var last: List<MarkdownBlock> = emptyList()
        feedChunks(streamed, MIXED_DOC) { last = it }

        val oneShot = MarkdownStreamingState()
        val direct = assertNotNull(oneShot.update(MIXED_DOC, force = true))

        assertEquals(direct.map { it.id }, last.map { it.id }, "流式终态与一次性解析的块列表不一致")
        assertEquals(direct.map { it.blockContent }, last.map { it.blockContent })
    }

    @Test
    fun repeatedFlushOfSameTextDoesNotRebuildViews() {
        val state = MarkdownStreamingState()
        val first = assertNotNull(state.update(MIXED_DOC))
        // 无变化时应返回 null，diffUpdate 直接跳过，避免无意义刷新
        assertEquals(null, state.update(MIXED_DOC))
        // 强制 flush（模拟下拉刷新 / 列表 reload）必须产出完全一致的 id
        val forced = assertNotNull(state.update(MIXED_DOC, force = true))
        assertEquals(first.map { it.id }, forced.map { it.id })

        // reset 后重新灌入同样内容，结果仍应一致
        state.reset()
        val replay = assertNotNull(state.update(MIXED_DOC, force = true))
        assertEquals(first.map { it.id }, replay.map { it.id })
    }

    @Test
    fun noBlockIsDuplicatedOrDropped() {
        val state = MarkdownStreamingState()
        val blocks = assertNotNull(state.update(MIXED_DOC, force = true))

        assertEquals(blocks.map { it.id }.toSet().size, blocks.size, "同一帧出现重复块 id")
        blocks.forEachIndexed { index, block ->
            assertEquals(index, block.blockIndex, "blockIndex 与列表位置不一致")
        }

        val joined = blocks.joinToString("\n") { it.blockContent }
        MIXED_SENTINELS.forEach { sentinel ->
            assertTrue(joined.contains(sentinel), "块内容丢失片段: $sentinel")
        }
        // 每个块内容必须是原文中连续的一段，不能错位
        blocks.forEach { block ->
            assertTrue(
                MIXED_DOC.contains(block.blockContent),
                "块内容不是原文子串，可能发生错位: ${block.blockContent}",
            )
        }
    }

    @Test
    fun halfOpenFenceAndMathDoNotBreakLaterBlocks() {
        val state = MarkdownStreamingState()

        // 1) 代码围栏未闭合
        assertNotNull(state.update("# 标题\n\n```kotlin\nval x = 1"))
        assertTrue(state.parseResult!!.content.contains("val x = 1"))

        // 2) 块级公式未闭合
        assertNotNull(state.update("# 标题\n\n```kotlin\nval x = 1\n```\n\n\$\$x^2"))
        assertTrue(state.parseResult!!.content.contains("```"))

        // 3) 全部闭合后，前面的块必须原样保留
        val final = assertNotNull(state.update(MIXED_DOC, force = true))
        val joined = final.joinToString("\n") { it.blockContent }
        MIXED_SENTINELS.forEach { sentinel ->
            assertTrue(joined.contains(sentinel), "闭合后丢失片段: $sentinel")
        }
    }

    @Test
    fun oneCharChunksConverge() {
        val state = MarkdownStreamingState()
        MIXED_DOC.forEachIndexed { index, _ ->
            state.update(MIXED_DOC.substring(0, index + 1))
        }
        val streamed = assertNotNull(state.update(MIXED_DOC, force = true))

        val oneShot = assertNotNull(MarkdownStreamingState().update(MIXED_DOC, force = true))
        assertEquals(oneShot.map { it.id }, streamed.map { it.id })
    }

    @Test
    fun multiLineDisplayMathIsRecognized() {
        val state = MarkdownStreamingState()
        assertNotNull(state.update(MIXED_DOC, force = true))
        val types = collectTypes(state.blockNodes)
        assertTrue(types.contains("BLOCK_MATH"), "多行 \$\$ 公式未被识别: $types")

        val mathBlock = findFirst(state.blockNodes, "BLOCK_MATH")
        assertNotNull(mathBlock)
        val latex = state.parseResult!!.content.substring(mathBlock.startOffset, mathBlock.endOffset)
        assertTrue(latex.contains("bmatrix"), "块级公式内容异常: $latex")
        assertFalse(latex.contains("收尾段落"), "公式吞掉了后续正文: $latex")
    }

    @Test
    fun loneDollarLineDoesNotSwallowFollowingParagraph() {
        val state = MarkdownStreamingState()
        val src = "价格 \$\$ 待定\n\n收尾段落。"
        assertNotNull(state.update(src, force = true))
        val joined = state.blockNodes.joinToString("\n") {
            state.parseResult!!.content.substring(it.startOffset, it.endOffset)
        }
        assertTrue(joined.contains("收尾段落"), "正文被公式吞掉: $joined")
    }

    private fun collectTypes(nodes: List<org.intellij.markdown.ast.ASTNode>): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(node: org.intellij.markdown.ast.ASTNode, depth: Int) {
            out.add(node.type.name)
            if (depth >= 4) return
            node.children.forEach { walk(it, depth + 1) }
        }
        nodes.forEach { walk(it, 0) }
        return out
    }

    private fun findFirst(
        nodes: List<org.intellij.markdown.ast.ASTNode>,
        typeName: String,
    ): org.intellij.markdown.ast.ASTNode? {
        fun search(node: org.intellij.markdown.ast.ASTNode): org.intellij.markdown.ast.ASTNode? {
            if (node.type.name == typeName) return node
            node.children.forEach { search(it)?.let { return it } }
            return null
        }
        nodes.forEach { search(it)?.let { return it } }
        return null
    }

    /**
     * 按 chunk 灌入 [state]，每帧回调一次当前块列表（模拟 diffUpdate 前的快照）。
     */
    private fun feedChunks(
        state: MarkdownStreamingState,
        text: String,
        chunkSize: Int = 12,
        onFrame: (List<MarkdownBlock>) -> Unit,
    ) {
        var index = 0
        while (index < text.length) {
            val end = (index + chunkSize).coerceAtMost(text.length)
            val prefix = text.substring(0, end)
            state.update(prefix)?.let { onFrame(it) }
            index = end
        }
        state.update(text, force = true)?.let { onFrame(it) }
    }

    companion object {
        /** 渲染器可处理的块级节点类型全集（与 MarkdownRenderer 的 when 分支对齐） */
        private val RENDERABLE_BLOCK_TYPES = buildSet {
            add(MarkdownTokenTypes.TEXT)
            add(MarkdownElementTypes.CODE_FENCE)
            add(MarkdownElementTypes.CODE_BLOCK)
            add(MarkdownElementTypes.ATX_1)
            add(MarkdownElementTypes.ATX_2)
            add(MarkdownElementTypes.ATX_3)
            add(MarkdownElementTypes.ATX_4)
            add(MarkdownElementTypes.ATX_5)
            add(MarkdownElementTypes.ATX_6)
            add(MarkdownElementTypes.SETEXT_1)
            add(MarkdownElementTypes.SETEXT_2)
            add(MarkdownElementTypes.BLOCK_QUOTE)
            add(MarkdownElementTypes.PARAGRAPH)
            add(MarkdownElementTypes.ORDERED_LIST)
            add(MarkdownElementTypes.UNORDERED_LIST)
            add(MarkdownElementTypes.IMAGE)
            add(MarkdownTokenTypes.HORIZONTAL_RULE)
            add(GFMElementTypes.TABLE)
            add(GFMElementTypes.BLOCK_MATH)
        }

        private val MIXED_SENTINELS = listOf(
            "# 混排稳定性",
            "行内公式",
            "val answer = 42",
            "bmatrix",
            "| 列 A |",
            "- 列表项",
        )

        private val MIXED_DOC = """
            # 混排稳定性

            这是一段含行内公式 ${'$'}E=mc^2${'$'} 的正文，用来验证混排刷新。

            - 列表项 一
            - 列表项 二

            ```kotlin
            val answer = 42
            println(answer)
            ```

            $$
            \begin{bmatrix}1 & 2 \\ 3 & 4\end{bmatrix}
            $$

            | 列 A | 列 B |
            | --- | --- |
            | 1 | 2 |

            收尾段落。
        """.trimIndent()
    }
}
