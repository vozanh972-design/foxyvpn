package com.example.foxy.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
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
    isDarkTheme: Boolean = false,
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
        isDarkTheme = isDarkTheme,
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
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isOn = state.status == ConnectionStatus.Connected
    val isBusy = state.status == ConnectionStatus.Connecting

    val bg = if (isDarkTheme) Color(0xFF141414) else BgWhite
    val ink = if (isDarkTheme) Color(0xFFEEEEEE) else Ink
    val ink2 = if (isDarkTheme) Color(0xFF9E9E9E) else Ink2
    val line = if (isDarkTheme) Color(0xFF262626) else LineSoft

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
            .background(bg)
            .statusBarsPadding(),
    ) {
        // Top Bar phong cách 1.1.1.1: Logo Foxy + by ThanhMost góc trái, Nút 3 gạch góc phải
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
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
                    modifier = Modifier.size(width = 22.dp, height = 30.dp),
                    contentScale = ContentScale.Fit,
                )
                Column {
                    Text(
                        text = "FOXY",
                        color = ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = "by ThanhMost",
                        color = ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp,
                    )
                }
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
                                .background(ink),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.35f))

        // Trung tâm: Chữ Game động phong cách WARP / 1.1.1.1 + Công tắc Switch lớn + Trạng thái
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Chữ Free Fire hoặc Free Fire Max với animation gradient
            AnimatedGameLogo(selectedGame = state.serverName)

            Spacer(Modifier.height(44.dp))

            PillSwitch(
                on = isOn,
                enabled = !isBusy,
                onClick = onPowerClick,
                isDarkTheme = isDarkTheme,
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = statusHeadline,
                color = if (isOn) FoxyOrange else ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = statusSubtitle,
                color = ink2,
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
                    .background(line),
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
                    color = ink2,
                    fontSize = 13.sp,
                )
                Text(
                    text = if (isOn) formatDuration(state.elapsedSeconds) else "--:--:--",
                    color = if (isOn) ink else ink2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Chữ logo Game động phong cách LogoView của 1.1.1.1:
 * - Free Fire: Hiệu ứng gradient lửa cam rực rỡ (từ bg_warp_logo)
 * - Free Fire Max: Hiệu ứng gradient cầu vồng đa sắc (từ bg_1111_logo)
 */
@Composable
private fun AnimatedGameLogo(
    selectedGame: String,
    modifier: Modifier = Modifier,
) {
    val isMax = selectedGame.contains("Max", ignoreCase = true)
    val text = if (isMax) "FREE FIRE MAX" else "FREE FIRE"

    val infiniteTransition = rememberInfiniteTransition(label = "logoFlow")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gradientOffset",
    )

    // Palette màu chuẩn từ bg_warp_logo và bg_1111_logo
    val warpColors = remember {
        listOf(
            Color(0xFFEC1557),
            Color(0xFFF16137),
            Color(0xFFF24C00),
            Color(0xFFFFA000),
            Color(0xFFFF5722),
            Color(0xFFEC1557),
        )
    }

    val rainbowColors = remember {
        listOf(
            Color(0xFF343893),
            Color(0xFF8A318A),
            Color(0xFFB21F6F),
            Color(0xFFF16137),
            Color(0xFF45BAD2),
            Color(0xFF343893),
        )
    }

    val activeColors = if (isMax) rainbowColors else warpColors

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = if (isMax) 28.sp else 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = if (isMax) 2.5.sp else 3.5.sp,
                brush = Brush.horizontalGradient(
                    colors = activeColors + activeColors,
                    startX = offsetFraction * 700f,
                    endX = offsetFraction * 700f + 700f,
                    tileMode = TileMode.Repeated,
                ),
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// Công tắc viên thuốc nằm ngang: 148 x 64 dp, phong cách Comet Switch của 1.1.1.1
@Composable
private fun PillSwitch(
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isDarkTheme: Boolean = false,
) {
    val trackOffColor = if (isDarkTheme) Color(0xFF2C2C30) else TrackOff
    val trackColor by animateColorAsState(
        targetValue = if (on) FoxyOrange else trackOffColor,
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
