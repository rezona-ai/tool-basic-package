/**
 * [INPUT]: 依赖 :features:cleaner 的 CleanViewModel / CleanResult，依赖 ui/scene 的 CleanScene，依赖 :core:ads 的 showInterstitial
 * [OUTPUT]: 对外提供 CleaningView、CLEAN_ANIMATION_MS
 * [POS]: 清理进行中的整页：大场景（CLEANING 阶段）+ 已释放空间；删除完成后弹一次清理完成插屏，再交给完成页。
 *        删除是真实进度；动画最短 [CLEAN_ANIMATION_MS] 只为过渡不闪屏，不为等广告加载而拖时间
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.clean

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ads.showInterstitial
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.state.CleanResult
import com.appbase.feature.cleaner.state.CleanViewModel
import {{CODE_PACKAGE}}.ui.Placements
import {{CODE_PACKAGE}}.ui.scene.CleanScene
import {{CODE_PACKAGE}}.ui.scene.ScenePhase

/** 清理动画的最短时长：删除很快时也让用户看清“正在删除”，避免页面一闪而过 */
const val CLEAN_ANIMATION_MS = 900L

@Composable
fun CleaningView(vm: CleanViewModel, onCleaned: (CleanResult) -> Unit) {
    val activity = LocalActivity.current
    val progress = remember { Animatable(0f) }
    BackHandler { }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(CLEAN_ANIMATION_MS.toInt())) }
    val result = vm.cleanResult
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        progress.animateTo(1f, tween(150))
        showInterstitial(activity, Placements.CLEAN_DONE_INTERSTITIAL) {
            vm.finishClean()
            onCleaned(result)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CleanScene(ScenePhase.CLEANING, progress.value, Modifier.fillMaxWidth(0.82f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatSize((vm.cleaningBytes * progress.value).toLong()), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("freed", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        // 已处理的条数跟着进度一起跳，和中间的释放空间同步
        val doneCount = (vm.cleaningCount * progress.value).toInt().coerceAtMost(vm.cleaningCount)
        Text("Removing $doneCount of ${plural(vm.cleaningCount, "item")}…", color = TextSecondary)
    }
}
