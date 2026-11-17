/**
 * [INPUT]: 依赖 ./Routes 的共用路由，依赖 ui/home 的 HomeScreen、ui/browser 的 BrowserScreen
 * [OUTPUT]: 对外提供 AppNav
 * [POS]: 路由中枢（变体：tabs 底部三标签）：清理首页 / 我的文件 / 设置；功能子页面从各标签进入，子页面不显示底部栏。
 *        点提醒通知进入时直达扫描页
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import {{CODE_PACKAGE}}.ui.browser.BrowserScreen
import {{CODE_PACKAGE}}.ui.components.RequestNotificationPermissionOnce
import {{CODE_PACKAGE}}.ui.home.HomeScreen
import {{CODE_PACKAGE}}.ui.motion.Motion

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Clean", Icons.Rounded.CleaningServices),
    Tab(Routes.FILES, "Files", Icons.Rounded.Folder),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun AppNav(openScan: Boolean = false, onScanOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    RequestNotificationPermissionOnce()
    LaunchedEffect(openScan) {
        if (openScan) {
            nav.navigate(Routes.SCAN) { launchSingleTop = true }
            onScanOpened()
        }
    }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = DialogBg) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                indicatorColor = Primary.copy(alpha = 0.14f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = Motion.enter,
            exitTransition = Motion.exit,
            popEnterTransition = Motion.popEnter,
            popExitTransition = Motion.popExit,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { nav.navigate(Routes.SCAN) },
                    onKind = { nav.navigate(Routes.kind(it)) },
                    onDeviceInfo = { nav.navigate(Routes.DEVICE) },
                    onFiles = { nav.openTab(Routes.FILES) },
                    onSettings = null,
                )
            }
            composable(Routes.FILES) {
                Screen {
                    BrowserScreen(
                        path = "",
                        onBack = null,
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
