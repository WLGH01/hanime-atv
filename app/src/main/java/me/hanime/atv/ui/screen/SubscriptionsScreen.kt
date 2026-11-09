package me.hanime.atv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.hanime.atv.data.SubscriptionArtist
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.ActionChip
import me.hanime.atv.ui.components.EmptyView
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

/**
 * 订阅页（需登录）：站点 `/subscriptions` 的已订阅作者 + 他们的最新作品。
 *
 * 站点把作者和作品放在同一个页面：作者列表用于筛选（点作者 = 按名字搜索该作者），
 * 作品区与首页/收藏共用一套卡片解析，所以这里也直接复用 [VideoCard]。
 */
@Composable
fun SubscriptionsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    // 登录状态变化（登录成功 / 退出）后自动重拉
    LaunchedEffect(Unit, vm.isLoggedIn) {
        if (vm.isLoggedIn) vm.loadSubscriptions()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("订阅", Modifier.weight(1f))
            if (vm.isLoggedIn) {
                Text(
                    vm.user?.name.orEmpty(),
                    color = Palette.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                ActionChip("刷新", onClick = { vm.loadSubscriptions(force = true) })
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!vm.isLoggedIn) {
            LoginRequired(onLogin = { vm.openLogin() })
        } else when (val state = vm.subscriptionsState) {
            Loadable.Idle, Loadable.Loading -> LoadingView()
            is Loadable.Failure -> ErrorView(state.message, { vm.loadSubscriptions(force = true) })
            is Loadable.Success -> {
                val page = state.data
                if (page.artists.isEmpty() && page.items.isEmpty()) {
                    EmptyView("还没有订阅任何作者\n在喜欢的作者页面点「订阅」后，这里会显示他们的新作品")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(196.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (page.artists.isNotEmpty()) {
                            item(key = "artists", span = { GridItemSpan(maxLineSpan) }) {
                                ArtistRow(vm, page.artists)
                            }
                        }
                        if (page.items.isNotEmpty()) {
                            item(key = "feed-title", span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SectionTitle("最新作品")
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "已加载 ${page.items.size} 部",
                                        color = Palette.TextTertiary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            gridItems(page.items, key = { it.code }) { card ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    VideoCard(
                                        card = card,
                                        onClick = { vm.openDetail(card.code) },
                                        width = if (card.portrait) 168.dp else 196.dp,
                                        portrait = card.portrait,
                                    )
                                }
                            }
                        }
                        if (page.hasNext) {
                            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TvButton(
                                        text = if (vm.subscriptionsLoadingMore) "加载中…" else "加载更多",
                                        onClick = { if (!vm.subscriptionsLoadingMore) vm.subscriptionsMore() },
                                    )
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
private fun LoginRequired(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "登录后可查看订阅作者的作品",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            TvButton("去登录", onClick = onLogin, primary = true)
        }
    }
}

/** 已订阅作者横向列表：点了按作者名搜索其作品（与站点行为一致） */
@Composable
private fun ArtistRow(vm: AppViewModel, artists: List<SubscriptionArtist>) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("订阅作者")
            Spacer(Modifier.width(8.dp))
            Text("${artists.size} 位", color = Palette.TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("点作者查看其全部作品", color = Palette.TextTertiary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp, bottom = 6.dp),
        ) {
            rowItems(artists, key = { it.name }) { artist ->
                ArtistCard(artist = artist, onClick = { vm.openQuery(artist.name) })
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: SubscriptionArtist, onClick: () -> Unit) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.width(108.dp),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.05f,
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.SurfaceHigh else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (artist.avatar != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        artist.name.take(1),
                        color = Palette.AccentSoft,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                artist.name,
                color = if (focused) Color.White else Palette.TextPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!artist.countText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    artist.countText,
                    color = Palette.TextTertiary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
