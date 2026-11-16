/**
 * [INPUT]: 依赖 ./Routes 的共用路由，依赖 ui/home 的 HomeScreen、ui/browser 的 BrowserScreen
 * [OUTPUT]: 对外提供 AppNav
 * [POS]: 路由中枢（变体：hub 单页入口）：没有底部导航，首页就是全部入口（右上角设置、首页里的“我的文件”卡片），
 *        其余页面都是从首页压栈进入、用返回键回来。点提醒通知进入时直达扫描页
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appbase.core.ui.theme.Bg
import {{CODE_PACKAGE}}.ui.browser.BrowserScreen
import {{CODE_PACKAGE}}.ui.components.RequestNotificationPermissionOnce
import {{CODE_PACKAGE}}.ui.home.HomeScreen

@Composable
fun AppNav(openScan: Boolean = false, onScanOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    RequestNotificationPermissionOnce()
    LaunchedEffect(openScan) {
        if (openScan) {
            nav.navigate(Routes.SCAN) { launchSingleTop = true }
            onScanOpened()
        }
    }
    Scaffold(containerColor = Bg) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { nav.navigate(Routes.SCAN) },
                    onKind = { nav.navigate(Routes.kind(it)) },
                    onDeviceInfo = { nav.navigate(Routes.DEVICE) },
                    onFiles = { nav.navigate(Routes.FILES) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.FILES) {
                Screen {
                    BrowserScreen(
                        path = "",
                        onBack = { nav.popBackStack() },
                        onOpenDir = { nav.navigate(Routes.browser(it)) },
                        onManageFolders = { nav.navigate(Routes.FOLDERS) },
                    )
                }
            }
            settingsRoute(nav)
            featureRoutes(nav)
        }
    }
}
