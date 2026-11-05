package me.hanime.atv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.theme.Palette

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 30.dp),
    ) {
        SectionTitle("设置")
        Spacer(Modifier.height(22.dp))

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.user?.avatar != null) {
                    AsyncImage(
                        model = vm.user?.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp)),
                    )
                    Spacer(Modifier.width(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.isLoggedIn) (vm.user?.name ?: "已登录") else "未登录",
                        color = Palette.TextPrimary,
                        fontSize = 19.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vm.isLoggedIn) "可以同步收藏与订阅" else "登录后可同步收藏、稍后再看等内容",
                        color = Palette.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                if (vm.isLoggedIn) {
                    TvButton("退出登录", { vm.logout() })
                } else {
                    TvButton("登录", { vm.openLogin() }, primary = true)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingCard {
            Column {
                Text("播放设置", color = Palette.TextPrimary, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "播放器会自动选择可用的最高画质，可在播放界面切换清晰度与倍速。",
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                TvButton("清空观看历史", { vm.clearHistory() })
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingCard {
            Column {
                Text("关于", color = Palette.TextPrimary, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hanime ATV 1.0.0 · 非官方客户端\n内容来自 hanime1.me，仅供个人学习研究使用。",
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    TvFocusable(
        onClick = {},
        enabled = false,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(0.85f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                .padding(22.dp),
        ) {
            content()
        }
    }
}
