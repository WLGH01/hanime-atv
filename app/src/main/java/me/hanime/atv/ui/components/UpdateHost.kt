package me.hanime.atv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.hanime.atv.data.UpdateState
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * 应用内更新弹窗：数据来自 GitHub Releases（CI 以 v<version> 打 tag）。
 *
 * 用 [Dialog] 而不是普通 Box 覆盖层：
 * - 弹窗跑在独立窗口里，遥控器 D-pad 焦点不会再"漏"到背景视频卡片上；
 * - 返回键通过 dismissOnBackPress/onDismissRequest 关闭；
 * - 视觉仍是自绘的全屏深色遮罩 + 560dp 圆角卡片（不用 Material AlertDialog）。
 */
@Composable
fun UpdateHost(vm: AppViewModel) {
    val info = vm.updateInfoOrNull()
    if (!vm.showUpdateDialog || info == null) return

    val state = vm.updateState
    val primaryFocus = remember { FocusRequester() }

    // Downloading -> Ready -> Failed 按钮会更换，状态一变就重新把焦点交给主按钮
    val stateKey = when (state) {
        is UpdateState.Downloading -> "downloading"
        is UpdateState.Ready -> "ready"
        is UpdateState.Failed -> "failed"
        else -> "idle"
    }
    LaunchedEffect(stateKey) {
        // 弹窗是独立窗口，刚创建时焦点节点可能还没挂载：延迟 120ms 再请求，失败则短暂重试
        repeat(6) {
            delay(120)
            if (runCatching { primaryFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Dialog(
        onDismissRequest = { vm.dismissUpdateDialog() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
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
                        Text("下载完成", color = Palette.Success, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "按「立即安装」继续，系统会弹出安装确认界面",
                            color = Palette.TextTertiary,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    is UpdateState.Failed -> {
                        Text(state.message, color = Palette.Danger, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                    }

                    else -> Unit
                }

                // 安装失败 / 需要授权时的提示与入口
                val hint = vm.installHint ?: missingPermissionHint(state, vm.canInstallPackages)
                if (!hint.isNullOrBlank()) {
                    Text(
                        hint,
                        color = if (vm.canInstallPackages) Palette.Danger else Palette.Success,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 只在确实缺权限时才给"去授权"入口（安装包丢失等提示不需要它）
                        if (!vm.canInstallPackages) {
                            TvButton("打开安装权限设置", onClick = { vm.openInstallPermissionSettings() })
                        }
                        if (state is UpdateState.Ready && vm.canInstallPackages) {
                            TvButton("重试安装", onClick = { vm.installUpdate(state.file) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state) {
                        is UpdateState.Downloading -> {
                            TvButton(
                                "后台下载中…",
                                onClick = { vm.dismissUpdateDialog() },
                                focusRequester = primaryFocus,
                            )
                        }

                        is UpdateState.Ready -> {
                            val ready = state
                            TvButton(
                                "立即安装",
                                onClick = { vm.installUpdate(ready.file) },
                                primary = true,
                                focusRequester = primaryFocus,
                            )
                            TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                        }

                        is UpdateState.Failed -> {
                            TvButton(
                                "重试下载",
                                onClick = { vm.downloadUpdate() },
                                primary = true,
                                focusRequester = primaryFocus,
                            )
                            TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                        }

                        else -> {
                            TvButton(
                                "立即更新",
                                onClick = { vm.downloadUpdate() },
                                primary = true,
                                focusRequester = primaryFocus,
                            )
                            TvButton("稍后", onClick = { vm.dismissUpdateDialog() })
                            TvButton("跳过此版本", onClick = { vm.skipUpdate() })
                        }
                    }
                }
            }
        }
    }
}

/** Ready 但还没有"安装未知应用"权限时，给一行提示 */
private fun missingPermissionHint(state: UpdateState, canInstallPackages: Boolean): String? =
    if (state is UpdateState.Ready && !canInstallPackages) {
        "还没有允许本应用安装应用，点「打开安装权限设置」授权后返回会自动继续"
    } else {
        null
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
