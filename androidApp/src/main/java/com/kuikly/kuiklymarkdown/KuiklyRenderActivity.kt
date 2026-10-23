package com.kuikly.kuiklymarkdown

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.kuikly.kuiklymarkdown.adapter.KRColorParserAdapter
import com.kuikly.kuiklymarkdown.adapter.KRFontAdapter
import com.kuikly.kuiklymarkdown.adapter.KRImageAdapter
import com.kuikly.kuiklymarkdown.adapter.KRLogAdapter
import com.kuikly.kuiklymarkdown.adapter.KRRouterAdapter
import com.kuikly.kuiklymarkdown.adapter.KRThreadAdapter
import com.kuikly.kuiklymarkdown.adapter.KRUncaughtExceptionHandlerAdapter
import com.kuikly.kuiklymarkdown.module.KRBridgeModule
import com.kuikly.kuiklymarkdown.module.KRShareModule
import com.tencent.kuiklybase.markdown.KRMarkdownMathView
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)
    private var lastNotifiedNightMode: Boolean? = null

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "router"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
        lastNotifiedNightMode = isSystemNightMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupImmersiveMode()
        notifyKuiklyNightMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
        notifyKuiklyNightMode()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            renderViewExport(KRMarkdownMathView.VIEW_NAME, { context ->
                KRMarkdownMathView(context)
            })
        }
    }

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        param["isNightMode"] = isSystemNightMode()
        return param
    }

    private fun isSystemNightMode(): Boolean {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    private fun notifyKuiklyNightMode() {
        val night = isSystemNightMode()
        if (lastNotifiedNightMode == night) return
        lastNotifiedNightMode = night
        kuiklyRenderViewDelegator.sendEvent(
            EVENT_THEME_DID_CHANGED,
            mapOf(KEY_IS_NIGHT_MODE to night),
        )
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window?.statusBarColor = Color.TRANSPARENT
            val lightStatus = !isSystemNightMode()
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            if (lightStatus) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decorView.systemUiVisibility = flags
        }

    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        private const val KEY_IS_NIGHT_MODE = "isNightMode"
        private const val EVENT_THEME_DID_CHANGED = "themeDidChanged"

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}