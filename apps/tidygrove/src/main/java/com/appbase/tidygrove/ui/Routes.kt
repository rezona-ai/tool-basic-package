/**
 * [INPUT]: 依赖 ui 下的扫描 / 类别 / 完成 / 文件 / 设备信息页，依赖 :core:ui 的 FolderAccessScreen / SettingsScreen
 * [OUTPUT]: 对外提供 Routes、NavGraphBuilder.featureRoutes、settingsRoute、openTab
 * [POS]: 两种导航变体（tabs / hub）共用的路由：功能子页面在这里注册，AppNav 只决定顶层怎么组织（底部标签或单页入口）。
 *        子页面返回直接 popBackStack，不加返回插屏；插屏只在扫描结果、清理完成之后（见 Placements）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.appbase.core.ui.FolderAccessScreen
import com.appbase.core.ui.ReminderLabels
import com.appbase.core.ui.SettingsScreen
import com.appbase.core.ui.theme.Bg
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.CleanResult
import com.appbase.tidygrove.data.Copy
import com.appbase.tidygrove.ui.browser.BrowserScreen
import com.appbase.tidygrove.ui.clean.CategoryScreen
import com.appbase.tidygrove.ui.done.DoneScreen
import com.appbase.tidygrove.ui.info.DeviceInfoScreen
import com.appbase.tidygrove.ui.scan.ScanScreen

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val FOLDERS = "folders"
    const val SCAN = "scan"
    const val KIND = "kind/{kind}"
    const val BROWSER = "browser?path={path}"
    const val DEVICE = "device"
    const val DONE = "done/{kinds}/{freed}/{count}"

    fun kind(kind: FileKind) = "kind/${kind.name}"
    fun browser(path: String) = "browser?path=${Uri.encode(path)}"
    fun done(kinds: Set<FileKind>, result: CleanResult) = "done/${kinds.joinToString(",") { it.name }}/${result.freed}/${result.count}"
}

/** 扫描、类别、完成、子目录、设备信息、文件夹授权：两种导航共用 */
fun NavGraphBuilder.featureRoutes(nav: NavHostController) {
    composable(Routes.SCAN) {
        ScanScreen(
            onBack = { nav.popBackStack() },
            onOpenKind = { nav.navigate(Routes.kind(it)) },
            onCleaned = { kinds, result -> nav.navigate(Routes.done(kinds, result)) { popUpTo(Routes.SCAN) { inclusive = true } } },
        )
    }
    composable(Routes.KIND, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
        val kind = FileKind.valueOf(entry.arguments!!.getString("kind")!!)
        CategoryScreen(
            kind = kind,
            onBack = { nav.popBackStack() },
            onCleaned = { nav.navigate(Routes.done(setOf(kind), it)) { popUpTo(Routes.KIND) { inclusive = true } } },
        )
    }
    composable(
        Routes.DONE,
        arguments = listOf(
            navArgument("kinds") { type = NavType.StringType },
            navArgument("freed") { type = NavType.LongType },
            navArgument("count") { type = NavType.IntType },
        ),
    ) { entry ->
        val args = entry.arguments!!
        DoneScreen(
            cleaned = args.getString("kinds")!!.split(',').filter { it.isNotEmpty() }.map(FileKind::valueOf).toSet(),
            freed = args.getLong("freed"),
            count = args.getInt("count"),
            onHome = { nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false) },
            onOpen = { nav.navigate(Routes.kind(it)) },
        )
    }
    composable(Routes.BROWSER, arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })) { entry ->
        Screen {
            BrowserScreen(
                path = entry.arguments?.getString("path").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenDir = { nav.navigate(Routes.browser(it)) },
                onManageFolders = { nav.navigate(Routes.FOLDERS) },
            )
        }
    }
    composable(Routes.DEVICE) { DeviceInfoScreen(onBack = { nav.popBackStack() }) }
    composable(Routes.FOLDERS) { Screen { FolderAccessScreen(onBack = { nav.popBackStack() }) } }
}

/** 设置页（:core:ui 的通用设置页）：文件夹授权入口 + 提醒开关；作为子页面时用系统返回键返回 */
fun NavGraphBuilder.settingsRoute(nav: NavHostController) {
    composable(Routes.SETTINGS) {
        Screen {
            SettingsScreen(
                onFolders = { nav.navigate(Routes.FOLDERS) },
                reminder = ReminderLabels(Copy.reminderTitle, Copy.reminderSubtitle),
            )
        }
    }
}

@Composable
fun Screen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) { content() }
}

/** 切换到底部导航的某一页，各页保留自己的返回栈 */
fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
