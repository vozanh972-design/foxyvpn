package com.example.foxy.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.ui.theme.VeilColors

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)

/**
 * Toàn bộ tính năng network / freeze port từ Pigboy Proxy 3.3.10.
 */
data class BoostState(
    val manualFreeze: Boolean = false,
    val autoFreeze: Boolean = false,
    val aimLag: Boolean = false,
    val scopeFreeze: Boolean = false,
    val ghost: Boolean = false,
    val under: Boolean = false,
    val wall: Boolean = false,
    val magnet: Boolean = false,
    val teleport: Boolean = false,
    val speed: Int = 3,
)

/** Nội dung tab "Tăng tốc" — thay thế Chế độ ghim bằng bộ Features của Pigboy. */
@Composable
internal fun BoostContent(
    boost: BoostState,
    onChange: (BoostState) -> Unit,
    onTeleportTrigger: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SectionHeader("FREEZE & LAG")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FeatureToggleCard(
                title = "Freeze",
                subtitle = "Đóng băng thủ công",
                checked = boost.manualFreeze,
                onToggle = { onChange(boost.copy(manualFreeze = it)) },
                modifier = Modifier.weight(1f),
            )
            FeatureToggleCard(
                title = "Auto Freeze",
                subtitle = "Đóng băng khi không bắn",
                checked = boost.autoFreeze,
                onToggle = { onChange(boost.copy(autoFreeze = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FeatureToggleCard(
                title = "Aim Lag",
                subtitle = "Khựng khi mở ngắm",
                checked = boost.aimLag,
                onToggle = { onChange(boost.copy(aimLag = it)) },
                modifier = Modifier.weight(1f),
            )
            FeatureToggleCard(
                title = "Scope Freeze",
                subtitle = "Đóng băng lúc ngắm",
                checked = boost.scopeFreeze,
                onToggle = { onChange(boost.copy(scopeFreeze = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader("NETWORK FEATURES")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FeatureToggleCard(
                title = "Ghost",
                subtitle = "Giữ vị trí tàng hình",
                checked = boost.ghost,
                onToggle = { onChange(boost.copy(ghost = it)) },
                modifier = Modifier.weight(1f),
            )
            FeatureToggleCard(
                title = "Under",
                subtitle = "Chui đất (hạ trục Y)",
                checked = boost.under,
                onToggle = { onChange(boost.copy(under = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FeatureToggleCard(
                title = "Wall",
                subtitle = "Xuyên tường",
                checked = boost.wall,
                onToggle = { onChange(boost.copy(wall = it)) },
                modifier = Modifier.weight(1f),
            )
            FeatureToggleCard(
                title = "Magnet",
                subtitle = "Hút đạn khi bắn",
                checked = boost.magnet,
                onToggle = { onChange(boost.copy(magnet = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        FeatureToggleCard(
            title = "Teleport",
            subtitle = "Dịch chuyển theo hướng nhìn (${boost.speed * 5}m)",
            checked = boost.teleport,
            onToggle = { onChange(boost.copy(teleport = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (boost.teleport) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tốc độ / Khoảng cách", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${boost.speed} (${boost.speed * 5}m)", color = VeilColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VeilColors.Accent)
                                .clickable(role = Role.Button, onClick = onTeleportTrigger)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Dịch chuyển", color = VeilColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Slider(
                    value = boost.speed.toFloat(),
                    onValueChange = { onChange(boost.copy(speed = it.toInt().coerceIn(1, 10))) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = VeilColors.Accent,
                        activeTrackColor = VeilColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Bold,
        color = Soft,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
private fun FeatureToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) VeilColors.Accent.copy(alpha = 0.14f) else CardBg)
            .border(1.dp, if (checked) VeilColors.Accent.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Soft, fontSize = 10.sp)
        }
        Box(
            Modifier.size(34.dp, 20.dp).clip(RoundedCornerShape(10.dp))
                .background(if (checked) VeilColors.Accent else Color.White.copy(alpha = 0.18f)).padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(16.dp).background(Color.White, CircleShape)) }
    }
}
