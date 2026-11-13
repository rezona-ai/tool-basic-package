/**
 * [INPUT]: 依赖 ui 下各页面、ui/saturn 的 SaturnDirector/SaturnBackdrop、ui/components 的 OrbitNavBar/PlainScreen/DesignDensity，
 *          依赖 core/ads 的 rememberAdBack/showInterstitialWithMask、core/ui 的 SettingsScreen/FolderAccessScreen、./Placements 的广告位
 * [OUTPUT]: 对外提供 AppNav
 * [POS]: ui 的路由中枢：最底层是常驻土星舞台（页面通过 SaturnStage 指挥镜头，切页时镜头连续推移），其上是 NavHost；
 *        启动页是文件管理首页（FILES 标签），土星页是 CLEAN 标签；土星场景页铺满全屏自己处理系统栏，常规页用 PlainScreen 铺底色；
 *        CLEAN 页的底部导航在玻璃面板里，其余标签页单独显示
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appbase.core.ads.AdPlacement
import com.appbase.core.ads.rememberAdBack
import com.appbase.core.ads.showInterstitialWithMask
import com.appbase.core.common.Reminders
import com.appbase.core.ui.FolderAccessScreen
import com.appbase.core.ui.ReminderLabels
import com.appbase.core.ui.SettingsScreen
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextPrimary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.browser.BrowserScreen
import com.orbitclean.app.ui.components.DesignDensity
import com.orbitclean.app.ui.components.NavItem
import com.orbitclean.app.ui.components.OrbitNavBar
import com.orbitclean.app.ui.components.PlainScreen
import com.orbitclean.app.ui.components.ProcessingGate
import com.orbitclean.app.ui.done.CleanDoneScreen
import com.orbitclean.app.ui.filelist.CleanResult
import com.orbitclean.app.ui.filelist.FileListScreen
import com.orbitclean.app.ui.home.HomeScreen
import com.orbitclean.app.ui.info.DeviceInfoScreen
import com.orbitclean.app.ui.more.MoreScreen
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.saturn.SaturnBackdrop
import com.orbitclean.app.ui.saturn.SaturnDirector
import com.orbitclean.app.ui.scan.JunkScanScreen
import kotlinx.coroutines.launch

private object Routes {
    /** 土星清理页（底部导航 CLEAN）：Smart Scan 与清理工具 */
    const val HOME = "home"
    /** 启动页 = 底部导航第一个标签 FILES：文件管理器首页（存储根目录 + 分类 + 清理入口）；子目录走 [BROWSER] */
    const val MANAGER = "manager"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val SCAN = "scan"
    const val FILES = "files/{kind}?intro={intro}"
    const val BROWSER = "browser?path={path}"
    const val DEVICE = "device"
    /** 文件夹授权管理（设置页、文件首页 "Add a folder" 进入） */
    const val FOLDERS = "folders"
    const val DONE = "done/{kind}/{freed}/{count}"

    /** [intro] 为 true 时先播放处理中动画并弹插屏（从扫描结果进入时已有结果，不再重复） */
    fun files(kind: FileKind, intro: Boolean = true) = "files/${kind.name}?intro=$intro"
    fun browser(path: String) = "browser?path=${Uri.encode(path)}"
    fun done(kind: FileKind, result: CleanResult) = "done/${kind.name}/${result.freed}/${result.count}"
}

/** 首页“再按一次退出”的确认时长 */
private const val EXIT_CONFIRM_MS = 2_000L

/** 设置页提醒开关的文案（提醒内容见 reminder/ReminderWorker） */
private val REMINDER_LABELS = ReminderLabels("Cleaning reminders", "Notify me when leftover files pile up or storage runs low")

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.MANAGER, "FILES", Icons.Rounded.Folder),
    Tab(Routes.HOME, "CLEAN", Icons.Rounded.CleaningServices),
    Tab(Routes.MORE, "MORE", Icons.Rounded.GridView),
    Tab(Routes.SETTINGS, "SETTINGS", Icons.Rounded.Settings),
)

@Composable
fun AppNav(openScan: Boolean = false, onScanOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val activity = LocalActivity.current
    RequestNotificationPermissionOnce()
    // 启动页（文件管理首页）返回：2 秒内连续返回两次才退到后台（不结束 App，下次打开走热启动）。
    // 防止关闭广告时多滑的一下直接把 App 滑走。文件页自己的多选/搜索返回后注册，优先处理
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute == Routes.MANAGER) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < EXIT_CONFIRM_MS) {
            lastBackAt = 0L
            activity?.moveTaskToBack(true)
        } else {
            lastBackAt = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    // 点提醒通知进入：先弹插屏（刚弹过开屏时处于冷却期，会直接跳过），再进扫描页
    LaunchedEffect(openScan) {
        if (openScan) {
            showInterstitialWithMask(activity, Placements.CLICK_NOTICE_INTERSTITIAL)
            nav.navigate(Routes.SCAN) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
            onScanOpened()
        }
    }

    val director = remember { SaturnDirector() }
    CompositionLocalProvider(LocalSaturn provides director) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            SaturnBackdrop(director, Modifier.fillMaxSize())
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (currentRoute != Routes.HOME && tabs.any { it.route == currentRoute }) {
                        DesignDensity { inset -> OrbitNavBar(navItems(currentRoute, nav), inset, standalone = true) }
                    }
                },
            ) { padding ->
                AppRoutes(nav, padding, activity)
            }
        }
    }
}

@Composable
private fun AppRoutes(nav: NavHostController, padding: androidx.compose.foundation.layout.PaddingValues, activity: android.app.Activity?) {
        NavHost(nav, startDestination = Routes.MANAGER, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { nav.navigate(Routes.SCAN) },
                    onTool = { nav.navigate(Routes.files(it)) },
                    onFiles = { nav.openTab(Routes.MANAGER) },
                    onDeviceInfo = { nav.navigate(Routes.DEVICE) },
                    navBar = { inset -> OrbitNavBar(navItems(Routes.HOME, nav), inset, standalone = false) },
                )
            }
            composable(Routes.MANAGER) {
                PlainScreen(bottomInset = false) {
                    BrowserScreen(
                        path = "",
                        onBack = null,
                        onOpenDir = { nav.navigate(Routes.browser(it)) },
                        onCategory = { nav.navigate(Routes.files(it)) },
                        onCleanup = { nav.openTab(Routes.HOME) },
                        onManageFolders = { nav.navigate(Routes.FOLDERS) },
                    )
                }
            }
            composable(Routes.MORE) {
                PlainScreen(bottomInset = false) {
                    MoreScreen(
                        onBrowse = { nav.openTab(Routes.MANAGER) },
                        onCategory = { nav.navigate(Routes.files(it)) },
                        onDeviceInfo = { nav.navigate(Routes.DEVICE) },
                    )
                }
            }
            composable(Routes.SETTINGS) {
                PlainScreen(bottomInset = false) {
                    SettingsScreen(onFolders = { nav.navigate(Routes.FOLDERS) }, reminder = REMINDER_LABELS)
                }
            }
            composable(Routes.FOLDERS) {
                PlainScreen(bottomInset = true) { FolderAccessScreen(onBack = { nav.popBackStack() }) }
            }
            composable(Routes.SCAN) {
                val back = rememberAdBack { nav.popBackStack() }
                JunkScanScreen(
                    onBack = back,
                    onOpen = { nav.navigate(Routes.files(it, intro = false)) },
                )
            }
            composable(
                Routes.FILES,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("intro") { type = NavType.BoolType; defaultValue = true },
                ),
            ) { entry ->
                val kind = FileKind.valueOf(entry.arguments!!.getString("kind")!!)
                val repo = App.instance.scans
                val results by repo.results.collectAsState()
                LaunchedEffect(kind) { repo.ensureFresh(kind) }
                ProcessingGate(
                    enabled = entry.arguments!!.getBoolean("intro"),
                    title = kind.title,
                    status = "Scanning ${kind.title.lowercase()}…",
                    ready = kind in results,
                    placement = Placements.TOOL_RESULT_INTERSTITIAL,
                    onBack = { nav.popBackStack() },
                ) {
                    val back = rememberAdBack { nav.popBackStack() }
                    FileListScreen(
                        kind = kind,
                        onBack = back,
                        onCleaned = { nav.navigate(Routes.done(kind, it)) },
                    )
                }
            }
            composable(
                Routes.DONE,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("freed") { type = NavType.LongType },
                    navArgument("count") { type = NavType.IntType },
                ),
            ) { entry ->
                val args = entry.arguments!!
                val scope = rememberCoroutineScope()
                val back = rememberAdBack { nav.popBackStack() }
                CleanDoneScreen(
                    kind = FileKind.valueOf(args.getString("kind")!!),
                    freed = args.getLong("freed"),
                    count = args.getInt("count"),
                    onBack = back,
                    onHome = {
                        scope.launch {
                            showInterstitialWithMask(activity, AdPlacement.BACK_INTERSTITIAL)
                            // 从清理页进来回清理页；从文件首页/更多页的分类进来时栈里没有清理页，回启动页
                            if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                                nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                            }
                        }
                    },
                    onOpen = { nav.navigate(Routes.files(it)) },
                )
            }
            composable(
                Routes.BROWSER,
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                PlainScreen(bottomInset = true) {
                    BrowserScreen(
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenDir = { nav.navigate(Routes.browser(it)) },
                    )
                }
            }
            composable(Routes.DEVICE) {
                ProcessingGate(
                    enabled = true,
                    title = "Device Info",
                    status = "Checking device status…",
                    ready = true,
                    placement = Placements.TOOL_RESULT_INTERSTITIAL,
                    onBack = { nav.popBackStack() },
                ) {
                    val back = rememberAdBack { nav.popBackStack() }
                    PlainScreen(bottomInset = true) { DeviceInfoScreen(onBack = back) }
                }
            }
        }
}

/** Android 13+ 首次进入主界面时申请一次通知权限；拒绝后不再弹，设置页开关可再次引导 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!Reminders.isPermissionAsked(context) && !Reminders.notificationsAllowed(context)) {
            Reminders.setPermissionAsked(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun navItems(current: String?, nav: NavHostController) = tabs.map { tab ->
    NavItem(tab.label, tab.icon, tab.route == current) { nav.openTab(tab.route) }
}

/** 切换到底部导航的某一页，各页保留自己的返回栈 */
private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

