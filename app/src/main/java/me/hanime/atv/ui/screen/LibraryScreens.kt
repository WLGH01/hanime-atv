package me.hanime.atv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.EmptyView
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

@Composable
fun FavoritesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit, vm.isLoggedIn) { if (vm.isLoggedIn) vm.loadFavorites() }

    Column(modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SectionTitle("我的收藏", Modifier.weight(1f))
            if (vm.isLoggedIn) {
                Text(vm.user?.name.orEmpty(), color = Palette.TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (!vm.isLoggedIn) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(90.dp))
                Text("登录后可同步站点收藏", color = Palette.TextSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
                TvButton("去登录", { vm.openLogin() }, primary = true)
            }
        } else when (val state = vm.favoritesState) {
            is Loadable.Loading, Loadable.Idle -> LoadingView()
            is Loadable.Failure -> ErrorView(state.message, { vm.loadFavorites(force = true) })
            is Loadable.Success -> {
                if (vm.favorites.isEmpty()) {
                    EmptyView("还没有收藏的影片")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(268.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(bottom = 60.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(vm.favorites, key = { it.code }) { card ->
                            VideoCard(card = card, onClick = { vm.openDetail(card.code) })
                        }
                        item {
                            Box(Modifier.padding(top = 6.dp)) {
                                if (state.data.hasNext) {
                                    TvButton("加载更多", { vm.favoritesMore() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SectionTitle("观看历史", Modifier.weight(1f))
            if (vm.history.isNotEmpty()) {
                TvButton("清空", { vm.clearHistory() })
            }
        }
        Spacer(Modifier.height(18.dp))
        if (vm.history.isEmpty()) {
            EmptyView("还没有观看记录")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(268.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 60.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.history, key = { it.code }) { item ->
                    VideoCard(
                        card = me.hanime.atv.data.HanimeCard(
                            code = item.code,
                            title = item.title,
                            cover = item.cover,
                        ),
                        onClick = { vm.openDetail(item.code) },
                        progress = item.progress,
                        progressText = "已看 ${(item.progress * 100).toInt()}%",
                    )
                }
            }
        }
    }
}

