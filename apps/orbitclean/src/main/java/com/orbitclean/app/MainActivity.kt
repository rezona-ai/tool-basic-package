/**
 * [INPUT]: 依赖 core/ui 的 StartupActivity（闪屏、开屏广告、遮罩）与 FolderGate（首次文件夹授权），依赖 ui 的 AppNav/SplashScreen/AppTheme
 * [OUTPUT]: 对外提供 MainActivity
 * [POS]: Orbit Clean 唯一 Activity：只提供闪屏、主题和主界面；点击提醒通知进入时直达扫描页
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app

import androidx.compose.runtime.Composable
import com.appbase.core.CoreServices
import com.appbase.core.storage.files
import com.appbase.core.ui.FolderGate
import com.appbase.core.ui.StartupActivity
import com.orbitclean.app.ui.AppNav
import com.orbitclean.app.ui.splash.SplashScreen
import com.orbitclean.app.ui.theme.AppTheme

class MainActivity : StartupActivity() {

    @Composable
    override fun Theme(content: @Composable () -> Unit) = AppTheme(content)

    @Composable
    override fun Splash() = SplashScreen()

    @Composable
    override fun Main() = FolderGate {
        AppNav(openScan = openedFromReminder, onScanOpened = ::onReminderHandled)
    }

    /** 首次授权完成前不弹热启动开屏 */
    override fun warmStartAllowed() = CoreServices.files.access.hasAny()
}
