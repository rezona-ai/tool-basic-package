/**
 * [INPUT]: 依赖品牌 res 的启动图标、core/ui/theme 的 Bg、ui/theme 的 OrbitText
 * [OUTPUT]: 对外提供 SplashScreen
 * [POS]: ui/splash 的冷启动闪屏，图标位置与系统启动画面一致，等待开屏广告期间展示
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.R
import com.orbitclean.app.ui.theme.OrbitText

/**
 * 冷启动闪屏：等待开屏广告期间展示。
 * 图标的位置和尺寸与 Android 12+ 系统启动画面一致（屏幕居中、160dp 圆形裁切、图层 240dp），
 * 从系统启动画面切过来时图标不跳动，看起来是同一屏。
 */
@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(160.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(240.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(240.dp))
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name).uppercase(), style = OrbitText.Brand.copy(fontSize = 22.sp), color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("File manager & storage cleaner", color = TextSecondary, fontSize = 13.sp)
        }
        CircularProgressIndicator(
            color = Primary,
            strokeWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .size(28.dp),
        )
    }
}
