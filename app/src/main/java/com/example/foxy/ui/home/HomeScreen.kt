package com.example.foxy.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.ui.theme.FoxyColors
import com.example.foxy.ui.theme.FoxyTheme
import java.util.Locale
import android.content.Intent
import android.content.ComponentName

/** Màn chính, tự lấy trạng thái từ ViewModel. */
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onLocationClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    HomeContent(
        state = state,
        onPowerClick = viewModel::onPowerClick,
        onMenuClick = onMenuClick,
        onLocationClick = onLocationClick,
    )
}

/** Phiên bản không giữ trạng thái, dùng cho Preview và test. */
@Composable
fun HomeContent(
    state: HomeUiState,
    onPowerClick: () -> Unit,
    onMenuClick: () -> Unit,
    onLocationClick: () -> Unit,
) {
    val anim = rememberGlobeAnim(state.status, state.current)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FoxyColors.Violet2,
                    0.45f to FoxyColors.Violet,
                    1f to FoxyColors.VioletDeep,
                ),
            ),
    ) {
        GlobeCanvas(anim, ServerRepository.userLocation, state.current, Modifier.fillMaxSize())
        TopBar(state, onMenuClick, Modifier.align(Alignment.TopCenter))
        BottomPanel(state, onPowerClick, onLocationClick, Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------------------------------------------------------------------
// Thanh trên: nút 3 gạch + nhãn trạng thái
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(state: HomeUiState, onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    val protected = state.status == ConnectionStatus.Connected
    val chipText = if (protected) "Đang bảo vệ" else "Chưa được bảo vệ"

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FoxyColors.Glass)
                .semantics { contentDescription = "Mở menu" }
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White),
                    )
                }
            }
        }

        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(FoxyColors.Glass)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (protected) FoxyColors.DotOn else FoxyColors.DotOff),
            )
            Text(chipText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Bảng trắng phía dưới
// ---------------------------------------------------------------------------

@Composable
private fun BottomPanel(
    state: HomeUiState,
    onPowerClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val caption = when (state.status) {
        ConnectionStatus.Idle -> "Nhấn để kết nối"
        ConnectionStatus.Connecting -> "Đang kết nối…"
        ConnectionStatus.Connected -> "Đã kết nối · ${formatElapsed(state.elapsedSeconds)}"
    }
    val connected = state.status == ConnectionStatus.Connected

    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 54.dp),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = FoxyColors.Sheet,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 64.dp, bottom = 26.dp),
            ) {
                Text(
                    caption,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = FoxyColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.height(14.dp))
                LocationCard(state, onLocationClick)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatItem(
                        value = if (connected) formatMbps(state.downMbps) else "--",
                        label = "Tải xuống Mb/s",
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterVertically)
                            .width(1.dp)
                            .height(36.dp)
                            .background(FoxyColors.Line),
                    )
                    StatItem(
                        value = if (connected) formatMbps(state.upMbps) else "--",
                        label = "Tải lên Mb/s",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        PowerButton(state.status, onPowerClick, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun LocationCard(state: HomeUiState, onClick: () -> Unit) {
    // Chưa kết nối + chế độ tự động => hiện "Ngẫu nhiên"
    val shown: Server? = if (state.status == ConnectionStatus.Idle) {
        if (state.isAuto) null else state.selected
    } else {
        state.current
    }

    // State cho nút Auto: false = "Auto", true = "..." đang mở game
    var autoTapped by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown?.flag ?: "🌐", fontSize = 30.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    shown?.country ?: "Ngẫu nhiên",
                    color = FoxyColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    shown?.city ?: "Tự động chọn máy chủ",
                    color = FoxyColors.Muted,
                    fontSize = 12.sp,
                )
            }
            // Chip "Auto" / "..." — bấm vào sẽ launch Free Fire
            Text(
                text = if (shown != null) {
                    "${shown.pingMs} ms"
                } else {
                    if (autoTapped) "..." else "Auto"
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (autoTapped && shown == null) Color(0xFFFF4E00)
                        else FoxyColors.PingChip
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(enabled = shown == null) {
                        // Khi bấm vào "Auto": đổi thành "...", rồi mở Free Fire
                        if (!autoTapped) {
                            autoTapped = true
                            try {
                                val intent = Intent().apply {
                                    component = ComponentName(
                                        "com.dts.freefireth",
                                        "com.dts.freefireth.FFMainActivity"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Game chưa cài — thử mở qua package launcher
                                try {
                                    val launchIntent = context.packageManager
                                        .getLaunchIntentForPackage("com.dts.freefireth")
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                } catch (_: Exception) {}
                            }
                            // Reset lại sau 2 giây
                            android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed({ autoTapped = false }, 2000)
                        }
                    },
                color = if (autoTapped && shown == null) Color.White else FoxyColors.Violet2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = FoxyColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(label, color = FoxyColors.Muted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------
// Nút nguồn
// ---------------------------------------------------------------------------

@Composable
private fun PowerButton(status: ConnectionStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val connected = status == ConnectionStatus.Connected
    val connecting = status == ConnectionStatus.Connecting
    val bg by animateColorAsState(
        targetValue = if (connected) FoxyColors.Mint else FoxyColors.Sun,
        animationSpec = tween(400),
        label = "powerBg",
    )
    val beat by rememberInfiniteTransition(label = "beat").animateFloat(
        initialValue = 1f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "beatValue",
    )

    Box(
        modifier
            .size(108.dp)
            .graphicsLayer {
                val s = if (connecting) beat else 1f
                scaleX = s
                scaleY = s
            }
            .shadow(24.dp, CircleShape, ambientColor = bg, spotColor = bg)
            .background(FoxyColors.Sheet, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(bg)
            .semantics { contentDescription = if (connected) "Ngắt kết nối VPN" else "Kết nối VPN" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PowerIcon(FoxyColors.Violet, Modifier.size(42.dp))
    }
}

@Composable
private fun PowerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = Stroke(width = w * 2.6f / 24f, cap = StrokeCap.Round)
        // Cung tròn hở phía trên
        drawArc(
            color = color,
            startAngle = -42.4f,
            sweepAngle = 264.8f,
            useCenter = false,
            topLeft = Offset(w * 4f / 24f, w * 4f / 24f),
            size = Size(w * 16f / 24f, w * 16f / 24f),
            style = stroke,
        )
        // Vạch đứng ở giữa
        drawLine(color, Offset(w / 2f, w * 3f / 24f), Offset(w / 2f, w * 11f / 24f), stroke.width, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Tiện ích
// ---------------------------------------------------------------------------

private fun formatElapsed(totalSeconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60)

private fun formatMbps(v: Double): String = String.format(Locale.US, "%.1f", v)

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeIdlePreview() {
    FoxyTheme { HomeContent(HomeUiState(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeConnectedPreview() {
    FoxyTheme {
        HomeContent(
            HomeUiState(
                status = ConnectionStatus.Connected,
                current = ServerRepository.all[7],
                elapsedSeconds = 12,
                downMbps = 90.1,
                upMbps = 34.4,
            ),
            {}, {}, {},
        )
    }
}
