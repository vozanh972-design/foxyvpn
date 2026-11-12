package com.example.foxy.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bolt
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
import com.example.foxy.ui.home.CountryFlag
import com.example.foxy.ui.home.SignalBars
import com.example.foxy.ui.theme.VeilColors

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)

enum class BoostMode(val title: String, val desc: String, val icon: ImageVector) {
    Balanced("Cân bằng", "Tốc độ và pin vừa phải", Icons.Rounded.Tune),
    Game("Game", "Ưu tiên gói game", Icons.Rounded.SportsEsports),
    Saver("Tiết kiệm pin", "Giảm tải khi chơi lâu", Icons.Rounded.BatteryFull),
}

data class ServerPing(val name: String, val countryCode: String, val pingMs: Int)

data class BoostState(
    val mode: BoostMode = BoostMode.Game,
    val autoPickServer: Boolean = true,
    val keepOnNetworkSwitch: Boolean = true,
    val measuring: Boolean = false,
    val servers: List<ServerPing> = listOf(
        ServerPing("United States", "US", 38),
        ServerPing("Singapore", "SG", 64),
        ServerPing("Japan", "JP", 88),
        ServerPing("Germany", "DE", 142),
    ),
)

/** Nội dung tab "Tăng tốc" (tia sét) trong bảng trong game. */
@Composable
internal fun BoostContent(
    boost: BoostState,
    onChange: (BoostState) -> Unit,
    onMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CHẾ ĐỘ TĂNG TỐC", fontSize = 11.sp, letterSpacing = 0.8.sp, color = Soft)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoostMode.entries.forEach { m ->
                    ModeCard(m, m == boost.mode, { onChange(boost.copy(mode = m)) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchCard("Tự chọn máy chủ", "Khi độ trễ tăng cao", boost.autoPickServer, { onChange(boost.copy(autoPickServer = it)) }, Modifier.weight(1f))
                SwitchCard("Giữ kết nối", "Khi đổi Wi-Fi sang 4G", boost.keepOnNetworkSwitch, { onChange(boost.copy(keepOnNetworkSwitch = it)) }, Modifier.weight(1f))
            }
        }
        Column(Modifier.width(236.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PingList(boost.servers, Modifier.weight(1f).fillMaxWidth())
            MeasureButton(boost.measuring, onMeasure)
        }
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(34.dp).background(if (selected) VeilColors.Accent else Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(mode.icon, null, Modifier.size(18.dp), tint = if (selected) VeilColors.Ink else Color.White) }
        Spacer(Modifier.height(2.dp))
        Text(mode.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mode.desc, color = Soft, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchCard(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PillSwitch(checked)
    }
}

@Composable
private fun PillSwitch(checked: Boolean) {
    Box(
        Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp))
            .background(if (checked) VeilColors.Accent else Color.White.copy(alpha = 0.18f)).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(20.dp).background(Color.White, CircleShape)) }
}

@Composable
private fun PingList(servers: List<ServerPing>, modifier: Modifier = Modifier) {
    val best = servers.minOfOrNull { it.pingMs }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text("ĐỘ TRỄ MÁY CHỦ", fontSize = 11.sp, letterSpacing = 0.8.sp, color = Soft, modifier = Modifier.padding(bottom = 2.dp))
        servers.forEach { PingRow(it, it.pingMs == best, Modifier.weight(1f)) }
    }
}

@Composable
private fun PingRow(server: ServerPing, best: Boolean, modifier: Modifier = Modifier) {
    val level = when { server.pingMs <= 60 -> 3; server.pingMs <= 100 -> 2; else -> 1 }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CountryFlag(server.countryCode, 28.dp)
        Column(Modifier.weight(1f)) {
            Text(server.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (best) Text("Tốt nhất", color = VeilColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("${server.pingMs} ms", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SignalBars(level)
    }
}

@Composable
private fun MeasureButton(measuring: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
            .background(VeilColors.Accent.copy(alpha = if (measuring) 0.6f else 1f))
            .clickable(enabled = !measuring, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp), tint = VeilColors.Ink)
        Text(if (measuring) "Đang đo…" else "Đo lại độ trễ", color = VeilColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}
