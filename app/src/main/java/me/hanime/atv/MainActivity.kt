package me.hanime.atv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import me.hanime.atv.ui.AppRoot
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.theme.HanimeTheme
import me.hanime.atv.ui.theme.rememberTvDensity

class MainActivity : ComponentActivity() {

    // Activity 作用域持有：onResume 里也要用到（从"安装未知应用"设置页返回后自动继续安装）
    private val vm: AppViewModel by viewModels()

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
                // 关键：把小米等"密度上报异常"的电视归一化到 960x540dp 的 TV 逻辑尺寸
                val density = rememberTvDensity(vm.uiScale)
                CompositionLocalProvider(LocalDensity provides density) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户从系统"允许安装应用"设置页返回时，若已授权则自动继续安装
        vm.onAppResume()
    }
}
