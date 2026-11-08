package me.hanime.atv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.hanime.atv.data.UpdateState
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * 应用内更新弹窗：数据来自 GitHub Releases（CI 以 v<version> 打 tag）。
 * 完全用 Compose 绘制，不依赖 Material AlertDialog（电视上容易被系统主题放大）。
 */
@Composable
fun BoxScope.UpdateHost(vm: AppViewModel) {
    val info = vm.updateInfoOrNull()
    if (!vm.showUpdateDialog || info == null) return

    val state = vm.updateState
    BackHandler { vm.dismissUpdateDialog() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6080910)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .background(Palette.Surface, RoundedCornerShape(22.dp))
                .padding(horizontal = 30.dp, vertical = 26.dp),
        ) {
            Text(
                "发现新版本 v${info.version}",
                color = Palette.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "当前版本 v${vm.versionName} · ${sizeText(info.apkSize)}",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (info.notes.isNotBlank()) {
                Text(
                    info.notes,
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(16.dp))
            }

            when (state) {
                is UpdateState.Downloading -> {
                    val percent = (state.progress * 100).toInt()
                    Text(
                        "正在下载 $percent%  ·  ${sizeText(state.bytes)}",
                        color = Palette.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    DownloadBar(state.progress)
                    Spacer(Modifier.height(16.dp))
                }

                is UpdateState.Ready -> {
                    Text("下载完成，正在打开系统安装器…", color = Palette.Success, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                }

                is UpdateState.Failed -> {
                    Text(state.message, color = Palette.Danger, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                }

                else -> Unit
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is UpdateState.Downloading -> {
                        TvButton("后台下载中…", onClick = { vm.dismissUpdateDialog() })
                    }

                    is UpdateState.Ready -> {
                        val ready = state
                        TvButton("立即安装", onClick = { vm.installUpdate(ready.file) }, primary = true)
                        TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                    }

                    is UpdateState.Failed -> {
                        TvButton("重试下载", onClick = { vm.downloadUpdate() }, primary = true)
                        TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                    }

                    else -> {
                        TvButton("立即更新", onClick = { vm.downloadUpdate() }, primary = true)
                        TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                        TvButton("跳过此版本", onClick = { vm.skipUpdate() })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(999.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Palette.Accent, Palette.Violet)),
                topLeft = Offset.Zero,
                size = Size((size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(size.height), size.height),
                cornerRadius = corner,
            )
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
}

/** 弹窗出现时把焦点交给"立即更新"，方便遥控器直接确认 */
@Composable
fun rememberUpdateFocus(): androidx.compose.ui.focus.FocusRequester {
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        delay(140)
        runCatching { requester.requestFocus() }
    }
    return requester
}
