package me.hanime.atv.ui.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 纯本地二维码：用 zxing 把文本编码成 Bitmap 后交给 Compose 显示。
 *
 * 电视上只用于展示手机登录地址，不依赖网络/在线二维码服务；
 * 生成失败（内容非法、内存不足等）时降级显示文字，不抛异常中断登录流程。
 */
@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    quietZone: Int = 1,
) {
    val bitmap: ImageBitmap? = remember(content, size, quietZone) {
        if (content.isBlank()) {
            null
        } else {
            runCatching { buildQrBitmap(content, size, quietZone).asImageBitmap() }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "手机登录二维码",
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("二维码生成失败", color = Color(0xFF444444), fontSize = 13.sp)
        }
    }
}

/** 编码成 ARGB_8888 位图；白底黑块，quietZone 控制四边留白（单位：模块）。 */
private fun buildQrBitmap(content: String, size: Dp, quietZone: Int): Bitmap {
    // dp 尺寸只是逻辑尺寸，实际位图按 3 倍出图，保证 1080p 电视上不发虚
    val target = size.value.toInt().coerceAtLeast(160) * 3
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to quietZone,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, target, target, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    var index = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[index++] = if (matrix.get(x, y)) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
