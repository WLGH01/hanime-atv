package me.hanime.atv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.hanime.atv.data.SearchPage
import me.hanime.atv.data.TagCatalog
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.theme.Palette

/**
 * 分类页 / 搜索页共用的一套自绘筛选控件。
 *
 * 全部基于 [TvFocusable]，不依赖 Material3 的 DropdownMenu / AlertDialog，
 * 保证遥控器可以聚焦、且尺寸在 960×540dp 的电视逻辑尺寸里不溢出。
 */

/** 普通筛选项 chip：类型、排序、标签、清晰度都用它 */
@Composable
fun FilterChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        shape = shape,
        focusedScale = 1.04f,
    ) { focused ->
        Box(
            Modifier
                .background(
                    when {
                        selected -> Palette.Accent
                        focused -> Palette.SurfaceHigh
                        else -> Palette.Surface
                    },
                    shape,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                color = when {
                    selected -> Color.White
                    focused -> Color.White
                    else -> Palette.TextSecondary
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 操作型 chip：重置 / 更多筛选 / 展开 / 选择标签… */
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        shape = shape,
        focusedScale = 1.05f,
    ) { focused ->
        val bg = if (primary) {
            Modifier.background(Palette.accentGradient, shape)
        } else {
            Modifier.background(if (focused) Palette.SurfaceHigh else Palette.Surface, shape)
        }
        Box(
            Modifier
                .then(bg)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                color = if (primary || focused) Color.White else Palette.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 只读的信息胶囊（当前筛选摘要等），不可聚焦 */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Surface,
) {
    Box(
        modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = Palette.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行首小标签，例如“类型”“排序”“标签” */
@Composable
fun RowLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.width(40.dp),
        color = Palette.TextTertiary,
        fontSize = 11.sp,
    )
}

/**
 * 结果区：加载 / 失败 / 空 / 网格 + 加载更多。
 * 分类页与搜索页共用，避免两处逻辑漂移。
 */
@Composable
fun BrowseResults(
    state: Loadable<SearchPage>,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "没有符合条件的影片",
    onResetFilter: (() -> Unit)? = null,
) {
    when (state) {
        Loadable.Idle, Loadable.Loading -> LoadingView(modifier, "正在加载…")
        is Loadable.Failure -> ErrorView(state.message, onRetry, modifier)
        is Loadable.Success -> {
            val page = state.data
            if (page.items.isEmpty()) {
                Column(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(emptyText, color = Palette.TextTertiary, fontSize = 14.sp)
                    if (onResetFilter != null) {
                        Spacer(Modifier.height(14.dp))
                        TvButton("重置筛选", onResetFilter, primary = true)
                    }
                }
            } else {
                // 分类/标签结果页是 2:3 竖版海报，首页/关键词搜索是 16:9 横版缩略图。
                // 同一页里以多数为准，避免混排时格子忽高忽低。
                val portraitGrid = page.items.count { it.portrait } * 2 > page.items.size
                val cardWidth = if (portraitGrid) 168.dp else 196.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardWidth),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(page.items, key = { it.code }) { card ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            VideoCard(
                                card = card,
                                onClick = { onOpen(card.code) },
                                width = cardWidth,
                                portrait = card.portrait,
                            )
                        }
                    }
                    if (page.hasNext) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TvButton(
                                    text = if (loadingMore) "加载中…" else "加载更多",
                                    onClick = { if (!loadingMore) onLoadMore() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签选择覆盖层：热门标签 + 按站点分组的所有标签。
 * 纯自绘（半透明遮罩 + 卡片），遥控器可完整操作。
 */
@Composable
fun TagPickerOverlay(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    onClearTags: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }

    val firstChipFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(160)
        runCatching { firstChipFocus.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xF2090910)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .background(Palette.BackgroundElevated, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("选择标签")
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (selected.isEmpty()) "未选择" else "已选 ${selected.size} 个",
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("↑↓ 浏览 · 确定 勾选", color = Palette.TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                item(key = "popular") {
                    TagGroup(
                        title = "热门标签",
                        tags = TagCatalog.popular,
                        selected = selected,
                        onToggle = onToggle,
                        firstFocusRequester = firstChipFocus,
                    )
                }
                listItems(TagCatalog.groups, key = { it.first }) { group ->
                    TagGroup(
                        title = group.first,
                        tags = group.second,
                        selected = selected,
                        onToggle = onToggle,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TvButton("清空标签", onClearTags)
                Spacer(Modifier.weight(1f))
                TvButton("取消", onCancel)
                Spacer(Modifier.width(10.dp))
                TvButton("完成", onDone, primary = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(
    title: String,
    tags: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            text = title,
            color = Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEachIndexed { index, tag ->
                FilterChip(
                    label = tag,
                    onClick = { onToggle(tag) },
                    selected = selected.contains(tag),
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            }
        }
    }
}
