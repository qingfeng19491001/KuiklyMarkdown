package com.kuikly.kuiklymarkdown.base

import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig

internal abstract class BasePager : Pager() {
    private var nightModel: Boolean? by observable(null)
    private var cachedDemoMarkdownConfig: MarkdownConfig? = null
    private var cachedDemoMarkdownNight: Boolean? = null

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        return externalModules
    }

    override fun created() {
        super.created()
        isNightMode()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
        cachedDemoMarkdownConfig = null
        cachedDemoMarkdownNight = null
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (nightModel == null) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel!!
    }

    /** Demo 页复用的 MarkdownConfig；按夜间模式缓存，避免流式列表每块重建。 */
    protected fun markdownConfig(): MarkdownConfig {
        val night = isNightMode()
        cachedDemoMarkdownConfig?.let { cached ->
            if (cachedDemoMarkdownNight == night) return cached
        }
        return MarkdownConfig(
            colors = if (night) MarkdownColors.Dark else MarkdownColors.Light,
            codeHighlightDarkTheme = night,
            typography = MarkdownTypography(paragraph = TextStyleConfig(fontSize = 16f, lineHeight = 24f)),
            onMathCopy = { latex ->
                val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                bridge.copyToPasteboard(latex)
                bridge.toast("已复制公式源")
            },
            onCodeCopy = { code, _ ->
                val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                bridge.copyToPasteboard(code)
                bridge.toast("已复制代码")
            },
            onLinkClick = { url, _ ->
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).openUrl(url)
            },
        ).also {
            cachedDemoMarkdownNight = night
            cachedDemoMarkdownConfig = it
        }
    }

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }

}
