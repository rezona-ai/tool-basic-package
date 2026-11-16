/**
 * [INPUT]: 依赖 ./home、./create、./result 页面，依赖 data 的 HistoryStore，依赖 :core:ui 的 SettingsScreen
 * [OUTPUT]: 对外提供 AppNav、Routes
 * [POS]: ui 的路由中枢：底部导航（首页 + 设置）；生成页、结果页从首页进入。
 *        生成或识别完成时先记入历史，再以 fresh=true 打开结果页（结果页负责那一次插屏）；从历史打开时 fresh=false。
 *        子页面返回直接返回，不加返回插屏：每次核心操作只在结果出来后弹一次
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui

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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appbase.core.ui.SettingsScreen
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.qrkit.data.HistoryKind
import com.appbase.qrkit.data.HistoryStore
import com.appbase.qrkit.data.QrType
import com.appbase.qrkit.ui.create.CreateScreen
import com.appbase.qrkit.ui.home.HomeScreen
import com.appbase.qrkit.ui.result.ResultScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CREATE = "create/{type}"
    const val RESULT = "result/{id}?fresh={fresh}"

    fun create(type: QrType) = "create/${type.name}"
    fun result(id: Long, fresh: Boolean) = "result/$id?fresh=$fresh"
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
        // 本应用没有提醒；保留基类约定，收到标记直接清除
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
            composable(Routes.HOME) {
                HomeScreen(
                    onCreate = { nav.navigate(Routes.create(it)) },
                    onScanned = { raw -> nav.navigate(Routes.result(HistoryStore.add(HistoryKind.SCANNED, raw), fresh = true)) },
                    onOpen = { id -> nav.navigate(Routes.result(id, fresh = false)) },
                )
            }
            composable(Routes.SETTINGS) {
                Box(Modifier.fillMaxSize().background(Bg)) { SettingsScreen() }
            }
            composable(Routes.CREATE, arguments = listOf(navArgument("type") { type = NavType.StringType })) { entry ->
                val type = runCatching { QrType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(QrType.LINK)
                CreateScreen(
                    initialType = type,
                    onBack = { nav.popBackStack() },
                    onCreated = { raw ->
                        val id = HistoryStore.add(HistoryKind.CREATED, raw)
                        // 结果页返回直接回首页，不再回到填好的表单
                        nav.navigate(Routes.result(id, fresh = true)) { popUpTo(Routes.CREATE) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.RESULT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                ResultScreen(
                    entryId = entry.arguments?.getLong("id") ?: 0L,
                    fresh = entry.arguments?.getBoolean("fresh") ?: false,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
