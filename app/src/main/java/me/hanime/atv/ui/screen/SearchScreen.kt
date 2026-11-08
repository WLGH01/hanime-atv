package me.hanime.atv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.hanime.atv.data.Genres
import me.hanime.atv.data.SortOption
import me.hanime.atv.data.TagCatalog
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.Screen
import me.hanime.atv.ui.components.ActionChip
import me.hanime.atv.ui.components.BrowseResults
import me.hanime.atv.ui.components.FilterChip
import me.hanime.atv.ui.components.MetaPill
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.components.TvKeyboard
import me.hanime.atv.ui.theme.Palette

/**
 * 搜索页：搜索框 + 屏幕键盘 + 热门入口 / 搜索历史 + 结果网格。
 *
 * 电视上没有实体键盘，所有输入都走 [TvKeyboard] 覆盖层（自绘，不用 TextField）。
 */
@Composable
fun SearchScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var keyboardVisible by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val searchBoxFocus = remember { FocusRequester() }
    val keyboardFocus = remember { FocusRequester() }

    // 只有"按关键词搜索过"才展示结果区；分类页的浏览结果不应串到搜索页
    val showResults = vm.browseState !is Loadable.Idle && vm.filter.query.isNotBlank()

    fun submit(text: String) {
        val keyword = text.trim()
        keyboardVisible = false
        if (keyword.isBlank()) {
            vm.showToast("请输入关键词")
            return
        }
        // 关键词搜索保留类型 / 排序，但清空已选标签，避免结果为空
        vm.onSearchInput(keyword)
        vm.applyFilter(vm.filter.copy(query = keyword, tags = emptyList()))
    }

    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) {
            delay(120)
            runCatching { keyboardFocus.requestFocus() }
        } else {
            delay(80)
            runCatching { searchBoxFocus.requestFocus() }
        }
    }

    BackHandler(enabled = keyboardVisible) { keyboardVisible = false }

    Box(modifier.fillMaxSize()) {
        // 键盘覆盖层打开时不再组合底层内容，避免遥控器焦点逃到被遮住的 chip 上
        if (!keyboardVisible) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("搜索")
                    Spacer(Modifier.width(12.dp))
                    SearchField(
                        query = vm.searchQuery,
                        onClick = {
                            draft = vm.searchQuery
                            keyboardVisible = true
                        },
                        modifier = Modifier.weight(1f),
                        focusRequester = searchBoxFocus,
                    )
                    Spacer(Modifier.width(10.dp))
                    TvButton(
                        text = "搜索",
                        onClick = {
                            if (vm.searchQuery.isNotBlank()) {
                                submit(vm.searchQuery)
                            } else {
                                draft = vm.searchQuery
                                keyboardVisible = true
                            }
                        },
                        primary = true,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (showResults) {
                    ResultsHeader(vm)
                    Spacer(Modifier.height(8.dp))
                    BrowseResults(
                        state = vm.browseState,
                        loadingMore = vm.browseLoadingMore,
                        onLoadMore = { vm.browseMore() },
                        onOpen = { vm.openDetail(it) },
                        onRetry = { vm.applyFilter(vm.filter) },
                        emptyText = "没有找到相关影片",
                        onResetFilter = { vm.clearFilter() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    IdleSuggestions(
                        vm = vm,
                        onPickKeyword = { submit(it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }

        if (keyboardVisible) {
            KeyboardOverlay(
                draft = draft,
                onKey = { draft += it },
                onBackspace = { draft = draft.dropLast(1) },
                onClear = { draft = "" },
                onDone = { submit(draft) },
                onClose = { keyboardVisible = false },
                keyboardFocus = keyboardFocus,
            )
        }
    }
}

/** 自绘搜索框（Material TextField 在电视上会被系统主题放大、且焦点不友好） */
@Composable
private fun SearchField(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        focusRequester = focusRequester,
        shape = shape,
        focusedScale = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(Palette.Surface, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) Palette.Accent else Palette.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = query.ifBlank { "搜索影片、作者或标签" },
                color = if (query.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 结果区上方的当前筛选摘要 + 更多筛选入口 */
@Composable
private fun ResultsHeader(vm: AppViewModel) {
    val chips = buildList {
        if (vm.filter.query.isNotBlank()) add("关键词：${vm.filter.query}")
        vm.filter.genre?.takeIf { it.isNotBlank() }?.let { add("类型：$it") }
        if (vm.filter.sort != SortOption.LatestUpload) add("排序：${vm.filter.sort.label}")
        vm.filter.tags.forEach { add("#$it") }
    }
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (chips.isEmpty()) {
            Text(
                text = "全部影片",
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
                items(chips) { label -> MetaPill(text = label) }
            }
        }
        Spacer(Modifier.width(10.dp))
        ActionChip("更多筛选", onClick = { vm.navigateToTopLevel(Screen.Discover) })
    }
}

/** 未搜索时的推荐入口：热门类型、热门标签、搜索历史 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleSuggestions(
    vm: AppViewModel,
    onPickKeyword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        GroupTitle("热门类型")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Genres.all.forEach { genre ->
                FilterChip(label = genre, onClick = { vm.openGenre(genre) })
            }
        }

        Spacer(Modifier.height(14.dp))
        GroupTitle("热门标签")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagCatalog.popular.forEach { tag ->
                FilterChip(label = tag, onClick = { vm.openTag(tag) })
            }
        }

        if (vm.searchHistory.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GroupTitle("搜索历史", Modifier.weight(1f))
                ActionChip("清空", onClick = { vm.clearSearchHistory() })
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.searchHistory.forEach { keyword ->
                    FilterChip(label = keyword, onClick = { onPickKeyword(keyword) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "选中搜索框可呼出屏幕键盘，输入完成后按“搜索”。",
            color = Palette.TextTertiary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = 8.dp),
        color = Palette.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 屏幕键盘覆盖层：整体高度约 360dp，稳稳落在 540dp 内容区里；取消按钮在右上角 */
@Composable
private fun KeyboardOverlay(
    draft: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    keyboardFocus: FocusRequester,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2090910)),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(0.8f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("输入关键词", color = Palette.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                ActionChip("清空", onClick = onClear)
                Spacer(Modifier.width(8.dp))
                ActionChip("取消", onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = draft.ifBlank { "尚未输入关键词" },
                    color = if (draft.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            TvKeyboard(
                onKey = onKey,
                onBackspace = onBackspace,
                onDone = onDone,
                doneLabel = "搜索",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                firstFocusRequester = keyboardFocus,
            )
        }
    }
}
