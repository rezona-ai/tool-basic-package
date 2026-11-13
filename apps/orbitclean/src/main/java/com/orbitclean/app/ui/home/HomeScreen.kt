/**
 * [INPUT]: 依赖 ./HomeViewModel 的 storage，依赖 ui/saturn 的 SaturnStage/SaturnShots，依赖 ui/components 的 DesignFrame 与原型积木，依赖 ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: ui/home 的首页，逐项对应原型 state = home：土星在右上（cx 250 / cy 212），外圈青色轨道 = 存储占用，星尘沿轨道缓慢环绕；
 *        左下超大百分比（首次进入从 0 滚到实际值，轨道同步长出）；玻璃面板里依次是主按钮、四个快捷入口（文件管理 + 三个清理工具）、Banner、底部导航
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.ads.AdPlacement
import com.appbase.core.common.formatSize
import com.appbase.core.storage.StorageInfo
import com.appbase.core.ui.icon
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.orbitclean.app.App
import com.orbitclean.app.BuildConfig
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.Placements
import com.orbitclean.app.ui.components.BannerSlot
import com.orbitclean.app.ui.components.DesignFrame
import com.orbitclean.app.ui.components.GlassIconButton
import com.orbitclean.app.ui.components.GlassSheet
import com.orbitclean.app.ui.components.Gutter
import com.orbitclean.app.ui.components.Kicker
import com.orbitclean.app.ui.components.OrbitGlyph
import com.orbitclean.app.ui.components.PrimaryCta
import com.orbitclean.app.ui.components.ToolTile
import com.orbitclean.app.ui.components.heroShadow
import com.orbitclean.app.ui.components.icon
import com.orbitclean.app.ui.components.reportTop
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.saturn.ParticleMode
import com.orbitclean.app.ui.saturn.SaturnShots
import com.orbitclean.app.ui.saturn.SaturnStage
import com.orbitclean.app.ui.saturn.saturnTilt
import com.orbitclean.app.ui.theme.OrbitText
import kotlin.math.roundToInt

/** 计数时长，与 SaturnScene 镜头过渡（1100ms FastOutSlowIn）一致，数字和轨道同步走完 */
private const val COUNT_MS = 1100

/** 第一个快捷入口是文件管理器，其后是三个清理工具 */
private val TOOLS = listOf(FileKind.REDUNDANT, FileKind.LARGE, FileKind.DUPLICATE)

/**
 * @param navBar 底部导航，放在玻璃面板最下方；参数是系统导航栏高度（设计单位）
 */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onTool: (FileKind) -> Unit,
    onFiles: () -> Unit,
    onDeviceInfo: () -> Unit,
    navBar: @Composable (bottomInset: Float) -> Unit,
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(App.instance.scans) }
    // 清理完回到首页时刷新用量
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val storage = vm.storage

    // 首次进入首页：百分比从 0 滚到实际用量，外圈用量轨道同步从 0 长出来。
    // 只在本次启动第一次进入时播放（rememberSaveable 随返回栈保存），清理后回来只从旧值过渡到新值
    var counted by rememberSaveable { mutableStateOf(false) }
    val usage = remember { Animatable(if (counted) storage.usedFraction else 0f) }

    DesignFrame { m ->
        var sheetTop by remember { mutableStateOf<Float?>(null) }
        val ready = sheetTop != null
        LaunchedEffect(ready, storage.usedFraction) {
            if (!ready) return@LaunchedEffect
            // 先让舞台以当前值（首次为 0）就位一帧，轨道才会从 0 开始长
            withFrameNanos { }
            usage.animateTo(storage.usedFraction, tween(COUNT_MS, easing = FastOutSlowInEasing))
            counted = true
        }
        SaturnStage(
            view = sheetTop?.let {
                // 用目标值而不是逐帧的动画值：舞台自己用同样的 1100ms 缓动过渡，两者同步
                SaturnShots.home(usage.targetValue).placed(m.frameTop, it - m.frameTop, SaturnShots.HOME_SHEET_TOP)
            },
            particles = ParticleMode.ORBIT,
        )
        Box(Modifier.fillMaxSize().saturnTilt(LocalSaturn.current)) {
            HomeHeader(m.topBar, onDeviceInfo)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                UsageHero(storage, percent = { (usage.value * 100).roundToInt() })
                Spacer(Modifier.height(20.dp))
                GlassSheet(
                    Modifier.reportTop { sheetTop = it },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp),
                ) {
                    PrimaryCta("Smart Scan", "Redundant, duplicate and large files", onScan)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolTile(Icons.Rounded.FolderOpen, "Files", onFiles, Modifier.weight(1f))
                        TOOLS.forEach { kind ->
                            ToolTile(kind.icon(), kind.title.removeSuffix(" Files"), { onTool(kind) }, Modifier.weight(1f))
                        }
                    }
                    Column {
                        BannerSlot { AdBanner(Placements.HOME_BANNER, it) }
                        // .nav 在原型里撑满面板宽度（margin: 0 -20px）
                        Box(Modifier.layoutBleed(20)) {
                            navBar(m.navBottom)
                        }
                    }
                }
            }
        }
    }
}

/** 让子元素左右各溢出 [px] 设计单位，抵消面板内边距（CSS 的负 margin） */
private fun Modifier.layoutBleed(px: Int) = layout { measurable, constraints ->
    val extra = (px * 2).dp.roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun HomeHeader(top: Float, onDeviceInfo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = top.dp, start = Gutter, end = Gutter)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitGlyph(planet = Secondary, ring = Primary, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(BuildConfig.APP_NAME.uppercase(), style = OrbitText.Brand)
            Spacer(Modifier.height(4.dp))
            Text(BuildConfig.APP_TAGLINE, style = OrbitText.BrandSub)
        }
        GlassIconButton(Icons.Rounded.PhoneAndroid, "Device info", onDeviceInfo)
    }
}

/** 原型 .hero（home）：kicker → 84 号百分比 + 青色上标 % → 带光点的说明 */
@Composable
private fun UsageHero(storage: StorageInfo, percent: () -> Int) {
    val shadow = heroShadow()
    Column(Modifier.padding(horizontal = Gutter)) {
        Kicker("Storage used")
        Spacer(Modifier.height(10.dp))
        // .big 整体左移 4，sup 距数字 6、下沉 8
        Row(Modifier.offset(x = (-4).dp)) {
            Text("${percent()}", style = OrbitText.Hero.copy(shadow = shadow))
            Text("%", style = OrbitText.HeroUnit.copy(shadow = shadow), modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(Primary.copy(alpha = 0.6f), Color.Transparent), radius = 10.dp.toPx()), radius = 10.dp.toPx(), center = Offset(size.width / 2, size.height / 2)) }
                    .clip(CircleShape)
                    .background(Primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${formatSize(storage.used)} of ${formatSize(storage.total)} · outer orbit = used",
                style = OrbitText.Sub.copy(shadow = shadow),
            )
        }
    }
}
