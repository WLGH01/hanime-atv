package me.hanime.atv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.hanime.atv.data.Genres
import me.hanime.atv.data.SortOption
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.ActionChip
import me.hanime.atv.ui.components.BrowseResults
import me.hanime.atv.ui.components.FilterChip
import me.hanime.atv.ui.components.MetaPill
import me.hanime.atv.ui.components.RowLabel
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TagPickerOverlay
import me.hanime.atv.ui.theme.Palette

/**
 * 分类页：类型 / 排序 / 标签 三重筛选 + 结果网格。
 *
 * 版面按 960×540dp 电视逻辑尺寸排布（左侧导航 84dp，内容区 876×540dp）：
 * 顶部筛选区固定，结果网格占剩余空间滚动。
 */
@Composable
fun DiscoverScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var pickerVisible by remember { mutableStateOf(false) }
    var pickerOpened by remember { mutableStateOf(false) }
    val pickerButtonFocus = remember { FocusRequester() }

    // 首次进入（Idle）才主动拉一次，避免从详情页返回时重复请求
    LaunchedEffect(Unit) {
        if (vm.browseState is Loadable.Idle) vm.applyFilter(vm.filter)
    }

    // 覆盖层关闭后把焦点还给“选择标签”按钮，遥控器不会失焦
    LaunchedEffect(pickerVisible) {
        if (pickerVisible) {
            pickerOpened = true
        } else if (pickerOpened) {
            delay(80)
            runCatching { pickerButtonFocus.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize()) {
        // 覆盖层打开时不再组合底层内容：否则遥控器按 ↑/↓ 的焦点搜索可能落到
        // 被卡片遮住、看不见的 chip 上（失焦感很强）。
        if (!pickerVisible) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 14.dp),
            ) {
                // -------------------------------------------- 标题 + 摘要 + 重置
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("分类")
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        MetaPill(text = vm.filter.summary.ifBlank { "全部影片" })
                    }
                    Spacer(Modifier.width(12.dp))
                    ActionChip("重置", onClick = { vm.clearFilter() })
                }

                Spacer(Modifier.height(8.dp))

                // -------------------------------------------- 类型
                FilterRow("类型") {
                    item {
                        FilterChip(
                            label = "全部",
                            onClick = { vm.openGenre(null) },
                            selected = vm.filter.genre == null,
                        )
                    }
                    items(Genres.all, key = { it }) { genre ->
                        FilterChip(
                            label = genre,
                            onClick = { vm.openGenre(genre) },
                            selected = vm.filter.genre == genre,
                        )
                    }
                }

                // -------------------------------------------- 排序
                FilterRow("排序") {
                    items(SortOption.entries.toList(), key = { it.name }) { option ->
                        FilterChip(
                            label = option.label,
                            onClick = { vm.setSort(option) },
                            selected = vm.filter.sort == option,
                        )
                    }
                }

                // -------------------------------------------- 标签
                Row(
                    Modifier.fillMaxWidth().height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowLabel("标签")
                    if (vm.filter.tags.isEmpty()) {
                        Text(
                            text = "未选择标签（可多选）",
                            color = Palette.TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(vm.filter.tags, key = { it }) { tag ->
                                FilterChip(
                                    label = "#$tag",
                                    onClick = { vm.toggleTagFilter(tag) },
                                    selected = true,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ActionChip(
                        label = if (vm.filter.tags.isEmpty()) {
                            "选择标签"
                        } else {
                            "选择标签 (${vm.filter.tags.size})"
                        },
                        onClick = { pickerVisible = true },
                        focusRequester = pickerButtonFocus,
                        primary = vm.filter.tags.isEmpty(),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // -------------------------------------------- 结果
                BrowseResults(
                    state = vm.browseState,
                    loadingMore = vm.browseLoadingMore,
                    onLoadMore = { vm.browseMore() },
                    onOpen = { vm.openDetail(it) },
                    onRetry = { vm.applyFilter(vm.filter) },
                    emptyText = "没有符合条件的影片",
                    onResetFilter = { vm.clearFilter() },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }

        if (pickerVisible) {
            TagPickerOverlay(
                selected = vm.filter.tags,
                onToggle = { vm.toggleTagFilter(it) },
                onDone = { pickerVisible = false },
                onClearTags = { vm.applyFilter(vm.filter.copy(tags = emptyList())) },
                onCancel = { pickerVisible = false },
            )
        }
    }
}

/** 一行筛选：行首小标签 + 横向滚动的 chips */
@Composable
private fun FilterRow(
    label: String,
    content: LazyListScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
