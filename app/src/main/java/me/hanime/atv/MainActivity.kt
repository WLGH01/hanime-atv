package me.hanime.atv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import me.hanime.atv.ui.AppRoot
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.theme.HanimeTheme
import me.hanime.atv.ui.theme.rememberTvDensity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            HanimeTheme {
                val vm: AppViewModel = viewModel()
                // 关键：把小米等"密度上报异常"的电视归一化到 960x540dp 的 TV 逻辑尺寸
                val density = rememberTvDensity(vm.uiScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    AppRoot(vm)
                }
            }
        }
    }
}
