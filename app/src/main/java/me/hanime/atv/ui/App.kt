package me.hanime.atv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.screen.DetailScreen
import me.hanime.atv.ui.screen.FavoritesScreen
import me.hanime.atv.ui.screen.HistoryScreen
import me.hanime.atv.ui.screen.HomeScreen
import me.hanime.atv.ui.screen.LoginScreen
import me.hanime.atv.ui.screen.PlayerScreen
import me.hanime.atv.ui.screen.SearchScreen
import me.hanime.atv.ui.screen.SettingsScreen
import me.hanime.atv.ui.theme.Palette

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        if (vm.homeState is Loadable.Idle) vm.loadHome()
    }

    val current = vm.screen
    val chromeVisible = current !is Screen.Player && current !is Screen.Login

    BackHandler(enabled = vm.backStack.size > 1) { vm.goBack() }

    val railFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { railFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        // 顶部微妙的渐变光晕，避免纯黑显得廉价
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x337C4DFF), Color.Transparent),
                        radius = 1400f,
                    ),
                ),
        )
        Row(Modifier.fillMaxSize()) {
            if (chromeVisible) {
                NavRail(vm, current, railFocus)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (current) {
                    Screen.Home -> HomeScreen(vm, Modifier.fillMaxSize())
                    Screen.Search -> SearchScreen(vm, Modifier.fillMaxSize())
                    Screen.Favorites -> FavoritesScreen(vm, Modifier.fillMaxSize())
                    Screen.History -> HistoryScreen(vm, Modifier.fillMaxSize())
                    Screen.Settings -> SettingsScreen(vm, Modifier.fillMaxSize())
                    Screen.Login -> LoginScreen(vm, Modifier.fillMaxSize())
                    is Screen.Detail -> DetailScreen(vm, current.code, Modifier.fillMaxSize())
                    is Screen.Player -> PlayerScreen(vm, Modifier.fillMaxSize())
                }
            }
        }
        ToastHost(vm)
    }
}

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Home, "首页", Icons.Filled.Home),
    NavItem(Screen.Search, "搜索", Icons.Filled.Search),
    NavItem(Screen.Favorites, "收藏", Icons.Filled.Star),
    NavItem(Screen.History, "历史", Icons.Filled.List),
    NavItem(Screen.Settings, "设置", Icons.Filled.Settings),
)

@Composable
private fun NavRail(vm: AppViewModel, current: Screen, firstItemFocus: FocusRequester) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Color(0x66000000))
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "H",
            color = Palette.Accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 22.dp),
        )
        navItems.forEachIndexed { index, item ->
            val selected = current == item.screen || (current is Screen.Detail && item.screen == Screen.Home)
            NavRailButton(
                item = item,
                selected = selected,
                focusRequester = if (index == 0) firstItemFocus else null,
                onClick = {
                    if (item.screen == Screen.Favorites && !vm.isLoggedIn) {
                        vm.openLogin()
                    } else {
                        vm.navigateToTopLevel(item.screen)
                    }
                },
            )
        }
        Spacer(Modifier.weight(1f))
        NavRailButton(
            item = NavItem(Screen.Login, if (vm.isLoggedIn) "账号" else "登录", Icons.Filled.Person),
            selected = current == Screen.Login,
            onClick = { if (vm.isLoggedIn) vm.navigateToTopLevel(Screen.Settings) else vm.openLogin() },
            showDot = vm.isLoggedIn,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NavRailButton(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    showDot: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.04f,
        modifier = Modifier.width(76.dp),
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth(1f)
                .background(
                    when {
                        selected -> Color(0x33FF5C8A)
                        focused -> Palette.SurfaceHigh
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(18.dp),
                )
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected || focused) Palette.Accent else Palette.TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
                if (showDot) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .background(Palette.Success, RoundedCornerShape(50)),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                item.label,
                fontSize = 11.sp,
                color = if (selected || focused) Palette.TextPrimary else Palette.TextTertiary,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ToastHost(vm: AppViewModel) {
    val message = vm.toast
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            vm.consumeToast()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 40.dp),
    ) {
        Surface(
            color = Palette.SurfaceHigh.copy(alpha = 0.96f),
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 12.dp,
        ) {
            Text(
                message.orEmpty(),
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }
    }
}



