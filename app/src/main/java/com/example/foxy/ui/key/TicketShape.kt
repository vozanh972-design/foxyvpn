package com.example.foxy.ui.key

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Hình tấm vé: hình chữ nhật bo góc, hai bên có khuyết tròn ở độ cao [notchCenterYPx].
 */
class TicketShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
    private val notchCenterYPx: Float,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val r = with(density) { notchRadius.toPx() }

        val card = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        }
        val holes = Path().apply {
            addOval(Rect(Offset(-r, notchCenterYPx - r), Size(2 * r, 2 * r)))
            addOval(Rect(Offset(size.width - r, notchCenterYPx - r), Size(2 * r, 2 * r)))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, card, holes))
    }
}
