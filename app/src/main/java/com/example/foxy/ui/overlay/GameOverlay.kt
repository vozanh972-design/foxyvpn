package com.example.foxy.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.ui.home.CountryFlag
import com.example.foxy.ui.home.SignalBars
import com.example.foxy.ui.theme.VeilColors
import com.example.foxy.ui.theme.VeilTheme
import java.util.Locale

/** Tỉ lệ thu nhỏ mặc định của bảng (700x350 dp * 0.80 = 560x280 dp). */
const val PANEL_SCALE = 0.80f

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)
private val HeroInk = Color(0xFF10282B)
private val HeroInkSoft = Color(0xFF12423B)

data class GameOverlayState(
    val serverName: String = "United States",
    val serverCountryCode: String = "US",
    val signalLevel: Int = 3,
    val pingMs: Int = 38,
    val pingHistory: List<Int> = listOf(41, 37, 45, 33, 39, 31, 36, 34, 38),
    val daysLeft: Int = 28,
    val downloadMbps: Float = 13.5f,
    val uploadMbps: Float = 15.5f,
    val gamePriority: Boolean = true,
    val languageLabel: String = "Tiếng Việt",
)

enum class OverlayTab(val icon: ImageVector, val label: String) {
    Home(Icons.Rounded.Home, "Trang chủ"),
    Boost(Icons.Rounded.Bolt, "Tăng tốc"),
    Servers(Icons.Rounded.Public, "Máy chủ"),
    Security(Icons.Rounded.VerifiedUser, "Bảo mật"),
    Settings(Icons.Rounded.Settings, "Cài đặt"),
}

/**
 * Bảng điều khiển VPN hiện trên game.
 * - expanded = false: chỉ hiện chip độ trễ nhỏ (chạm để mở, kéo để di chuyển).
 * - expanded = true: hiện bảng đầy đủ 700x350 dp theo tỉ lệ panelScale.
 */
@Composable
fun GameOverlay(
    state: GameOverlayState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismissOverlay: () -> Unit = {},
    onPriorityChange: (Boolean) -> Unit,
    onServerClick: () -> Unit,
    onLanguageClick: () -> Unit,
    tab: OverlayTab = OverlayTab.Home,
    onTabSelected: (OverlayTab) -> Unit = {},
    boost: BoostState = BoostState(),
    onBoostChange: (BoostState) -> Unit = {},
    onMeasure: () -> Unit = {},
    modifier: Modifier = Modifier,
    panelScale: Float = PANEL_SCALE,
) {
    if (!expanded) {
        PingChip(state.pingMs, { onExpandedChange(true) }, modifier)
        return
    }
    val d = LocalDensity.current
    // Thu nhỏ cả dp lẫn sp theo panelScale và giữ kích thước chuẩn 700x350 để tránh bị WindowManager bóp ngang.
    CompositionLocalProvider(LocalDensity provides Density(d.density * panelScale, d.fontScale)) {
        Row(
            modifier
                .requiredSize(700.dp, 350.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1C2A2D).copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Rail(tab, onTabSelected)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(
                    state = state,
                    onCollapse = { onExpandedChange(false) },
                    onDismiss = onDismissOverlay,
                )
                val content = Modifier.weight(1f).fillMaxWidth()
                when (tab) {
                    OverlayTab.Home -> HomeContent(state, onDisconnect, onPriorityChange, onServerClick, onLanguageClick, content)
                    OverlayTab.Boost -> BoostContent(boost, onBoostChange, onMeasure, content)
                    else -> Box(content, contentAlignment = Alignment.Center) {
                        Text("${tab.label} · sắp có", color = Soft, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: GameOverlayState,
    onDisconnect: () -> Unit,
    onPriorityChange: (Boolean) -> Unit,
    onServerClick: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PingHero(state, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriorityCard(state.gamePriority, onPriorityChange, Modifier.weight(1f))
                LanguageCard(state.languageLabel, onLanguageClick, Modifier.weight(1f))
            }
        }
        Column(Modifier.width(236.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ServerCard(state, onServerClick)
            StatsCard(state, Modifier.weight(1f))
            DisconnectButton(onDisconnect)
        }
    }
}

@Composable
fun PingChip(pingMs: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C2A2D).copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Mở bảng điều khiển", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(VeilColors.Accent, CircleShape))
        Text("$pingMs ms", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Rail(selected: OverlayTab, onSelect: (OverlayTab) -> Unit) {
    Column(
        Modifier.width(52.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OverlayTab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) VeilColors.Accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(t.icon, t.label, Modifier.size(22.dp), tint = if (on) VeilColors.Accent else Soft)
            }
        }
    }
}

@Composable
private fun TopBar(state: GameOverlayState, onCollapse: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(VeilColors.Accent, CircleShape))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Đã kết nối") }
                        append(" · " + state.serverName)
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onCollapse),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, "Thu gọn thành chip", Modifier.size(20.dp), tint = Color.White) }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Tắt menu", Modifier.size(20.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun PingHero(state: GameOverlayState, modifier: Modifier = Modifier) {
    val quality = when {
        state.pingMs < 60 -> "Mạng ổn định"
        state.pingMs < 120 -> "Mạng trung bình"
        else -> "Mạng chậm"
    }
    Row(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF46C4AE), Color(0xFF2C8FA6))))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text("ĐỘ TRỄ HIỆN TẠI", color = HeroInkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)) { append(state.pingMs.toString()) }
                    withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)) { append(" ms") }
                },
                color = HeroInk, lineHeight = 60.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                quality, color = HeroInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Sparkline(state.pingHistory, Modifier.size(130.dp, 50.dp))
    }
}

@Composable
private fun Sparkline(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).coerceAtLeast(1)
        val pad = 4.dp.toPx()
        val w = size.width - pad * 2; val h = size.height - pad * 2
        val pts = values.mapIndexed { i, v -> Offset(pad + w * i / (values.size - 1), pad + h * (1f - (v - lo).toFloat() / span)) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, HeroInkSoft.copy(alpha = 0.75f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(HeroInkSoft, 4.dp.toPx(), pts.last())
    }
}

@Composable
private fun PriorityCard(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .border(BorderStroke(1.5.dp, if (checked) VeilColors.Accent.copy(alpha = 0.7f) else Color.Transparent), RoundedCornerShape(18.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).then(if (checked) Modifier.background(VeilColors.Accent) else Modifier.border(2.dp, Color(0xFF5B6E72), CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = VeilColors.Ink) }
        Column {
            Text("Ưu tiên gói game", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Giảm trễ khi giao tranh", color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LanguageCard(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Public, null, Modifier.size(16.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("Ngôn ngữ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = Soft)
    }
}

@Composable
private fun ServerCard(state: GameOverlayState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CountryFlag(state.serverCountryCode, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(state.serverName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Đổi máy chủ", color = Soft, fontSize = 12.sp)
        }
        SignalBars(level = state.signalLevel)
    }
}

@Composable
private fun StatsCard(state: GameOverlayState, modifier: Modifier = Modifier) {
    fun mbps(v: Float) = String.format(Locale.forLanguageTag("vi"), "%.1f Mb/s", v)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        StatRow("Còn lại", "${state.daysLeft} ngày")
        StatRow("Tốc độ tải xuống", mbps(state.downloadMbps))
        StatRow("Tốc độ tải lên", mbps(state.uploadMbps))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Soft, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DisconnectButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp), tint = Color.White)
        Text("Ngắt kết nối", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun PreviewExpanded() = VeilTheme {
    Box(Modifier.fillMaxSize().background(Color(0xFF101619)), contentAlignment = Alignment.Center) {
        GameOverlay(GameOverlayState(), true, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 300, heightDp = 100)
@Composable
private fun PreviewCollapsed() = VeilTheme {
    Box(Modifier.fillMaxSize().background(Color(0xFF101619)).padding(14.dp)) {
        GameOverlay(GameOverlayState(), false, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun PreviewBoost() = VeilTheme {
    Box(Modifier.fillMaxSize().background(Color(0xFF101619)), contentAlignment = Alignment.Center) {
        GameOverlay(GameOverlayState(), true, {}, {}, {}, {}, {}, {}, tab = OverlayTab.Boost)
    }
}
