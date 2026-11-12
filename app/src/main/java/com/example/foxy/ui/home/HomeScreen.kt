package com.example.foxy.ui.home

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.ui.theme.VeilColors as C
import com.example.foxy.ui.theme.VeilTheme
import java.util.Locale

private val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Crown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 18f)
        lineTo(3f, 8f)
        lineTo(8.5f, 12.5f)
        lineTo(12f, 5.5f)
        lineTo(15.5f, 12.5f)
        lineTo(21f, 8f)
        lineTo(20f, 18f)
        close()
    }.build()
}

enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Trang chủ", Icons.Rounded.Home),
    Servers("Máy chủ", Icons.Rounded.Public),
    Premium("Premium", CrownIcon),
    Account("Tài khoản", Icons.Rounded.Person),
}

/** Điểm vào: gắn ViewModel vào màn hình Trang chủ. */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = viewModel(),
    onUpgradeClick: () -> Unit = {},
    onServerClick: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(
        state = state,
        onPowerClick = viewModel::onPowerClick,
        onUpgradeClick = onUpgradeClick,
        onServerClick = onServerClick,
        onTabSelected = onTabSelected,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPowerClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onServerClick: () -> Unit,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradientEnd = with(LocalDensity.current) { 460.dp.toPx() }
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.HeaderTint, C.Background), startY = 0f, endY = gradientEnd))
            .statusBarsPadding(),
    ) {
        Header(onUpgradeClick)
        StatusText(state, Modifier.padding(top = 14.dp))
        PowerButton(state, onPowerClick, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 8.dp))
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Rounded.ArrowDownward, "Tải xuống", mbps(state.downMbps.toFloat()), C.StatDownload, Modifier.weight(1f))
                    StatCard(Icons.Rounded.ArrowUpward, "Tải lên", mbps(state.upMbps.toFloat()), C.StatUpload, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                LocationCard(state, onServerClick)
                Spacer(Modifier.weight(1f))
                BottomNav(HomeTab.Home, onTabSelected)
            }
        }
    }
}

@Composable
private fun Header(onUpgrade: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(onClick = onUpgrade, shape = RoundedCornerShape(19.dp), color = Color.White, modifier = Modifier.height(38.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(CrownIcon, null, Modifier.size(16.dp), tint = C.Ink)
                Text("Nâng cấp", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            }
        }
    }
}

@Composable
private fun StatusText(state: HomeUiState, modifier: Modifier = Modifier) {
    val (title, sub) = when (state.status) {
        ConnectionStatus.Idle -> "Chưa kết nối" to "IP của bạn đang hiển thị"
        ConnectionStatus.Connecting -> "Đang kết nối…" to state.serverName
        ConnectionStatus.Connected -> state.serverName to "Đã kết nối · IP " + state.ipAddress
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Ink, textAlign = TextAlign.Center)
        Text(sub, fontSize = 13.sp, color = C.Muted)
    }
}

@Composable
private fun PowerButton(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val connected = state.status == ConnectionStatus.Connected
    val container by animateColorAsState(if (connected) C.Accent else Color.White, label = "powerBg")
    val content = if (connected) C.Ink else C.Accent
    Box(modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(210.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
        Box(Modifier.size(178.dp).background(Color.White.copy(alpha = 0.55f), CircleShape))
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            shadowElevation = 12.dp,
            modifier = Modifier.size(150.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                when (state.status) {
                    ConnectionStatus.Connecting -> {
                        CircularProgressIndicator(Modifier.size(56.dp), color = C.Accent, strokeWidth = 3.dp)
                        Text("Vui lòng đợi", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                    ConnectionStatus.Connected -> {
                        Icon(Icons.Rounded.PowerSettingsNew, "Ngắt kết nối", Modifier.size(40.dp), tint = content)
                        Text("DỪNG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = content)
                        Text(clock(state.elapsedSeconds), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    }
                    ConnectionStatus.Idle -> {
                        Icon(Icons.Rounded.PowerSettingsNew, "Kết nối", Modifier.size(46.dp), tint = content)
                        Text("BẬT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, bg: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp)) {
        Box(Modifier.size(30.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = C.AccentText)
        }
        Text(label, fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
        Text(value, fontWeight = FontWeight.Bold, color = C.Ink)
    }
}

@Composable
private fun LocationCard(state: HomeUiState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, C.Border.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountryFlag(state.serverCountryCode, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(state.serverName, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(state.serverHint, fontSize = 13.sp, color = C.Muted)
            }
            SignalBars(level = 3)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = C.Muted)
        }
    }
}

@Composable
internal fun SignalBars(level: Int) {
    Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(5.dp, 9.dp, 14.dp).forEachIndexed { i, h ->
            Box(Modifier.size(width = 3.dp, height = h).background(if (i < level) C.SignalOn else C.SignalOff, RoundedCornerShape(1.dp)))
        }
    }
}

/** Cờ vẽ bằng Canvas cho vài nước. Nước khác: thay bằng ảnh cờ thật (drawable/Coil). */
@Composable
fun CountryFlag(code: String, size: Dp) {
    val red = Color(0xFFC9504A); val blue = Color(0xFF3E5A8C)
    Canvas(Modifier.size(size).clip(CircleShape).border(1.dp, C.Border, CircleShape)) {
        val u = this.size.width / 36f
        fun rect(c: Color, x: Float, y: Float, w: Float, h: Float) =
            drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
        when (code) {
            "FF", "FFMAX" -> {
                rect(Color(0xFF1A1D24), 0f, 0f, 36f, 36f)
                // Ngọn lửa cam-vàng đặc trưng Free Fire
                val flame = Path().apply {
                    moveTo(23f * u, 6f * u)
                    lineTo(27.5f * u, 13f * u)
                    lineTo(24f * u, 15.5f * u)
                    lineTo(26f * u, 20f * u)
                    lineTo(20f * u, 14.5f * u)
                    close()
                }
                drawPath(flame, Color(0xFFFF7A00))
                // Hai chữ F sắc cạnh màu trắng
                rect(Color.White, 8.5f, 11f, 3.2f, 15f)
                rect(Color.White, 8.5f, 11f, 8.5f, 3.2f)
                rect(Color.White, 8.5f, 16.5f, 6.5f, 2.8f)
                rect(Color.White, 19.5f, 11f, 3.2f, 15f)
                rect(Color.White, 19.5f, 11f, 8.5f, 3.2f)
                rect(Color.White, 19.5f, 16.5f, 6.5f, 2.8f)
                if (code == "FFMAX") {
                    drawCircle(Color(0xFFFFB300), 3.2f * u, Offset(28.5f * u, 28f * u))
                }
            }
            "US" -> {
                rect(Color.White, 0f, 0f, 36f, 36f)
                listOf(0f, 9.2f, 18.4f, 27.6f).forEach { rect(red, 0f, it, 36f, 4.6f) }
                rect(blue, 0f, 0f, 18f, 19f)
            }
            "DE" -> { rect(Color(0xFF2B2B2B), 0f, 0f, 36f, 12f); rect(red, 0f, 12f, 36f, 12f); rect(Color(0xFFE8B84A), 0f, 24f, 36f, 12f) }
            "FR" -> { rect(blue, 0f, 0f, 12f, 36f); rect(Color.White, 12f, 0f, 12f, 36f); rect(red, 24f, 0f, 12f, 36f) }
            "JP" -> { rect(Color.White, 0f, 0f, 36f, 36f); drawCircle(red, 8f * u, Offset(18f * u, 18f * u)) }
            "SG" -> { rect(red, 0f, 0f, 36f, 18f); rect(Color.White, 0f, 18f, 36f, 18f); drawCircle(Color.White, 4f * u, Offset(10f * u, 9f * u)) }
            else -> rect(C.AccentTint, 0f, 0f, 36f, 36f)
        }
    }
}

@Composable
private fun BottomNav(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(C.NavBar).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HomeTab.entries.forEach { tab ->
            val on = tab == selected
            Row(
                Modifier
                    .height(44.dp).widthIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) C.Background else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = if (on) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Icon(tab.icon, tab.label, Modifier.size(20.dp), tint = if (on) C.Ink else C.NavInactive)
                if (on) Text(tab.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            }
        }
    }
}

private fun mbps(v: Float) =
    if (v == 0f) "0 Mb/s" else String.format(Locale.forLanguageTag("vi"), "%.1f Mb/s", v)

private fun clock(s: Long) = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable private fun PreviewDisconnected() = VeilTheme { HomeScreen(HomeUiState(), {}, {}, {}, {}) }

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable private fun PreviewConnecting() = VeilTheme { HomeScreen(HomeUiState(status = ConnectionStatus.Connecting), {}, {}, {}, {}) }

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable private fun PreviewConnected() = VeilTheme {
    HomeScreen(
        HomeUiState(status = ConnectionStatus.Connected, downMbps = 13.5, upMbps = 15.5, elapsedSeconds = 2720),
        {}, {}, {}, {},
    )
}
