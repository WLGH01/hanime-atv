package me.hanime.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.hanime.atv.ui.theme.Palette

// 960dp 逻辑宽度下最多 10 键一行：10*44 + 9*6 = 494dp，远小于屏幕宽度
private val KeyWidth = 44.dp
private val KeyHeight = 40.dp
private val KeyGap = 6.dp
private val KeyShape = RoundedCornerShape(8.dp)

private val letterRows = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "@"),
    listOf("z", "x", "c", "v", "b", "n", "m", ".", "-", "_"),
)

// 覆盖常用英文标点：密码、邮箱、Cookie 都会用到
private val symbolRows = listOf(
    listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
    listOf("[", "]", "{", "}", "-", "_", "=", "+", "~", "`"),
    listOf(";", ":", "'", "\"", ",", ".", "/", "\\", "|", "?"),
)

/**
 * 电视遥控器可用的屏幕键盘。TV 上没有实体键盘，搜索/登录都靠它输入。
 *
 * 增强点（向后兼容，SearchScreen 调用方式不变）：
 * - 大写切换：Shift 键把字母键显示并输入为大写；
 * - 符号页：`?123` / `ABC` 切换数字符号键盘，覆盖英文密码常用符号；
 * - 键位固定 44×40dp，10 键一行，960dp 宽度内排布安全。
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "完成",
    firstFocusRequester: FocusRequester? = null,
    initialUppercase: Boolean = false,
) {
    var uppercase by remember { mutableStateOf(initialUppercase) }
    var symbolPage by remember { mutableStateOf(false) }
    val rows = if (symbolPage) symbolRows else letterRows

    Column(
        modifier = modifier.widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(KeyGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEachIndexed { colIndex, key ->
                    val isLetter = !symbolPage && key.length == 1 && key[0].isLetter()
                    KeyButton(
                        label = if (isLetter && uppercase) key.uppercase() else key,
                        width = KeyWidth,
                        height = KeyHeight,
                        focusRequester = if (rowIndex == 0 && colIndex == 0) firstFocusRequester else null,
                        onClick = {
                            onKey(if (isLetter && uppercase) key.uppercase() else key)
                        },
                    )
                }
            }
        }

        if (symbolPage) {
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                KeyButton(label = "ABC", width = 74.dp, height = KeyHeight, onClick = { symbolPage = false })
                KeyButton(label = "空格", width = 150.dp, height = KeyHeight, onClick = { onKey(" ") })
                KeyButton(label = "删除", width = 74.dp, height = KeyHeight, onClick = onBackspace)
                KeyButton(label = doneLabel, width = 96.dp, height = KeyHeight, primary = true, onClick = onDone)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                KeyButton(
                    label = if (uppercase) "小写" else "大写",
                    width = 66.dp,
                    height = KeyHeight,
                    onClick = { uppercase = !uppercase },
                )
                KeyButton(label = "空格", width = 150.dp, height = KeyHeight, onClick = { onKey(" ") })
                KeyButton(label = "删除", width = 74.dp, height = KeyHeight, onClick = onBackspace)
                KeyButton(label = "?123", width = 74.dp, height = KeyHeight, onClick = { symbolPage = true })
                KeyButton(label = doneLabel, width = 96.dp, height = KeyHeight, primary = true, onClick = onDone)
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.width(width).height(height),
        shape = KeyShape,
        focusedScale = 1.06f,
        focusRequester = focusRequester,
    ) { _ ->
        val bg = if (primary) {
            Modifier.background(Palette.accentGradient, KeyShape)
        } else {
            Modifier.background(Palette.Surface, KeyShape)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (primary) Color.White else Palette.TextPrimary,
                fontSize = if (label.length > 1) 12.sp else 13.sp,
                maxLines = 1,
            )
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
