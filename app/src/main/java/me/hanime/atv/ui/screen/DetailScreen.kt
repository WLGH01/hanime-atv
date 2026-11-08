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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import me.hanime.atv.data.VideoDetail
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.ActionChip
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.FilterChip
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.RowLabel
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

@Composable
fun DetailScreen(vm: AppViewModel, code: String, modifier: Modifier = Modifier) {
    LaunchedEffect(code) { vm.loadDetail(code) }

    when (val state = vm.detailState) {
        Loadable.Idle, Loadable.Loading -> LoadingView(modifier, "正在加载详情…")
        is Loadable.Failure -> ErrorView(state.message, { vm.retryDetail() }, modifier)
        is Loadable.Success -> DetailContent(vm, state.data, modifier)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, video: VideoDetail, modifier: Modifier = Modifier) {
    val playFocus = remember { FocusRequester() }
    var descriptionExpanded by remember(video.code) { mutableStateOf(false) }

    LaunchedEffect(video.code) {
        delay(200)
        runCatching { playFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item(key = "hero") {
            DetailHero(vm, video, playFocus)
        }
        if (video.sources.isNotEmpty()) {
            item(key = "quality") {
                QualityRow(vm, video)
            }
        }
        if (video.tags.isNotEmpty()) {
            item(key = "tags") {
                DetailTagsRow(vm, video.tags)
            }
        }
        video.description?.takeIf { it.isNotBlank() }?.let { description ->
            item(key = "description") {
                DescriptionBlock(
                    text = description,
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded },
                )
            }
        }
        if (video.related.isNotEmpty()) {
            item(key = "related") {
                RelatedRow(vm, video.related)
            }
        }
    }
}

/** 顶部大图：300dp（原来 430dp 会占掉大半屏，导致下面的内容全被挤走） */
@Composable
private fun DetailHero(vm: AppViewModel, video: VideoDetail, playFocus: FocusRequester) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp),
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
                        0.45f to Color(0xAA000000),
                        1f to Palette.Background,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xDD090910), Color(0x44090910), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, bottom = 14.dp),
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 27.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                video.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                    if (video.artistAvatar != null) {
                        AsyncImage(
                            model = video.artistAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(artist, color = Palette.AccentSoft, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                }
                val meta = listOfNotNull(video.views, video.date, video.likeText)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = Palette.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                )
                TvButton(
                    text = "稍后看",
                    onClick = { vm.addToWatchLater(video) },
                )
                video.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                    TvButton(
                        text = "作者作品",
                        onClick = { vm.openQuery(artist) },
                    )
                }
            }
        }
    }
}

/** 可用清晰度：点击提示在播放器内切换（不新增 VM API） */
@Composable
private fun QualityRow(vm: AppViewModel, video: VideoDetail) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel("清晰度")
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(video.sources) { source ->
                FilterChip(
                    label = source.label,
                    onClick = { vm.showToast("播放时可在播放器内切换清晰度") },
                )
            }
        }
    }
}

/** 标签行：每个标签都可以点，点了就直接按标签筛选（用户第 5 条需求） */
@Composable
private fun DetailTagsRow(vm: AppViewModel, tags: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel("标签")
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(tags, key = { it }) { tag ->
                FilterChip(
                    label = "#$tag",
                    onClick = { vm.openTag(tag) },
                )
            }
        }
    }
}

/** 简介：默认最多 4 行，可聚焦按钮展开 / 收起 */
@Composable
private fun DescriptionBlock(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Text(
            text = "简介",
            color = Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = text,
            color = Palette.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(Modifier.height(6.dp))
        ActionChip(
            label = if (expanded) "收起" else "展开",
            onClick = onToggle,
        )
    }
}

@Composable
private fun RelatedRow(vm: AppViewModel, related: List<HanimeCard>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SectionTitle(
            "相关推荐",
            Modifier.padding(start = 32.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(related, key = { it.code }) { card ->
                VideoCard(
                    card = card,
                    onClick = { vm.openDetail(card.code) },
                    width = 196.dp,
                )
            }
        }
    }
}
