package com.example.foxy.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.ui.theme.VeilColors

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)

enum class BoostMode(val title: String, val desc: String, val icon: ImageVector) {
    Balanced("Cân bằng", "Tốc độ và pin vừa phải", Icons.Rounded.Tune),
    Game("Game", "Ưu tiên gói game", Icons.Rounded.SportsEsports),
    Saver("Tiết kiệm pin", "Giảm tải khi chơi lâu", Icons.Rounded.BatteryFull),
}

enum class AimTarget(val label: String) {
    Neck("Cổ"),
    Body("Thân"),
    Foot("Chân"),
}

data class BoostState(
    val mode: BoostMode = BoostMode.Game,
    val autoPickServer: Boolean = true,
    val keepOnNetworkSwitch: Boolean = true,
    val aimTarget: AimTarget = AimTarget.Neck,
)

/** Nội dung tab "Tăng tốc" (tia sét) trong bảng trong game. */
@Composable
internal fun BoostContent(
    boost: BoostState,
    onChange: (BoostState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CHẾ ĐỘ TĂNG TỐC", fontSize = 11.sp, letterSpacing = 0.8.sp, color = Soft)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoostMode.entries.forEach { m ->
                    ModeCard(m, m == boost.mode, { onChange(boost.copy(mode = m)) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Row(Modifier.fillMaxWidth().height(66.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchCard("Tự chọn máy chủ", "Khi độ trễ tăng cao", boost.autoPickServer, { onChange(boost.copy(autoPickServer = it)) }, Modifier.weight(1f))
                SwitchCard("Giữ kết nối", "Khi đổi Wi-Fi sang 4G", boost.keepOnNetworkSwitch, { onChange(boost.copy(keepOnNetworkSwitch = it)) }, Modifier.weight(1f))
            }
        }
        AimTargetCard(
            selected = boost.aimTarget,
            onSelect = { onChange(boost.copy(aimTarget = it)) },
            modifier = Modifier.width(236.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun ModeCard(mode: BoostMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) VeilColors.Accent.copy(alpha = 0.12f) else CardBg)
            .border(1.5.dp, if (selected) VeilColors.Accent.copy(alpha = 0.7f) else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(32.dp).background(if (selected) VeilColors.Accent else Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(mode.icon, null, Modifier.size(18.dp), tint = if (selected) VeilColors.Ink else Color.White) }
        Spacer(Modifier.height(1.dp))
        Text(mode.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mode.desc, color = Soft, fontSize = 10.5.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchCard(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, color = Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PillSwitch(checked)
    }
}

@Composable
private fun PillSwitch(checked: Boolean) {
    Box(
        Modifier.size(38.dp, 22.dp).clip(RoundedCornerShape(11.dp))
            .background(if (checked) VeilColors.Accent else Color.White.copy(alpha = 0.18f)).padding(2.5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(17.dp).background(Color.White, CircleShape)) }
}

@Composable
private fun AimTargetCard(selected: AimTarget, onSelect: (AimTarget) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VỊ TRÍ MỤC TIÊU", fontSize = 11.sp, letterSpacing = 0.8.sp, color = Soft)
        AimTarget.entries.forEach { target ->
            val on = target == selected
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(shape)
                    .background(if (on) VeilColors.Accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                    .border(1.5.dp, if (on) VeilColors.Accent.copy(alpha = 0.75f) else Color.Transparent, shape)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(target) }
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
