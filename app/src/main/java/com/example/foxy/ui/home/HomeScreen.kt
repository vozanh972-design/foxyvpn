package com.example.foxy.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.R
import com.example.foxy.ui.theme.VeilColors as C
import com.example.foxy.ui.theme.VeilTheme
import java.util.Locale

// ---------- Màu chuẩn từ bản mẫu VpnHomeScreen ----------
private val BgPaper = Color(0xFFFBFBFA)
private val Ink = Color(0xFF1C1F1D)
private val Ink2 = Color(0xFF6B6F6C)
private val Ink3 = Color(0xFFA2A7A3)
private val LineSoft = Color(0xFFE0D8C7)
private val Teal = Color(0xFF15685A)
private val TealSoft = Color(0xFFDCEBE6)
private val TrackOff = Color(0xFFE7E4DA)

private const val RealIp = "113.161.44.20"

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
    onMenuClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onServerClick: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
    showBottomNav: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(
        state = state,
        onPowerClick = viewModel::onPowerClick,
        onMenuClick = onMenuClick,
        onUpgradeClick = onUpgradeClick,
        onServerClick = onServerClick,
        onTabSelected = onTabSelected,
        showBottomNav = showBottomNav,
    )
}

/**
 * Giao diện màn hình chính VPN theo phong cách VpnHomeScreen tối giản hiện đại.
 * Toàn bộ logic VPN và Menu nổi ngang được giữ nguyên 100%.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPowerClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onServerClick: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
    showBottomNav: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isOn = state.status == ConnectionStatus.Connected
    val isBusy = state.status == ConnectionStatus.Connecting

    val statusLabel = when {
        isBusy -> "Đang kết nối"
        isOn -> state.serverName
        else -> "Đang tắt"
    }
    val caption = when {
        isBusy -> "Đang kết nối…"
        isOn -> "Chạm để tắt"
        else -> "Chạm để bật"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPaper)
            .statusBarsPadding(),
    ) {
        // Thanh trên: tên app và nút menu 3 gạch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 22.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Foxy",
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(1.6.dp)
                                .background(Ink),
                        )
                    }
                }
            }
        }

        // Trạng thái kết nối
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp)) {
            Text(text = statusLabel, color = Ink2, fontSize = 14.sp)
        }

        // Công tắc viên thuốc nằm ngang, căn giữa (PillSwitch)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillSwitch(
                on = isOn,
                enabled = !isBusy,
                onClick = onPowerClick,
            )
            Text(
                text = caption,
                modifier = Modifier.padding(top = 10.dp),
                color = Ink2,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // Thời gian và dữ liệu
        val totalMb = if (isOn) (state.elapsedSeconds * 0.08).coerceAtLeast(0.1) else 0.0
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LineSoft),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        append("Thời gian ")
                        append(if (isOn) formatDuration(state.elapsedSeconds) else "--:--")
                    },
                    color = Ink2,
                    fontSize = 14.sp,
                )
                Text(
                    text = buildString {
                        append("Dữ liệu ")
                        append(if (isOn) String.format(Locale.US, "%.1f MB", totalMb) else "--")
                    },
                    color = Ink2,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// Công tắc viên thuốc nằm ngang: 148 x 64 dp, núm trắng trượt trái phải
@Composable
private fun PillSwitch(
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (on) TealSoft else TrackOff,
        animationSpec = tween(300),
        label = "track",
    )
    val knobColor by animateColorAsState(
        targetValue = if (on) Teal else Color.White,
        animationSpec = tween(300),
        label = "knob",
    )
    val knobX by animateDpAsState(
        targetValue = if (on) 74.dp else 6.dp,
        animationSpec = tween(350),
        label = "knobX",
    )
    val knobShape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .size(width = 148.dp, height = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobX, y = 6.dp)
                .size(width = 68.dp, height = 52.dp)
                .shadow(2.dp, knobShape)
                .clip(knobShape)
                .background(knobColor),
        )
    }
}

@Composable
private fun LocationChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .border(1.dp, if (selected) Ink else LineSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Ink else Ink2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

@Composable
internal fun SignalBars(level: Int) {
    Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(5.dp, 9.dp, 14.dp).forEachIndexed { i, h ->
            Box(Modifier.size(width = 3.dp, height = h).background(if (i < level) C.SignalOn else C.SignalOff, RoundedCornerShape(1.dp)))
        }
    }
}

/** Cờ vẽ bằng Canvas cho các nước và logo game. */
@Composable
fun CountryFlag(code: String, size: Dp) {
    if (code == "FF" || code == "FFMAX") {
        Image(
            painter = painterResource(
                if (code == "FFMAX") R.drawable.logo_freefire_max else R.drawable.logo_freefire
            ),
            contentDescription = if (code == "FFMAX") "Free Fire Max" else "Free Fire",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, C.Border, CircleShape),
        )
        return
    }
    val red = Color(0xFFC9504A); val blue = Color(0xFF3E5A8C)
    Canvas(Modifier.size(size).clip(CircleShape).border(1.dp, C.Border, CircleShape)) {
        val u = this.size.width / 36f
        fun rect(c: Color, x: Float, y: Float, w: Float, h: Float) =
            drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
        when (code) {
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
internal fun BottomNav(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
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

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable
private fun PreviewHomeOff() = VeilTheme {
    HomeScreen(HomeUiState(), {})
}
