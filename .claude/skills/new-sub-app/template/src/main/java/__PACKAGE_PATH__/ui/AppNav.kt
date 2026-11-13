/**
 * [INPUT]: 依赖 ./home 等功能页面，依赖 :core:ui 的 SettingsScreen，依赖 :core:ads 的 rememberAdBack
 * [OUTPUT]: 对外提供 AppNav
 * [POS]: ui 的路由中枢：底部导航（首页 + 设置），功能子页面从首页进入；子页面返回走返回插屏
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appbase.core.ui.SettingsScreen
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import {{CODE_PACKAGE}}.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun AppNav(openedFromReminder: Boolean = false, onReminderHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    LaunchedEffect(openedFromReminder) {
        // 点击提醒通知进入：跳到提醒对应的页面后清除标记
        if (openedFromReminder) onReminderHandled()
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
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.SETTINGS) {
                Box(Modifier.fillMaxSize().background(Bg)) { SettingsScreen() }
            }
            // 功能子页面在这里注册；返回用 rememberAdBack { nav.popBackStack() }
        }
    }
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
