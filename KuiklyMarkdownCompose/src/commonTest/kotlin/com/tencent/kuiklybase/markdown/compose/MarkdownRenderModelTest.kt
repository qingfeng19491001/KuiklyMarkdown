package com.tencent.kuiklybase.markdown.compose

import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.math.visibleMathText
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownRenderModelTest {
    @Test
    fun buildsGfmBlocksAndInlineStyles() {
        val markdown =
            """
            # Heading

            Plain **bold** *italic* ~~deleted~~ and [link](https://example.com).

            ```kotlin
            val answer = 42
            ```

            - first
              - nested

            3. third
            4. fourth

            > quoted **text**

            ![logo](https://example.com/logo.png)

            ---

            | Name | Value |
            | --- | --- |
            | A | **B** |
            """.trimIndent()

        val blocks =
            buildMarkdownRenderModel(
                content = markdown,
                config = MarkdownConfig.Default,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )

        val heading = assertIs<MarkdownRenderBlock.RichText>(blocks.first())
        assertEquals("Heading", heading.segments.joinToString("") { it.text })

        val paragraph = blocks.filterIsInstance<MarkdownRenderBlock.RichText>()[1]
        assertTrue(paragraph.segments.any { it.text == "bold" && it.bold })
        assertTrue(paragraph.segments.any { it.text == "italic" && it.italic })
        assertTrue(paragraph.segments.any { it.text == "deleted" && it.strikethrough })
        assertTrue(paragraph.segments.any { it.text == "link" && it.linkUrl == "https://example.com" })

        val code = assertNotNull(blocks.filterIsInstance<MarkdownRenderBlock.Code>().firstOrNull())
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val answer = 42"))

        val lists = blocks.filterIsInstance<MarkdownRenderBlock.ListBlock>()
        assertTrue(lists.size >= 2)
        assertEquals(
            "• ",
            lists
                .first()
                .items
                .first()
                .marker,
        )
        assertTrue(
            lists
                .first()
                .items
                .first()
                .children
                .any { it is MarkdownRenderBlock.ListBlock },
        )
        assertEquals("3. ", lists[1].items.first().marker)

        assertTrue(blocks.any { it is MarkdownRenderBlock.Quote })
        assertTrue(blocks.any { it is MarkdownRenderBlock.Divider })

        val image = assertNotNull(blocks.filterIsInstance<MarkdownRenderBlock.Image>().firstOrNull())
        assertEquals("https://example.com/logo.png", image.url)

        val table = assertNotNull(blocks.filterIsInstance<MarkdownRenderBlock.Table>().firstOrNull())
        assertEquals(2, table.header.size)
        assertEquals(1, table.rows.size)
        assertTrue(table.rows.single()[1].any { it.text == "B" && it.bold })
    }

    @Test
    fun appliesConfigAndReferenceLookup() {
        val config =
            MarkdownConfig(
                imageUrlTransformer = { "proxy:$it" },
                orderedListBullet = { index, number, _ -> "(${number + index}) " },
                unorderedListBullet = { _, depth -> "u$depth " },
            )
        val markdown =
            """
            [reference][docs]

            [docs]: https://example.com/docs

            5. item

            ![image](asset.png)
            """.trimIndent()

        val blocks =
            buildMarkdownRenderModel(
                content = markdown,
                config = config,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )

        val reference = blocks.filterIsInstance<MarkdownRenderBlock.RichText>().first()
        assertTrue(reference.segments.any { it.text == "reference" && it.linkUrl == "https://example.com/docs" })
        val ordered = blocks.filterIsInstance<MarkdownRenderBlock.ListBlock>().single()
        assertEquals("(5) ", ordered.items.single().marker)
        val image = blocks.filterIsInstance<MarkdownRenderBlock.Image>().single()
        assertEquals("proxy:asset.png", image.url)
    }

    @Test
    fun rendersInlineAndDisplayMath() {
        val blocks =
            buildMarkdownRenderModel(
                content = "Energy is \$E=mc^2\$ and\n\n\$\$x^2+y^2=z^2\$\$",
                config = MarkdownConfig.Default,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )

        val inline = blocks.filterIsInstance<MarkdownRenderBlock.RichText>()
            .flatMap { it.segments }
            .first { it.mathLatex != null }
        assertEquals("E=mc^2", inline.mathLatex)
        assertEquals("E=mc²", inline.text)
        assertTrue(!inline.mathDisplay)

        val display = assertNotNull(blocks.filterIsInstance<MarkdownRenderBlock.Math>().firstOrNull())
        assertEquals("x^2+y^2=z^2", display.latex)
        assertEquals("x²+y²=z²", visibleMathText(display.latex, display = true))
    }

    @Test
    fun layoutsGreekAndFractionInInlineMath() {
        val blocks =
            buildMarkdownRenderModel(
                content = "See \$\\alpha = \\frac{1}{2}\$",
                config = MarkdownConfig.Default,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )
        val inline = blocks.filterIsInstance<MarkdownRenderBlock.RichText>()
            .flatMap { it.segments }
            .first { it.mathLatex != null }
        assertEquals("\\alpha = \\frac{1}{2}", inline.mathLatex)
        assertEquals("α=(1/2)", inline.text)
    }

    @Test
    fun unclosedMathStaysTextAndDoesNotCreateMathBlock() {
        val blocks =
            buildMarkdownRenderModel(
                content = "open \$E=mc",
                config = MarkdownConfig.Default,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )
        assertTrue(blocks.filterIsInstance<MarkdownRenderBlock.Math>().isEmpty())
        val text = blocks.filterIsInstance<MarkdownRenderBlock.RichText>()
            .flatMap { it.segments }
            .joinToString("") { it.text }
        assertTrue(text.contains("\$E=mc"))
        assertTrue(blocks.filterIsInstance<MarkdownRenderBlock.RichText>().none { it.segments.any { seg -> seg.mathLatex != null } })
    }

    @Test
    fun mixedCodeFenceAndMath() {
        val markdown =
            """
            ```kotlin
            val answer = 42
            ```

            Use ${'$'}E=mc^2${'$'} after the fence.
            """.trimIndent()
        val blocks =
            buildMarkdownRenderModel(
                content = markdown,
                config = MarkdownConfig.Default,
                flavour = GFMFlavourDescriptor(),
                lookupLinks = true,
            )
        val code = assertNotNull(blocks.filterIsInstance<MarkdownRenderBlock.Code>().firstOrNull())
        assertTrue(code.code.contains("val answer = 42"))
        assertTrue(
            blocks.filterIsInstance<MarkdownRenderBlock.RichText>()
                .flatMap { it.segments }
                .any { it.mathLatex == "E=mc^2" },
        )
    }
}
