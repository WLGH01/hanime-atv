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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import me.hanime.atv.data.HanimeCard
import me.hanime.atv.data.HomePage
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

@Composable
fun HomeScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    when (val state = vm.homeState) {
        is Loadable.Loading, Loadable.Idle -> LoadingView(modifier, "正在加载首页…")
        is Loadable.Failure -> ErrorView(state.message, { vm.loadHome(force = true) }, modifier)
        is Loadable.Success -> HomeContent(vm, state.data, modifier)
    }
}

@Composable
private fun HomeContent(vm: AppViewModel, page: HomePage, modifier: Modifier = Modifier) {
    val hero = page.banner ?: page.rows.firstOrNull()?.items?.firstOrNull()
    val heroFocus = androidx.compose.runtime.remember { FocusRequester() }

    LaunchedEffect(hero?.code) {
        delay(150)
        runCatching { heroFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "hero") {
            HeroSection(
                card = hero,
                onPlay = { hero?.let { vm.playCode(it.code) } },
                onDetail = { hero?.let { vm.openDetail(it.code) } },
                focusRequester = heroFocus,
            )
        }

        if (vm.history.isNotEmpty()) {
            item(key = "continue") {
                ContinueWatchingRow(vm)
            }
        }

        itemsIndexed(page.rows) { index, row ->
            Column(Modifier.padding(top = 6.dp, bottom = 10.dp)) {
                SectionTitle(
                    row.title,
                    Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(row.items, key = { it.code }) { card ->
                        VideoCard(card = card, onClick = { vm.openDetail(card.code) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    card: HanimeCard?,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    focusRequester: FocusRequester,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp),
    ) {
        if (card != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(card.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Palette.Surface))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x22000000),
                        0.45f to Color(0x99000000),
                        1f to Palette.Background,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE6090910), Color(0x33090910), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 28.dp, end = 420.dp),
        ) {
            Text(
                card?.title ?: "Hanime ATV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 32.sp,
            )
            Spacer(Modifier.height(12.dp))
            val meta = listOfNotNull(
                card?.uploader,
                card?.views,
                card?.timeAgo,
            ).joinToString("  ·  ")
            Text(
                meta.ifBlank { "今日精选 · 高清无修动画" },
                color = Palette.TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = "立即播放",
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    focusRequester = focusRequester,
                )
                TvButton(text = "详情", onClick = onDetail)
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("H · 高清在线", color = Palette.TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(vm: AppViewModel) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionTitle("继续观看", Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(vm.history.take(12), key = { it.code }) { item ->
                VideoCard(
                    card = HanimeCard(code = item.code, title = item.title, cover = item.cover),
                    onClick = { vm.openDetail(item.code) },
                    progress = item.progress,
                    progressText = "已看 ${(item.progress * 100).toInt()}%",
                )
            }
        }
    }
}


