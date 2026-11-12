package com.example.foxy.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R
import com.example.foxy.ui.theme.VeilColors

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private const val CHARACTER_ASPECT = 550f / 800f

enum class AimTarget(val label: String, val yFraction: Float) {
    Neck("Cổ", 0.155f),
    Body("Thân", 0.34f),
    Foot("Chân", 0.68f),
}

data class BoostState(
    val pinEnabled: Boolean = true,
    val aimTarget: AimTarget = AimTarget.Neck,
)

/** Nội dung tab "Tăng tốc" (tia sét): khung nhân vật + đường kẻ ghim bên trái, chế độ ghim bên phải. */
@Composable
internal fun BoostContent(
    boost: BoostState,
    onChange: (BoostState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CharacterPinPreview(
            enabled = boost.pinEnabled,
            target = boost.aimTarget,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        PinModeCard(
            enabled = boost.pinEnabled,
            selected = boost.aimTarget,
            onEnabledChange = { onChange(boost.copy(pinEnabled = it)) },
            onSelect = { onChange(boost.copy(aimTarget = it)) },
            modifier = Modifier.width(236.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun CharacterPinPreview(enabled: Boolean, target: AimTarget, modifier: Modifier = Modifier) {
    val lineFraction by animateFloatAsState(
        targetValue = target.yFraction,
        animationSpec = tween(durationMillis = 180),
        label = "pinLineY",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) CardBg else Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.08f else 0.04f), RoundedCornerShape(18.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.character_aim),
            contentDescription = "Nhân vật",
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            colorFilter = if (enabled) null else GrayscaleFilter,
            modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.35f),
        )
        if (enabled) {
            Canvas(Modifier.fillMaxSize()) {
                val boxRatio = size.width / size.height.coerceAtLeast(1f)
                val drawH = if (boxRatio > CHARACTER_ASPECT) size.height else size.width / CHARACTER_ASPECT
                val topOffset = (size.height - drawH) / 2f
                val y = topOffset + drawH * lineFraction
                val marginX = (size.width * 0.14f).coerceAtLeast(16.dp.toPx())
                val startX = marginX
                val endX = size.width - marginX
                val midX = size.width / 2f
                val tickH = 6.dp.toPx()

                // Đường kẻ ngang chỉ đúng vị trí Cổ / Thân / Chân trên nhân vật.
                drawLine(
                    color = VeilColors.Accent.copy(alpha = 0.9f),
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                )
                drawLine(VeilColors.Accent, Offset(startX, y - tickH), Offset(startX, y + tickH), strokeWidth = 2.dp.toPx())
                drawLine(VeilColors.Accent, Offset(endX, y - tickH), Offset(endX, y + tickH), strokeWidth = 2.dp.toPx())
                drawCircle(Color(0xFF1C2A2D), radius = 5.5.dp.toPx(), center = Offset(midX, y))
                drawCircle(VeilColors.Accent, radius = 4.dp.toPx(), center = Offset(midX, y))
                drawCircle(VeilColors.Accent.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = Offset(midX, y), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PinModeCard(
    enabled: Boolean,
    selected: AimTarget,
    onEnabledChange: (Boolean) -> Unit,
    onSelect: (AimTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("CHẾ ĐỘ GHIM", fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (enabled) Color.White else Soft)
            PinSwitch(enabled)
        }
        AimTarget.entries.forEach { target ->
            val on = enabled && target == selected
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(shape)
                    .background(
                        when {
                            !enabled -> Color.White.copy(alpha = 0.03f)
                            on -> VeilColors.Accent.copy(alpha = 0.14f)
                            else -> Color.White.copy(alpha = 0.05f)
                        }
                    )
                    .border(1.5.dp, if (on) VeilColors.Accent.copy(alpha = 0.75f) else Color.Transparent, shape)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(target) }
                    .alpha(if (enabled) 1f else 0.4f)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(target.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(22.dp).clip(CircleShape).then(
                        if (on) Modifier.background(VeilColors.Accent)
                        else Modifier.border(1.5.dp, Color(0xFF5B6E72), CircleShape)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = VeilColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun PinSwitch(checked: Boolean) {
    Box(
        Modifier.size(34.dp, 20.dp).clip(RoundedCornerShape(10.dp))
            .background(if (checked) VeilColors.Accent else Color.White.copy(alpha = 0.18f)).padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(16.dp).background(Color.White, CircleShape)) }
}
