package me.hanime.atv.ui.screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.Loadable
import me.hanime.atv.ui.components.EmptyView
import me.hanime.atv.ui.components.ErrorView
import me.hanime.atv.ui.components.LoadingView
import me.hanime.atv.ui.components.SectionTitle
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.components.TvKeyboard
import me.hanime.atv.ui.components.TvSearchBox
import me.hanime.atv.ui.components.VideoCard
import me.hanime.atv.ui.theme.Palette

private val hotGenres = listOf(
    "裏番", "泡麵番", "Motion Anime", "3DCG", "2.5D",
    "2D動畫", "AI生成", "MMD", "Cosplay", "新番預告",
)

@Composable
fun SearchScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var keyboardVisible by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val keyboardFocus = remember { FocusRequester() }
    val searchBoxFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { searchBoxFocus.requestFocus() }
    }

    BackHandler(enabled = keyboardVisible) { keyboardVisible = false }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("搜索", Modifier.width(120.dp))
                TvSearchBox(
                    query = vm.searchQuery,
                    onClick = {
                        draft = vm.searchQuery
                        keyboardVisible = true
                    },
                    modifier = Modifier.weight(1f),
                    focusRequester = searchBoxFocus,
                )
            }

            Spacer(Modifier.height(18.dp))

            if (vm.searchState is Loadable.Idle) {
                // 推荐区
                SectionTitle("热门分类", Modifier.padding(bottom = 12.dp))
                FlowChips(hotGenres) { genre -> vm.search("", genre) }
                if (vm.searchHistory.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    SectionTitle("搜索历史", Modifier.padding(bottom = 12.dp))
                    FlowChips(vm.searchHistory) { kw -> vm.search(kw) }
                }
                Spacer(Modifier.height(30.dp))
                Text(
                    "使用遥控器选择搜索框，屏幕上会弹出键盘。",
                    color = Palette.TextTertiary,
                    fontSize = 13.sp,
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    when (val state = vm.searchState) {
                        is Loadable.Loading -> LoadingView(text = "正在搜索…")
                        is Loadable.Failure -> ErrorView(state.message, { vm.search(reset = true) })
                        is Loadable.Success -> if (state.data.items.isEmpty()) {
                            EmptyView("没有找到相关影片")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(268.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                contentPadding = PaddingValues(bottom = 60.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(state.data.items, key = { it.code }) { card ->
                                    VideoCard(card = card, onClick = { vm.openDetail(card.code) })
                                }
                                if (state.data.hasNext) {
                                    item {
                                        Box(Modifier.padding(top = 6.dp)) {
                                            TvButton(
                                                if (vm.searchLoadingMore) "加载中…" else "加载更多",
                                                { vm.searchMore() },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Loadable.Idle -> Unit
                    }
                }
            }
        }

        if (keyboardVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xE6080910)),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 60.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        draft.ifBlank { "输入关键词" },
                        color = if (draft.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .background(Palette.Surface, RoundedCornerShape(14.dp))
                            .padding(horizontal = 22.dp, vertical = 16.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    TvKeyboard(
                        onKey = { draft += it },
                        onBackspace = { draft = draft.dropLast(1) },
                        onDone = {
                            keyboardVisible = false
                            vm.onSearchInput(draft)
                            vm.search(draft)
                        },
                        doneLabel = "搜索",
                        firstFocusRequester = keyboardFocus,
                    )
                    Spacer(Modifier.height(18.dp))
                    TvButton(
                        "取消",
                        {
                            keyboardVisible = false
                            runCatching { searchBoxFocus.requestFocus() }
                        },
                    )
                    LaunchedEffect(Unit) {
                        delay(120)
                        runCatching { keyboardFocus.requestFocus() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth(0.9f)
            .verticalScroll(rememberScrollState()),
    ) {
        items.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                row.forEach { label ->
                    TvFocusable(
                        onClick = { onPick(label) },
                        shape = RoundedCornerShape(999.dp),
                    ) { focused ->
                        Text(
                            label,
                            color = if (focused) Color.White else Palette.TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .background(
                                    if (focused) Palette.SurfaceHigh else Palette.Surface,
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
