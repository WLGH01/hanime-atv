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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.hanime.atv.data.VideoDetail
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

@Composable
fun DetailScreen(vm: AppViewModel, code: String, modifier: Modifier = Modifier) {
    LaunchedEffect(code) { vm.loadDetail(code) }

    when (val state = vm.detailState) {
        is Loadable.Loading, Loadable.Idle -> LoadingView(modifier, "正在加载详情…")
        is Loadable.Failure -> ErrorView(state.message, { vm.retryDetail() }, modifier)
        is Loadable.Success -> DetailContent(vm, state.data, modifier)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, video: VideoDetail, modifier: Modifier = Modifier) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(video.code) {
        delay(200)
        runCatching { playFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "hero") {
            DetailHero(
                vm = vm,
                video = video,
                playFocus = playFocus,
            )
        }
        if (video.tags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(video.tags, key = { it }) { tag ->
                        androidx.compose.material3.Surface(
                            color = Palette.Surface,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                "#$tag",
                                color = Palette.TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        video.description?.let { desc ->
            item(key = "desc") {
                Column(Modifier.padding(horizontal = 48.dp, vertical = 10.dp)) {
                    SectionTitle("简介")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        desc,
                        color = Palette.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                }
            }
        }

        if (video.related.isNotEmpty()) {
            item(key = "related") {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionTitle("相关推荐", Modifier.padding(start = 48.dp, bottom = 12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(video.related, key = { it.code }) { card ->
                            VideoCard(
                                card = card,
                                onClick = { vm.openDetail(card.code) },
                                width = 240.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(vm: AppViewModel, video: VideoDetail, playFocus: FocusRequester) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.cover)
                .crossfade(true)
                .build(),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x22000000),
                        0.5f to Color(0xAA000000),
                        1f to Palette.Background,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xDD090910), Color(0x55090910), Color.Transparent)),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 28.dp, end = 420.dp),
        ) {
            Text(
                video.title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                video.artist?.let { artist ->
                    if (video.artistAvatar != null) {
                        AsyncImage(
                            model = video.artistAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(artist, color = Palette.AccentSoft, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                }
                val meta = listOfNotNull(video.views, video.date, video.likeText)
                    .joinToString("  ·  ")
                Text(meta, color = Palette.TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = if (video.sources.isEmpty()) "暂无片源" else "播放",
                    onClick = { vm.play(video) },
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    focusRequester = playFocus,
                )
                TvButton(
                    text = if (video.saved) "已收藏" else "收藏",
                    onClick = { vm.toggleFavorite(video) },
                    icon = Icons.Filled.Star,
                )
                TvButton(text = "稍后看", onClick = { vm.addToWatchLater(video) })
                video.artist?.let { artist ->
                    TvButton(text = "作者作品", onClick = { vm.search(artist) })
                }
            }
            if (video.sources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "可用清晰度：" + video.sources.joinToString(" / ") { it.label },
                    color = Palette.TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
