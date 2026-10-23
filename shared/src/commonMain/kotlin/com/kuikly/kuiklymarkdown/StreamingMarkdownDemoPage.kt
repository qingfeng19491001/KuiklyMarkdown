package com.kuikly.kuiklymarkdown

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.kuikly.kuiklymarkdown.base.BasePager
import com.kuikly.kuiklymarkdown.base.setTimeout
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState

@Page("streaming_markdown_demo", supportInLocal = true)
internal class StreamingMarkdownDemoPage : BasePager() {

    private val streamingState = MarkdownStreamingState()
    private var blockList by observableList<MarkdownBlock>()
    private var charIndex = 0
    private var pendingText = ""
    private var isStreaming by observable(false)

    /** 流式输出时是否自动贴底；用户上滑离开底部后暂停 */
    private var autoFollow by observable(true)

    private var scrollerRef: ViewRef<ScrollerView<*, *>>? = null
    private var contentHeight = 0f
    private var viewportHeight = 0f
    private var offsetY = 0f
    private var tickCount = 0

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vbind({ ctx.isNightMode() }) {
                View {
                    attr {
                        flex(1f)
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF121212) else Color.WHITE)
                    }

            // 导航栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(if (ctx.isNightMode()) Color(0xFF121212) else Color.WHITE)
                }
                View {
                    attr {
                        height(44f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    // 返回按钮
                    View {
                        attr {
                            size(44f, 44f)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("‹")
                                fontSize(28f)
                                color(if (ctx.isNightMode()) Color(0xFFE8EAED) else Color(0xFF333333))
                            }
                        }
                        event {
                            click {
                                ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                    }
                    // 标题
                    Text {
                        attr {
                            flex(1f)
                            text("流式 Markdown 渲染 Demo")
                            fontSize(17f)
                            fontWeightBold()
                            textAlignCenter()
                            color(if (ctx.isNightMode()) Color(0xFFE8EAED) else Color(0xFF333333))
                        }
                    }
                    // 开始/重置按钮
                    View {
                        attr {
                            marginRight(12f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            height(32f)
                            allCenter()
                            backgroundColor(Color(0xFF1A73E8))
                            borderRadius(16f)
                        }
                        Text {
                            attr {
                                text(if (ctx.isStreaming) "输出中..." else "开始")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                        event {
                            click {
                                if (!ctx.isStreaming) {
                                    ctx.startStreaming()
                                }
                            }
                        }
                    }
                }
                // 分割线
                View {
                    attr {
                        height(0.5f)
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF3D3D3D) else Color(0xFFE0E0E0))
                    }
                }
            }

            // 内容区域 - 可滚动，流式输出时默认跟随底部
            View {
                attr {
                    flex(1f)
                }
            Scroller {
                ref {
                    ctx.scrollerRef = it
                }
                attr {
                    flex(1f)
                    absolutePositionAllZero()
                }
                event {
                    scroll { params ->
                        ctx.onScrollerMoved(params)
                    }
                    contentSizeChanged { _, height ->
                        ctx.contentHeight = height
                        ctx.followBottom()
                    }
                }
                View {
                    attr {
                        padding(16f)
                    }

                    vbind({ ctx.isNightMode() }) {
                        View {
                            vfor({ ctx.blockList }) { block ->
                                View {
                                    KuiklyStreamingMarkdown(
                                        state = ctx.streamingState,
                                        block = block,
                                        config = ctx.markdownConfig(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            vif({ !ctx.autoFollow }) {
                View {
                    attr {
                        absolutePosition(bottom = 16f, right = 16f)
                        height(32f)
                        paddingLeft(12f)
                        paddingRight(12f)
                        allCenter()
                        backgroundColor(Color(0xFF1A73E8))
                        borderRadius(16f)
                    }
                    Text {
                        attr {
                            text("回到底部")
                            fontSize(13f)
                            color(Color.WHITE)
                        }
                    }
                    event {
                        click {
                            ctx.autoFollow = true
                            ctx.followBottom()
                        }
                    }
                }
            }
            }
                }
            }
        }
    }

    private fun onScrollerMoved(params: ScrollParams) {
        contentHeight = params.contentHeight
        viewportHeight = params.viewHeight
        offsetY = params.offsetY
        val maxOffset = (params.contentHeight - params.viewHeight).coerceAtLeast(0f)
        val nearBottom = maxOffset - params.offsetY <= FOLLOW_SLOP
        val next = if (params.isDragging) nearBottom else autoFollow || nearBottom
        if (next != autoFollow) {
            autoFollow = next
        }
    }

    private fun followBottom() {
        if (!autoFollow) return
        val viewport = if (viewportHeight > 1f) {
            viewportHeight
        } else {
            (pagerData.pageViewHeight - pagerData.statusBarHeight - 44.5f).coerceAtLeast(1f)
        }
        val target = (contentHeight - viewport).coerceAtLeast(0f)
        if (target - offsetY <= 1f) return
        scrollerRef?.view?.setContentOffset(0f, target, false)
        offsetY = target
    }

    private fun startStreaming() {
        streamingState.reset()
        blockList.clear()
        charIndex = 0
        pendingText = ""
        tickCount = 0
        autoFollow = true
        offsetY = 0f
        isStreaming = true
        scrollerRef?.view?.setContentOffset(0f, 0f, false)
        scheduleNextTick()
    }

    private fun scheduleNextTick() {
        setTimeout(TICK_INTERVAL_MS) {
            if (charIndex < STREAMING_SAMPLE.length) {
                val end = minOf(charIndex + CHARS_PER_TICK, STREAMING_SAMPLE.length)
                pendingText = STREAMING_SAMPLE.substring(0, end)
                charIndex = end
                tickCount++

                val flushEveryN = (FLUSH_INTERVAL_MS / TICK_INTERVAL_MS).coerceAtLeast(1)
                if (tickCount % flushEveryN == 0) {
                    flushUpdate()
                }
                scheduleNextTick()
            } else {
                flushUpdate(force = true)
                isStreaming = false
            }
        }
    }

    private fun flushUpdate(force: Boolean = false) {
        val newBlocks = if (force) {
            streamingState.update(pendingText, force = true)
        } else {
            streamingState.update(pendingText)
        } ?: return

        blockList.diffUpdate(newBlocks) { old, new -> old.id == new.id }
        followBottom()
    }


    companion object {
        private const val FOLLOW_SLOP = 72f
        private const val CHARS_PER_TICK = 3
        private const val TICK_INTERVAL_MS = 30
        private const val FLUSH_INTERVAL_MS = 100

        /**
         * 模拟 AI 输出的 Markdown 文本
         */
        private const val STREAMING_SAMPLE = """# 什么是 Kuikly？

Kuikly 是基于 **Kotlin Multiplatform** 构建的跨端开发框架，利用 KMP 的逻辑跨平台能力，实现了一套高性能的跨平台 UI 渲染方案。

## 核心特点

1. **轻量级** — 包增量仅 0.3MB（Android AAR）
2. **高性能** — 复用平台原生 UI 组件
3. **可动态化** — 支持动态下发更新
4. **跨平台** — 覆盖 Android、iOS、鸿蒙、H5

## 代码示例

```kotlin
@Page("hello_page")
class HelloPage : BasePager() {
    private var count by observable(0)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr { allCenter() }
                Text {
                    attr {
                        text("点击次数: ${'$'}{ctx.count}")
                        fontSize(20f)
                    }
                }
                event {
                    click { ctx.count++ }
                }
            }
        }
    }
}
```

## 布局系统

Kuikly 使用 **FlexBox** 作为跨平台布局引擎：

| 属性 | 说明 | 示例 |
|------|------|------|
| flexDirection | 主轴方向 | `flexDirectionRow()` |
| justifyContent | 主轴对齐 | `justifyContentCenter()` |
| alignItems | 交叉轴对齐 | `alignItemsCenter()` |
| flex | 弹性比例 | `flex(1f)` |

> Kuikly 的 FlexBox 实现保证了 Android、iOS、鸿蒙三端的一致性。

## 公式示例

质量与能量的关系是行内公式 ${'$'}E=mc^2${'$'}。块级公式会单独成段：

${'$'}${'$'}x^2+y^2=z^2${'$'}${'$'}

分式、上下标与希腊字母也可以流式到达：${'$'}\alpha = \frac{v}{c}${'$'}。

未闭合片段 ${'$'}E=mc 在后续 chunk 补全前按普通文本展示。

## 混排稳定性

价格是 ${'$'}5 美元，变量 ${'$'}x${'$'} 是未知数 —— 货币符号不能吞掉正文。

${'$'}${'$'}
\begin{bmatrix}
1 & 2 \\
3 & 4
\end{bmatrix}
${'$'}${'$'}

紧邻的代码块里也有美元符号：

```swift
let price = 5        // ${'$'}5
let latex = "x^2"
```

| 场景 | 期望 |
|------|------|
| 多行 $$ 公式 | 独立居中排版 |
| 代码块中的 $ | 不解析为公式 |
| 货币 $5 | 保持原文本 |

> 引用里的行内公式 ${'$'}a^2+b^2=c^2${'$'} 同样正常。

## 总结

- [x] 跨平台开发
- [x] 原生性能
- [ ] 更多平台支持（持续迭代中）

---

*以上内容由 AI 流式生成，使用 KuiklyStreamingMarkdown 实时渲染。*
"""
    }
}
