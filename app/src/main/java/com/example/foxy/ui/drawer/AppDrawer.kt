package com.example.foxy.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R
import com.example.foxy.ui.theme.FoxyColors

enum class DrawerPanel { None, Settings, Account }

@Composable
fun AppDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSignOut: () -> Unit,
    userKey: String?,
    modifier: Modifier = Modifier,
) {
    var activePanel by remember { mutableStateOf(DrawerPanel.None) }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
    ) {
        // Scrim (Lớp làm tối nền: rgba(10,4,40,.5))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x800A0428))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (activePanel != DrawerPanel.None) {
                            activePanel = DrawerPanel.None
                        } else {
                            onClose()
                        }
                    },
                ),
        )
    }

    // Drawer Menu trượt từ trái qua phải (width: 78%)
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(420)),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(420)),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .shadow(16.dp, RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)),
                shape = RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp),
                color = FoxyColors.Sheet,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 26.dp),
                ) {
                    // Header: Logo SVG (.dmk) + Foxy (.dh b)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 6.dp, bottom = 26.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_foxy_logo),
                            contentDescription = "Foxy Logo",
                            colorFilter = ColorFilter.tint(FoxyColors.Violet2),
                            modifier = Modifier.size(46.dp),
                        )
                        Text(
                            text = "Foxy",
                            color = FoxyColors.Violet,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.8).sp,
                        )
                    }

                    // Danh sách mục menu
                    DrawerItem(
                        icon = { SettingsIcon(color = it) },
                        text = "Cài đặt",
                        onClick = { activePanel = DrawerPanel.Settings },
                    )
                    DrawerItem(
                        icon = { AccountIcon(color = it) },
                        text = "Gói của tôi",
                        onClick = { activePanel = DrawerPanel.Account },
                    )

                    Spacer(Modifier.height(16.dp))

                    DrawerItem(
                        icon = { HelpIcon(color = it) },
                        text = "Trợ giúp & hỗ trợ",
                        onClick = {},
                    )
                    DrawerItem(
                        icon = { PrivacyIcon(color = it) },
                        text = "Quyền riêng tư",
                        onClick = {},
                    )

                    Spacer(Modifier.height(16.dp))

                    DrawerItem(
                        icon = { DevicesIcon(color = it) },
                        text = "Bảo vệ mọi thiết bị",
                        onClick = {},
                    )
                    DrawerItem(
                        icon = { GiftIcon(color = it) },
                        text = "Tặng 30 ngày miễn phí",
                        onClick = {},
                    )
                    DrawerItem(
                        icon = { FeedbackIcon(color = it) },
                        text = "Gửi phản hồi",
                        onClick = {},
                    )

                    Spacer(Modifier.weight(1f))

                    // Đăng xuất (.it.out: color: #d6455d)
                    DrawerItem(
                        icon = { LogoutIcon(color = Color(0xFFD6455D)) },
                        text = "Đăng xuất",
                        textColor = Color(0xFFD6455D),
                        onClick = {
                            onClose()
                            onSignOut()
                        },
                    )
                }
            }

            // Sub-panel trượt từ trái sang phải: Cài đặt / Gói của tôi
            AnimatedVisibility(
                visible = activePanel != DrawerPanel.None,
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(420)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(420)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FoxyColors.Sheet,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(22.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (activePanel == DrawerPanel.Settings) "Cài đặt" else "Gói của tôi",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FoxyColors.Ink,
                                letterSpacing = (-0.8).sp,
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFECE6FF))
                                    .clickable { activePanel = DrawerPanel.None },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✕", color = FoxyColors.Violet, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (activePanel == DrawerPanel.Settings) {
                            SettingsContent()
                        } else if (activePanel == DrawerPanel.Account) {
                            AccountContent(userKey = userKey)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: @Composable (Color) -> Unit,
    text: String,
    onClick: () -> Unit,
    textColor: Color = FoxyColors.Ink,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(modifier = Modifier.size(23.dp), contentAlignment = Alignment.Center) {
            icon(textColor)
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Nội dung Panel Cài đặt
// ---------------------------------------------------------------------------

@Composable
private fun SettingsContent() {
    var killSwitch by remember { mutableStateOf(true) }
    var autoConnect by remember { mutableStateOf(true) }
    var adBlock by remember { mutableStateOf(false) }
    var splitTunnel by remember { mutableStateOf(false) }

    SettingRow("Ngắt mạng khi mất VPN", "Chặn truy cập nếu VPN bị gián đoạn", killSwitch) { killSwitch = it }
    SettingRow("Tự động kết nối", "Bật VPN khi dùng Wi-Fi công cộng", autoConnect) { autoConnect = it }
    SettingRow("Chặn quảng cáo", "Giảm quảng cáo và trình theo dõi", adBlock) { adBlock = it }
    SettingRow("Chia tách đường hầm", "Chọn ứng dụng không đi qua VPN", splitTunnel) { splitTunnel = it }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Giao thức", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FoxyColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text("WireGuard (khuyến nghị)", fontSize = 12.sp, color = FoxyColors.Muted)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FoxyColors.Ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = FoxyColors.Muted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FoxyColors.Violet2,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = FoxyColors.Line,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Nội dung Panel Gói của tôi
// ---------------------------------------------------------------------------

@Composable
private fun AccountContent(userKey: String?) {
    val displayKey = remember(userKey) {
        val k = userKey ?: "ABCD1234EFGH5678"
        if (k.length >= 16) "${k.take(4)}-••••-••••-${k.takeLast(4)}" else k
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(FoxyColors.Sun),
            contentAlignment = Alignment.Center,
        ) {
            Text("F", color = FoxyColors.Violet, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column {
            Text("Gói Premium", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FoxyColors.Ink)
            Text("Đang hoạt động", fontSize = 12.sp, color = FoxyColors.Muted)
        }
    }

    InfoRow(label = "Key của bạn", value = displayKey)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Hết hạn", fontSize = 12.sp, color = FoxyColors.Muted)
            Spacer(Modifier.height(2.dp))
            Text("03/12/2026 · còn 28 ngày", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FoxyColors.Ink)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFECE6FF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(FoxyColors.Violet2),
                )
            }
        }
    }

    InfoRow(label = "Thiết bị đang dùng", value = "2 / 5 thiết bị")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, fontSize = 12.sp, color = FoxyColors.Muted)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FoxyColors.Ink)
        }
    }
}

// ---------------------------------------------------------------------------
// SVG Icons vẽ Canvas chuẩn xác 1:1 từ HTML
// ---------------------------------------------------------------------------

@Composable
private fun SettingsIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = 3f * s, center = Offset(12f * s, 12f * s), style = stroke)
        val path = Path().apply {
            moveTo(12f * s, 2f * s); lineTo(12f * s, 5f * s)
            moveTo(12f * s, 19f * s); lineTo(12f * s, 22f * s)
            moveTo(2f * s, 12f * s); lineTo(5f * s, 12f * s)
            moveTo(19f * s, 12f * s); lineTo(22f * s, 12f * s)
            moveTo(4.9f * s, 4.9f * s); lineTo(7f * s, 7f * s)
            moveTo(17f * s, 17f * s); lineTo(19.1f * s, 19.1f * s)
            moveTo(4.9f * s, 19.1f * s); lineTo(7f * s, 17f * s)
            moveTo(17f * s, 7f * s); lineTo(19.1f * s, 4.9f * s)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun AccountIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = 4f * s, center = Offset(12f * s, 8f * s), style = stroke)
        val path = Path().apply {
            moveTo(4f * s, 21f * s)
            cubicTo(4f * s, 17f * s, 8f * s, 15f * s, 12f * s, 15f * s)
            cubicTo(16f * s, 15f * s, 20f * s, 17f * s, 20f * s, 21f * s)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun HelpIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = 9f * s, center = Offset(12f * s, 12f * s), style = stroke)
        val path = Path().apply {
            moveTo(9.5f * s, 9.5f * s)
            cubicTo(9.5f * s, 7.5f * s, 14.5f * s, 7.5f * s, 14.5f * s, 10.5f * s)
            cubicTo(14.5f * s, 12f * s, 12f * s, 12.5f * s, 12f * s, 14.5f * s)
            moveTo(12f * s, 17f * s); lineTo(12.01f * s, 17f * s)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun PrivacyIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(12f * s, 3f * s)
            lineTo(20f * s, 6f * s)
            lineTo(20f * s, 12f * s)
            cubicTo(20f * s, 17f * s, 16.5f * s, 20f * s, 12f * s, 21f * s)
            cubicTo(7.5f * s, 20f * s, 4f * s, 17f * s, 4f * s, 12f * s)
            lineTo(4f * s, 6f * s)
            close()
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun DevicesIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, topLeft = Offset(3f * s, 4f * s), size = Size(13f * s, 10f * s), cornerRadius = CornerRadius(2f * s), style = stroke)
        drawRoundRect(color, topLeft = Offset(15f * s, 10f * s), size = Size(6f * s, 11f * s), cornerRadius = CornerRadius(1.5f * s), style = stroke)
    }
}

@Composable
private fun GiftIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, topLeft = Offset(3f * s, 8f * s), size = Size(18f * s, 4f * s), cornerRadius = CornerRadius(1f * s), style = stroke)
        val path = Path().apply {
            moveTo(12f * s, 8f * s); lineTo(12f * s, 21f * s)
            moveTo(5f * s, 12f * s); lineTo(5f * s, 21f * s); lineTo(19f * s, 21f * s); lineTo(19f * s, 12f * s)
            moveTo(12f * s, 8f * s)
            cubicTo(10f * s, 4f * s, 6f * s, 5f * s, 7f * s, 8f * s)
            moveTo(12f * s, 8f * s)
            cubicTo(14f * s, 4f * s, 18f * s, 5f * s, 17f * s, 8f * s)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun FeedbackIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(4f * s, 5f * s)
            lineTo(20f * s, 5f * s)
            lineTo(20f * s, 16f * s)
            lineTo(9f * s, 16f * s)
            lineTo(5f * s, 20f * s)
            close()
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun LogoutIcon(color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(9f * s, 4f * s)
            lineTo(5f * s, 4f * s)
            lineTo(5f * s, 20f * s)
            lineTo(9f * s, 20f * s)
            moveTo(16f * s, 8f * s)
            lineTo(20f * s, 12f * s)
            lineTo(16f * s, 16f * s)
            moveTo(20f * s, 12f * s)
            lineTo(9f * s, 12f * s)
        }
        drawPath(path, color, style = stroke)
    }
}
