/**
 * [INPUT]: 依赖 core/ads 的开屏广告、频控与过渡遮罩，依赖 core/common 的 Reminders（通知点击 action），依赖 ./theme 的 BrandTheme
 * [OUTPUT]: 对外提供 StartupActivity（各品牌唯一 Activity 的基类）、FolderGate
 * [POS]: core/ui 的启动流程：闪屏 + 隐私授权 + 冷启动开屏广告、回到前台的热启动开屏广告、广告遮罩、系统栏样式、提醒通知点击。
 *        品牌只提供闪屏、主题和主界面；FolderGate 是基于文件夹授权的品牌在主界面外层加的首次授权门槛
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.appbase.core.CoreServices
import com.appbase.core.ads.AdPlacement
import com.appbase.core.ads.AdPolicy
import com.appbase.core.ads.Ads
import com.appbase.core.ads.showAppOpen
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.common.Reminders
import com.appbase.core.storage.files
import com.appbase.core.ui.theme.BrandTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

abstract class StartupActivity : ComponentActivity() {
    /** 点击提醒通知进入时置为 true，品牌跳到对应页面后调用 [onReminderHandled] 清除 */
    protected var openedFromReminder by mutableStateOf(false)
        private set

    /** 冷启动闪屏 + 开屏广告结束后为 true */
    private var splashDone by mutableStateOf(false)

    /** 品牌主题（颜色已经由 BrandTheme 注册，这里套上 MaterialTheme 等） */
    @Composable
    protected abstract fun Theme(content: @Composable () -> Unit)

    /** 闪屏：授权和等开屏广告期间显示，最多约 [AdPolicy.COLD_START_WAIT_MS] */
    @Composable
    protected abstract fun Splash()

    /** 闪屏结束后的主界面 */
    @Composable
    protected abstract fun Main()

    /** 热启动开屏广告的额外条件，例如首次授权还没完成时不弹 */
    protected open fun warmStartAllowed(): Boolean = true

    protected fun onReminderHandled() {
        openedFromReminder = false
    }

    /**
     * 热启动：App 回到前台就展示开屏广告。以下情况除外：
     * 离开不到 [AdPolicy.WARM_START_MIN_BACKGROUND_MS]（瞬间切换）、我们自己跳出去的（系统设置、分享、打开文件、点击广告）、
     * 灭屏 / 锁屏导致的退后台、[warmStartAllowed] 为 false。
     */
    private val warmStartAd = object : DefaultLifecycleObserver {
        private var backgroundAt = 0L
        private var skipNext = false

        override fun onStop(owner: LifecycleOwner) {
            backgroundAt = SystemClock.elapsedRealtime()
            val screenOff = !getSystemService(PowerManager::class.java).isInteractive
            skipNext = ExternalLaunch.isRecent() || screenOff
        }

        override fun onStart(owner: LifecycleOwner) {
            if (backgroundAt == 0L || !splashDone || skipNext) return
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
            if (!warmStartAllowed()) return
            if (SystemClock.elapsedRealtime() - backgroundAt < AdPolicy.WARM_START_MIN_BACKGROUND_MS) return
            AdPolicy.startSession()
            showAppOpen(this@StartupActivity, AdPlacement.APP_OPEN_WARM, waitMs = 0) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从最近任务恢复时 intent 还是旧的，不能再次跳转
        if (savedInstanceState == null) openedFromReminder = intent.action == Reminders.ACTION_OPEN_FROM_REMINDER
        // Activity 重建（如系统配置变化、进程回收后恢复）不再走闪屏
        splashDone = savedInstanceState != null
        ProcessLifecycleOwner.get().lifecycle.addObserver(warmStartAd)
        // 系统栏图标跟随品牌主题的深浅，不跟随系统浅色模式
        val bars = if (BrandTheme.palette.isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            Theme {
                if (!splashDone) {
                    // 闪屏期间忽略返回，避免误滑直接退出 App。
                    // 开屏广告本身在广告 SDK 的 Activity 里展示，那里的返回仍由 SDK 处理（关闭广告）
                    BackHandler { }
                    Splash()
                    LaunchedEffect(Unit) {
                        showColdStartAd()
                        splashDone = true
                    }
                    return@Theme
                }
                Box {
                    Main()
                    if (Ads.maskVisible) AdMask()
                }
            }
        }
    }

    /**
     * 闪屏流程：先按需完成隐私授权（GDPR），再最多等 [AdPolicy.COLD_START_WAIT_MS] 加载开屏广告；
     * 无广告时闪屏也至少停留 [MIN_SPLASH_MS]
     */
    private suspend fun showColdStartAd() {
        val consent = CompletableDeferred<Unit>()
        Ads.provider.gatherConsent(this) { consent.complete(Unit) }
        consent.await()
        val start = SystemClock.elapsedRealtime()
        AdPolicy.startSession()
        val remainingWaitMs = (AdPolicy.COLD_START_WAIT_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0)
        val done = CompletableDeferred<Unit>()
        showAppOpen(this, AdPlacement.APP_OPEN_COLD, remainingWaitMs) { done.complete(Unit) }
        done.await()
        delay(MIN_SPLASH_MS - (SystemClock.elapsedRealtime() - start))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Reminders.ACTION_OPEN_FROM_REMINDER) openedFromReminder = true
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(warmStartAd)
        super.onDestroy()
    }

    private companion object {
        const val MIN_SPLASH_MS = 1_000L
    }
}

/**
 * 首次启动的文件夹授权门槛：先挑文件夹，点 Continue 才进入 [content]（期间可以连续添加多个）；
 * 文件夹全部移除后回到这里。回到前台时刷新授权（用户可能在系统设置里撤销）
 */
@Composable
fun FolderGate(content: @Composable () -> Unit) {
    val access = CoreServices.files.access
    val grants by access.grants.collectAsState()
    var ready by rememberSaveable { mutableStateOf(access.hasAny()) }
    LaunchedEffect(grants.isEmpty()) { if (grants.isEmpty()) ready = false }
    LifecycleResumeEffect(Unit) {
        access.refresh()
        onPauseOrDispose { }
    }
    if (ready && grants.isNotEmpty()) content() else FolderAccessScreen(onContinue = { ready = true })
}
