package com.tencent.kuiklybase.markdown.compose

import com.tencent.kuiklybase.config.MarkdownConfig
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
}
