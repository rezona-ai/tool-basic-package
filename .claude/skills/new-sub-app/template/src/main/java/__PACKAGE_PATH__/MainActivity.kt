/**
 * [INPUT]: 依赖 :core:ui 的 StartupActivity（闪屏、开屏广告、遮罩），依赖 ui 的 AppNav / AppTheme
 * [OUTPUT]: 对外提供 MainActivity
 * [POS]: {{APP_NAME}} 唯一 Activity：只提供闪屏、主题和主界面
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appbase.core.ui.StartupActivity
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import {{CODE_PACKAGE}}.ui.AppNav
import {{CODE_PACKAGE}}.ui.theme.AppTheme

class MainActivity : StartupActivity() {

    @Composable
    override fun Theme(content: @Composable () -> Unit) = AppTheme(content)

    @Composable
    override fun Splash() {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(28.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(20.dp))
                Text(BuildConfig.APP_NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(BuildConfig.APP_TAGLINE, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }

    /** 基于文件夹授权（SAF）的方向：改成 FolderGate { AppNav(...) }，并给 SettingsScreen 传 onFolders */
    @Composable
    override fun Main() = AppNav(openedFromReminder = openedFromReminder, onReminderHandled = ::onReminderHandled)
}
