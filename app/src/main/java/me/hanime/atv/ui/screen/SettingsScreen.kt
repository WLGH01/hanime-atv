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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.hanime.atv.data.UpdateState
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 22.dp),
    ) {
        SectionTitle("设置")
        Spacer(Modifier.height(16.dp))

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.user?.avatar != null) {
                    AsyncImage(
                        model = vm.user?.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(23.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.isLoggedIn) (vm.user?.name ?: "已登录") else "未登录",
                        color = Palette.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (vm.isLoggedIn) "收藏与稍后再看已同步" else "登录后可同步收藏、稍后再看、观看记录",
                        color = Palette.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
                if (vm.isLoggedIn) {
                    TvButton("退出登录", { vm.logout() })
                } else {
                    TvButton("登录", { vm.openLogin() }, primary = true)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 界面适配（小米电视密度异常的核心开关）
        SettingCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("界面缩放", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "已自动适配电视逻辑尺寸（标准 960×540dp，系统密度异常时自动校正）。画面仍偏大就选小一点。",
                            color = Palette.TextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "${(vm.uiScale * 100).toInt()}%",
                        color = Palette.Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f).forEach { value ->
                        ScaleChip(
                            label = "${(value * 100).toInt()}%",
                            selected = kotlin.math.abs(vm.uiScale - value) < 0.01f,
                            onClick = { vm.applyUiScale(value) },
                        )
                    }
                    TvButton("恢复默认", { vm.applyUiScale(1f) })
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 播放与历史
        SettingCard {
            Column {
                Text("播放与历史", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "播放器会自动选择最高画质，播放中可切换清晰度、倍速，进度会自动保存。",
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("清空观看历史", { vm.clearHistory() })
                    TvButton("清空搜索历史", { vm.clearSearchHistory(); vm.showToast("已清空搜索历史") })
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 应用内更新
        SettingCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("版本与更新", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "当前版本 v${vm.versionName} · 通过 GitHub Releases 检查更新",
                            color = Palette.TextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                    val state = vm.updateState
                    if (state is UpdateState.Checking) {
                        Text("检查中…", color = Palette.TextSecondary, fontSize = 12.sp)
                    } else {
                        TvButton(
                            if (state is UpdateState.Available || state is UpdateState.Ready) "有可用更新" else "检查更新",
                            { vm.checkUpdate() },
                            primary = state is UpdateState.Available || state is UpdateState.Ready,
                        )
                    }
                }
                when (val state = vm.updateState) {
                    is UpdateState.Available -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "新版本 v${state.info.version} 已发布",
                            color = Palette.Success,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        TvButton("查看并更新", { vm.openUpdateDialog() }, primary = true)
                    }

                    is UpdateState.Ready -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (vm.canInstallPackages) {
                                "下载完成，点击「立即安装」继续"
                            } else {
                                "下载完成，但还没有「安装未知应用」权限"
                            },
                            color = if (vm.canInstallPackages) Palette.Success else Palette.Danger,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton("立即安装", { vm.installUpdate(state.file) }, primary = true)
                            if (!vm.canInstallPackages) {
                                TvButton("打开安装权限设置", { vm.openInstallPermissionSettings() })
                            }
                        }
                    }

                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = Palette.Danger, fontSize = 12.sp)
                    }

                    else -> Unit
                }
                val hint = vm.installHint
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, color = Palette.TextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingCard {
            Column {
                Text("关于", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Hanime ATV v${vm.versionName} · 非官方 Android TV 客户端\n" +
                        "内容来自 hanime1.me，仅供个人学习研究使用，所有版权归原作者所有。\n" +
                        "开源地址：github.com/WLGH01/hanime-atv",
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ScaleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TvFocusable(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        focusedScale = 1.03f,
    ) { focused ->
        Text(
            label,
            color = if (selected) Palette.Accent else if (focused) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .background(
                    when {
                        selected -> Color(0x2EFF5C8A)
                        focused -> Palette.SurfaceHigh
                        else -> Palette.Surface
                    },
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth(0.9f)
            .background(Palette.Surface.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        content()
    }
}
