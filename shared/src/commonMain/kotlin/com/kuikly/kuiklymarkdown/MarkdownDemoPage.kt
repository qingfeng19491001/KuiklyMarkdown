package com.kuikly.kuiklymarkdown

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.*
import com.tencent.kuikly.core.views.Scroller
import com.kuikly.kuiklymarkdown.base.BasePager
import com.tencent.kuiklybase.KuiklyMarkdown

@Page("markdown_demo", supportInLocal = true)
internal class MarkdownDemoPage : BasePager() {

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vbind({ ctx.isNightMode() }) {
                View {
                    attr {
                        flex(1f)
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF121212) else Color.WHITE)
                    }

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
                    Text {
                        attr {
                            flex(1f)
                            text("Markdown 渲染 Demo")
                            fontSize(17f)
                            fontWeightBold()
                            textAlignCenter()
                            color(if (ctx.isNightMode()) Color(0xFFE8EAED) else Color(0xFF333333))
                        }
                    }
                    View {
                        attr {
                            size(44f, 44f)
                        }
                    }
                }
                View {
                    attr {
                        height(0.5f)
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF3D3D3D) else Color(0xFFE0E0E0))
                    }
                }
            }

            Scroller {
                attr {
                    flex(1f)
                }
                View {
                    attr {
                        padding(16f)
                    }
                    KuiklyMarkdown(
                        content = SAMPLE_MARKDOWN,
                        config = ctx.markdownConfig(),
                    )
                }
            }
                }
            }
        }
    }


    companion object {
        /**
         * 示例 Markdown 文本，覆盖常见的 Markdown 语法元素
         */
        private const val SAMPLE_MARKDOWN = """# KuiklyMarkdown 渲染演示

## 1. 文本样式

这是一段普通文本。支持 **粗体**、*斜体*、~~删除线~~ 和 `行内代码` 等样式。

也可以组合使用：***粗斜体*** 文本。

## 2. 标题层级

### 三级标题
#### 四级标题
##### 五级标题
###### 六级标题

## 3. 链接

这是一个 [链接示例](https://kuikly.tencent.com)，点击可跳转。

## 4. 图片

![示例图片](https://vfiles.gtimg.cn/wuji_dashboard/wupload/xy/starter/62394e19.png)

## 5. 代码块（语法高亮）

```kotlin
fun main() {
    println("Hello, KuiklyMarkdown!")
    val config = MarkdownConfig.Default
    println("渲染配置: ${'$'}config")
}
```

```python
def fibonacci(n: int) -> list[int]:
    result = [0, 1]
    for i in range(2, n):
        result.append(result[-1] + result[-2])
    return result

print(fibonacci(10))
```

```javascript
function greet(name) {
    const message = "Hello, " + name + "!";
    console.log(message);
    return message;
}
greet("KuiklyMarkdown");
```

## 6. 引用

> 这是一段引用文本。
> Kuikly 是基于 Kotlin Multiplatform 构建的跨端开发框架。
>
> > 支持嵌套引用。

## 7. 无序列表

- 第一项
- 第二项
  - 嵌套子项 A
  - 嵌套子项 B
- 第三项

## 8. 有序列表

1. 第一步：创建项目
2. 第二步：添加依赖
3. 第三步：编写代码
4. 第四步：运行测试

## 9. 分割线

---

## 10. 表格

| 功能 | 状态 | 说明 |
|------|:----:|------|
| 标题 | ✅ | H1 ~ H6 |
| 粗体/斜体 | ✅ | 支持组合 |
| 代码块 | ✅ | 支持语法高亮 |
| 表格 | ✅ | 支持对齐 |
| 列表 | ✅ | 有序/无序 |

## 11. 复选框

- [x] 已完成的任务
- [ ] 待完成的任务
- [x] 另一个已完成的任务

## 12. 数学公式

行内公式 ${'$'}E=mc^2${'$'} 与周围文本混排。点击或长按公式可复制 LaTeX 源。

分式与希腊字母：${'$'}\alpha = \frac{v}{c}${'$'}。

当一段说明文字足够长、在公式出现之前就已经折行时，行内公式应当紧跟在上一行文字之后，例如 ${'$'}a^2+b^2=c^2${'$'}，后面的标点和文字也要继续接在同一行里排下去，而不是整段掉到下一行。

质能方程 ${'$'}E=mc^2${'$'}，其中 ${'$'}c${'$'} 为光速，**加粗**与[链接](https://kuikly.tds.qq.com)也能和公式在同一段落里自然折行。

基线对齐与高公式：xyz ${'$'}x_1+y^2${'$'} xyz，矩阵 ${'$'}\begin{pmatrix} a & b \\ c & d \end{pmatrix}${'$'} 和求和 ${'$'}\sum_{i=1}^{n} \frac{1}{i}${'$'} 不应与上下行文字重叠，段落设置了固定行高也一样。

块级公式独立排版：

${'$'}${'$'}x^2+y^2=z^2${'$'}${'$'}

希腊字母、分式与根号：

${'$'}${'$'}\alpha + \frac{1}{2} = \sqrt{x_i^2}${'$'}${'$'}

求和与积分：

${'$'}${'$'}\sum_{i=1}^{n} \frac{1}{i^2} = \frac{\pi^2}{6}${'$'}${'$'}

${'$'}${'$'}\int_{0}^{1} x^{2}\,dx = \frac{1}{3}${'$'}${'$'}

括号与二项式：

${'$'}${'$'}\left(\frac{a}{b}\right)^{n} = \binom{n}{k}${'$'}${'$'}

分段函数：

${'$'}${'$'}\begin{cases} x & x \ge 0 \\ -x & x < 0 \end{cases}${'$'}${'$'}

矩阵：

${'$'}${'$'}\begin{bmatrix} 1 & 2 & 3 \\ 4 & 5 & 6 \end{bmatrix}${'$'}${'$'}

也支持 `\[ ... \]` 块级定界符。

未闭合的 ${'$'}E=mc 会降级为普通文本。

## 13. 多行公式与混排（滚动/刷新稳定性手测）

价格是 ${'$'}5 美元，变量 ${'$'}x${'$'} 是未知数 —— 货币符号不能吞掉正文。

${'$'}${'$'}
\begin{bmatrix}
1 & 2 \\
3 & 4
\end{bmatrix}
\cdot
\begin{pmatrix} a & b \\ c & d \end{pmatrix}
=
\begin{bmatrix} a & b \\ c & d \end{bmatrix}
${'$'}${'$'}

紧邻的代码块里同样有美元符号，长按/点击右上角「复制」可复制代码：

```swift
let price = 5        // ${'$'}5
let latex = "x^2"
print(price)
```

| 场景 | 期望 |
|------|------|
| 多行 $$ 公式 | 独立居中排版 |
| 代码块中的 $ | 不解析为公式 |
| 货币 $5 | 保持原文本 |

> 引用里的行内公式 ${'$'}a^2+b^2=c^2${'$'} 同样正常。

---

*以上为 KuiklyMarkdown 组件的渲染能力演示。*
"""
    }
}
