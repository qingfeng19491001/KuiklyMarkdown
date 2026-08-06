package com.tencent.kuiklybase

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.DivView

internal class KuiklyMarkdownRootView : DivView()

internal fun ViewContainer<*, *>.KuiklyMarkdownRootView(init: KuiklyMarkdownRootView.() -> Unit) {
    addChild(KuiklyMarkdownRootView(), init)
}
