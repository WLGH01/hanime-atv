package me.hanime.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.hanime.atv.data.HanimeCard
import me.hanime.atv.ui.theme.Palette

@Composable
fun VideoCard(
    card: HanimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    progress: Float? = null,
    progressText: String? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.07f,
    ) { focused ->
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Surface),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(card.cover)
                        .crossfade(true)
                        .build(),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (card.duration != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(card.duration, color = Color.White, fontSize = 10.sp)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))),
                        ),
                )
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x55FFFFFF)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(Palette.Accent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.title,
                color = if (focused) Color.White else Palette.TextPrimary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            val sub = progressText ?: listOfNotNull(card.uploader, card.views).joinToString(" · ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = sub,
                    color = Palette.TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
