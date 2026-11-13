/**
 * [INPUT]: 依赖 core/ads 的 Ads.maskVisible（由 StartupActivity 判断是否渲染），依赖 ./theme 的颜色 token
 * [OUTPUT]: 对外提供 AdMask
 * [POS]: core/ui 的广告过渡遮罩：返回插屏加载期间盖住页面、拦截点击和返回键
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary

/** 过渡遮罩：拦截点击，提示正在加载 */
@Composable
fun AdMask() {
    BackHandler { }
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(12.dp))
            Text("Loading…", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
