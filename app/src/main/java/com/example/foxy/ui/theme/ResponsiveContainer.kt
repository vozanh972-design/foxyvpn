package com.example.foxy.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * ResponsiveContainer (Phiên bản cao cấp tự động tính toán kích thước):
 *
 * Thuật toán Aspect-Ratio Scaling (Tương tự Game Engine & Super Apps):
 * - Mốc thiết kế chuẩn: 390dp x 800dp (Chuẩn iPhone/Android quốc tế).
 * - Tự động đo cả chiều rộng (W) và chiều cao (H) của thiết bị thực tế.
 * - Tỷ lệ scale = min(W / 390, H / 800).
 * - Kết quả:
 *   + Máy màn hình nhỏ/ngắn (320x640): Tự động co nhỏ toàn diện (0.78x - 0.85x), không tràn viền, không mất nút.
 *   + Máy màn hình chuẩn/to (390x844, 412x915): Tự động giãn nở cân đối sắc nét (1.0x - 1.15x).
 *   + Tablet / Màn gập: Cố định tỷ lệ khung nhìn tối ưu, canh giữa trang nhã.
 */
@Composable
fun ResponsiveContainer(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0620)),
        contentAlignment = Alignment.Center,
    ) {
        val currentDensity = LocalDensity.current
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value

        // Mốc chuẩn thiết kế từ HTML mockup
        val designWidth = 390f
        val designHeight = 800f

        val scale = remember(widthDp, heightDp) {
            val scaleW = widthDp / designWidth
            val scaleH = heightDp / designHeight

            // Chọn tỷ lệ tối ưu để cả chiều dọc và ngang đều nằm trọn trong màn hình
            val fitScale = min(scaleW, scaleH)

            // Giới hạn trong khoảng an toàn (0.75x cho máy siêu bé đến 1.2x cho máy cực to)
            fitScale.coerceIn(0.75f, 1.20f)
        }

        val scaledDensity = remember(currentDensity, scale) {
            Density(
                density = currentDensity.density * scale,
                fontScale = currentDensity.fontScale * scale,
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxHeight(),
            ) {
                content()
            }
        }
    }
}
