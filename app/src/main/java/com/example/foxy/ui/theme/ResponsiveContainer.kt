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

/**
 * ResponsiveContainer:
 * Cơ chế tự động co giãn tỷ lệ (Dynamic Responsive Scaling):
 * - Mốc thiết kế chuẩn: 390dp (theo đúng chuẩn mockup HTML max-width: 390px).
 * - Màn hình nhỏ (< 390dp): tự động co tỷ lệ (scale down) để nội dung không bao giờ bị tràn hay đè nút.
 * - Màn hình lớn (390dp - 440dp): tự động giãn tỷ lệ theo tỷ lệ chuẩn của thiết bị.
 * - Tablet / màn hình gập (> 440dp): khóa kích thước tối đa 440dp và canh giữa sang trọng, tránh vỡ bố cục.
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
        val targetWidthDp = 390f
        val screenWidthDp = maxWidth.value

        // Tính toán tỷ lệ co giãn thông minh
        val scale = remember(screenWidthDp) {
            if (screenWidthDp <= 440f) {
                (screenWidthDp / targetWidthDp).coerceIn(0.82f, 1.15f)
            } else {
                1.05f
            }
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
