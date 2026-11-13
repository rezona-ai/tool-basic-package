package com.appbase.core.ads

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi

/**
 * 全屏广告（开屏、插屏）展示期间，让返回键 / 左右滑返回手势失效，只能点广告上的关闭按钮关闭。
 * 广告在 AdMob SDK 的 AdActivity 里展示，这里通过 ActivityLifecycleCallbacks 给它加一层拦截：
 * - Android 13+ 且启用了新版返回分发（targetSdk 36 在 Android 16 上默认启用）：注册最高优先级的 OnBackInvokedCallback；
 * - 其他情况（旧版返回分发，返回以 KEYCODE_BACK 按键送达）：包装 Window.Callback 吞掉返回键。
 * 只在 [AdProvider.fullscreenShowing] 为 true 时拦截；广告关闭后、或在 AdActivity 里打开的落地页，返回照常交给 SDK。
 *
 * 注意：这会改变 AdMob 广告页面的默认返回行为，存在政策风险。需要关闭时把 [ENABLED] 改为 false 即可。
 */
object AdBackBlocker : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AdBackBlocker"
    const val ENABLED = false
    private const val AD_ACTIVITY = "com.google.android.gms.ads.AdActivity"

    private val blocking get() = Ads.provider.fullscreenShowing

    fun install(app: Application) {
        if (ENABLED) app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.javaClass.name != AD_ACTIVITY) return
        val window = activity.window
        if (window.callback !is BackBlockingCallback) {
            window.callback = BackBlockingCallback(window.callback ?: activity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerBackCallback(activity)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerBackCallback(activity: Activity) {
        val dispatcher = activity.onBackInvokedDispatcher
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            object : OnBackInvokedCallback {
                override fun onBackInvoked() {
                    if (blocking) {
                        Log.d(TAG, "back ignored while ad is showing")
                        return
                    }
                    // 广告已不在展示（例如 AdActivity 里打开的落地页）：撤掉拦截，交还给 SDK 处理
                    dispatcher.unregisterOnBackInvokedCallback(this)
                    @Suppress("DEPRECATION")
                    activity.onBackPressed()
                }
            },
        )
    }

    /** 旧版返回分发：返回手势和返回键都以 KEYCODE_BACK 送到窗口，这里吞掉 */
    private class BackBlockingCallback(private val delegate: Window.Callback) : Window.Callback by delegate {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && blocking) {
                if (event.action == KeyEvent.ACTION_UP) Log.d(TAG, "back ignored while ad is showing")
                return true
            }
            return delegate.dispatchKeyEvent(event)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
