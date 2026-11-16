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

// ---------- Màu chuẩn phong cách 1.1.1.1 Cloudflare kết hợp nhận diện Foxy ----------
private val BgWhite = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1A1A1A)
private val Ink2 = Color(0xFF757575)
private val LineSoft = Color(0xFFEBEAE6)
private val FoxyOrange = Color(0xFFF24C00)
private val TrackOff = Color(0xFFE7E7EC)

enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Trang chủ", Icons.Rounded.Home),
    Servers("Máy chủ", Icons.Rounded.Public),
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

    val statusHeadline = when {
        isBusy -> "Đang kết nối..."
        isOn -> "Đã kết nối"
        else -> "Đã ngắt kết nối"
    }

    val statusSubtitle = when {
        isBusy -> "Vui lòng đợi trong giây lát"
        isOn -> "Đang tối ưu đường truyền cho ${state.serverName}"
        else -> "Chạm vào công tắc để bật Foxy"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgWhite)
            .statusBarsPadding(),
    ) {
        // Top Bar phong cách 1.1.1.1: Logo Foxy góc trái, Nút 3 gạch góc phải
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_foxy_mark),
                    contentDescription = "Logo Foxy",
                    modifier = Modifier.size(width = 20.dp, height = 28.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = "FOXY",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.5.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Ink),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.45f))

        // Trung tâm: Công tắc Switch lớn + Trạng thái chữ to rõ của 1.1.1.1
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillSwitch(
                on = isOn,
                enabled = !isBusy,
                onClick = onPowerClick,
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = statusHeadline,
                color = if (isOn) FoxyOrange else Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = statusSubtitle,
                color = Ink2,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1.55f))

        // Chân trang: Đường kẻ mỏng thanh lịch + Thời gian kết nối
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
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
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Thời gian kết nối",
                    color = Ink2,
                    fontSize = 13.sp,
                )
                Text(
                    text = if (isOn) formatDuration(state.elapsedSeconds) else "--:--:--",
                    color = if (isOn) Ink else Ink2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// Công tắc viên thuốc nằm ngang: 148 x 64 dp, phong cách Comet Switch của 1.1.1.1
@Composable
private fun PillSwitch(
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (on) FoxyOrange else TrackOff,
        animationSpec = tween(300),
        label = "track",
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
                .shadow(3.dp, knobShape)
                .clip(knobShape)
                .background(Color.White),
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
