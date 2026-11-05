package me.hanime.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.hanime.atv.ui.theme.Palette

/**
 * 电视遥控器可用的屏幕键盘。TV 上没有实体键盘，搜索/登录都靠它输入。
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "完成",
    firstFocusRequester: FocusRequester? = null,
) {
    val rows = remember {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "@"),
            listOf("z", "x", "c", "v", "b", "n", "m", ".", "-", "_"),
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, key ->
                    KeyButton(
                        label = key,
                        width = 52.dp,
                        height = 46.dp,
                        focusRequester = if (rowIndex == 0 && colIndex == 0) firstFocusRequester else null,
                        onClick = { onKey(key) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(label = "空格", width = 170.dp, height = 46.dp, onClick = { onKey(" ") })
            KeyButton(label = "删除", width = 110.dp, height = 46.dp, onClick = onBackspace)
            KeyButton(label = doneLabel, width = 150.dp, height = 46.dp, primary = true, onClick = onDone)
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.width(width).height(height),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.12f,
        focusRequester = focusRequester,
    ) { _ ->
        val bg = if (primary) {
            Modifier.background(Palette.accentGradient, RoundedCornerShape(10.dp))
        } else {
            Modifier.background(Palette.Surface, RoundedCornerShape(10.dp))
        }
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = if (label.length > 1) 12.sp else 15.sp)
        }
    }
}

/** 搜索框：电视上不能直接打字，点击后弹出屏幕键盘 */
@Composable
fun TvSearchBox(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索影片、作者或标签",
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.02f,
        focusRequester = focusRequester,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) Palette.Accent else Palette.TextTertiary,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                query.ifBlank { placeholder },
                color = if (query.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 17.sp,
            )
        }
    }
}

