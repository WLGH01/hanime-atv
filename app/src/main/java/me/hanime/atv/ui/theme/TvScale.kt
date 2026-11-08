package me.hanime.atv.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.min

/**
 * 电视尺寸归一化。
 *
 * Android TV 的布局基准是 960×540dp（1080p 用 xhdpi、720p 用 tvdpi、4K 用 4x），
 * 但不少国产电视（小米/Redmi 电视尤其常见）会把 1080p 面板上报成 480dpi，
 * 于是逻辑尺寸只剩 640×360dp —— 结果就是所有 dp/sp 被放大 1.5 倍：
 * 对话框大到出屏、进度条又粗又亮、字体特别大、键盘排不下。
 *
 * 这里按"逻辑短边固定 540dp"重新计算 density，让任何电视都回到标准逻辑尺寸；
 * 用户可以再用"界面缩放"在 80%~125% 之间微调。
 */
private const val BASE_SHORT_SIDE_DP = 540f

/** 电视上系统字体经常被放大，这里限制一下以免撑破布局 */
private const val MIN_FONT_SCALE = 1.0f
private const val MAX_FONT_SCALE = 1.1f

@Composable
fun rememberTvDensity(userScale: Float = 1f): Density {
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    return remember(
        base.density,
        base.fontScale,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.densityDpi,
        userScale,
    ) {
        normalize(base, configuration, userScale)
    }
}

private fun normalize(base: Density, configuration: Configuration, userScale: Float): Density {
    val widthPx = configuration.screenWidthDp * base.density
    val heightPx = configuration.screenHeightDp * base.density
    val shortSidePx = min(widthPx, heightPx)
    if (shortSidePx <= 0f) return base

    // 目标：逻辑短边 = 540dp
    var target = shortSidePx / BASE_SHORT_SIDE_DP
    // 防止极端的 Configuration 数据把界面推到不可用
    target = target.coerceIn(base.density * 0.5f, base.density * 3f)
    target *= userScale.coerceIn(0.8f, 1.25f)

    val fontScale = base.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    return if (target == base.density && fontScale == base.fontScale) {
        base
    } else {
        Density(target, fontScale)
    }
}

/** 给设置页展示当前逻辑分辨率用 */
@Composable
fun currentLogicalSize(): String {
    val configuration = LocalConfiguration.current
    return "${configuration.screenWidthDp} × ${configuration.screenHeightDp} dp"
}
