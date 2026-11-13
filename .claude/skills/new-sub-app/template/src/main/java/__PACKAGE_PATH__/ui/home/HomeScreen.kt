/**
 * [INPUT]: 依赖 :core:ui 的通用组件，依赖 :core:ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: ui/home 的首页：{{DIRECTION_NAME}} 的核心功能入口（生成时的占位，按方向重写）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ads.AdBanner
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.theme.TextSecondary
import {{CODE_PACKAGE}}.BuildConfig
import {{CODE_PACKAGE}}.ui.Placements

@Composable
fun HomeScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(BuildConfig.APP_NAME, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(BuildConfig.APP_TAGLINE, color = TextSecondary)
        AppCard(Modifier.fillMaxWidth()) {
            Text("{{DIRECTION_NAME}}", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
        }
        AdBanner(Placements.HOME_BANNER)
    }
}
