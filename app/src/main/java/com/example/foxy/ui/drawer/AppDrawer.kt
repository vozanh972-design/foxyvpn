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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            activePanel = DrawerPanel.None
        }
    }

    // 1. Scrim (Lớp làm tối nền: rgba(10,4,40,.5))
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
    ) {
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

    // 2. Drawer Menu chính trượt từ trái qua phải (width: 78%)
    AnimatedVisibility(
        visible = isOpen && activePanel == DrawerPanel.None,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(380)),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(380)),
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
                        modifier = Modifier.padding(start = 6.dp, bottom = 24.dp),
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

                    // Danh sách mục menu (cuộn mượt mà và chiếm phần trên)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                        // ĐÃ BỎ MỤC "Tặng 30 ngày miễn phí" THEO YÊU CẦU
                        DrawerItem(
                            icon = { FeedbackIcon(color = it) },
                            text = "Gửi phản hồi",
                            onClick = {},
                        )
                    }

                    // ĐĂNG XUẤT ĐƯỢC ĐẨY SÁT ĐÁY MÀN HÌNH (.it.out { margin-top: auto; color: #d6455d; })
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
        }
    }

    // 3. Panel trượt toàn màn hình (.pnl) khi bấm Cài đặt / Gói của tôi
    AnimatedVisibility(
        visible = isOpen && activePanel != DrawerPanel.None,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(380)),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(380)),
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .shadow(20.dp),
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
                // Header Panel: Tiêu đề + Nút đóng X
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

// ---------------------------------------------------------------------------
// Nội dung Panel Gói của tôi (Thiết kế cao cấp, tinh xảo, chuẩn Foxy Design)
// ---------------------------------------------------------------------------

@Composable
private fun AccountContent(userKey: String?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val rawKey = userKey?.ifBlank { null } ?: "ABCD1234EFGH5678"
    val displayKey = remember(rawKey) {
        if (rawKey.length >= 16) "${rawKey.take(4)}-••••-••••-${rawKey.takeLast(4)}" else rawKey
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hồ sơ gói: Logo thương hiệu Foxy + Tên gói & Trạng thái hoạt động
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon hộp thương hiệu Foxy sang trọng
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoxyColors.Violet),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_foxy_logo),
                    contentDescription = "Foxy Logo",
                    modifier = Modifier.size(34.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gói Premium",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoxyColors.Ink,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(4.dp))
                // Tag "Đang hoạt động" xanh ngọc emerald
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFE8F8F0))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14A85A)),
                    )
                    Text(
                        text = "Đang hoạt động",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF14A85A),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 2. Thẻ Key của bạn (.row)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, FoxyColors.Line),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Key của bạn",
                        fontSize = 12.sp,
                        color = FoxyColors.Muted,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = displayKey,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoxyColors.Ink,
                        letterSpacing = 1.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }

                // Nút Sao chép key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFECE6FF))
                        .clickable {
                            clipboard.setText(AnnotatedString(rawKey))
                            Toast.makeText(context, "Đã sao chép key!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sao chép",
                        color = FoxyColors.Violet2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // 3. Thẻ Hạn dùng & Thanh tiến trình (.row)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, FoxyColors.Line),
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Hết hạn",
                    fontSize = 12.sp,
                    color = FoxyColors.Muted,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "03/12/2026 · còn 28 ngày",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoxyColors.Ink,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.height(8.dp))

                // Thanh tiến trình chuẩn HTML .bar
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

        // 4. Thẻ Thiết bị đang dùng (.row)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, FoxyColors.Line),
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Thiết bị đang dùng",
                            fontSize = 12.sp,
                            color = FoxyColors.Muted,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "2 / 5 thiết bị",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoxyColors.Ink,
                        )
                    }

                    // 5 chấm chỉ số thiết bị (2 hoạt động, 3 khả dụng)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { i ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (i < 2) FoxyColors.Violet2 else Color(0xFFE4DEF8)),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(FoxyColors.Line))
                Spacer(Modifier.height(8.dp))

                // Danh sách thiết bị kết nối rõ ràng, chân thực
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "📱 Thiết bị này (Android)",
                        fontSize = 13.sp,
                        color = FoxyColors.Ink,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Đang kết nối",
                        fontSize = 11.5.sp,
                        color = Color(0xFF14A85A),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💻 Máy tính Windows",
                        fontSize = 13.sp,
                        color = FoxyColors.Muted,
                    )
                    Text(
                        "Đã đồng bộ",
                        fontSize = 11.5.sp,
                        color = FoxyColors.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 5. Nút Gia hạn gói (.go) - Chuẩn thiết kế Foxy Sun
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(6.dp, RoundedCornerShape(27.dp), ambientColor = Color(0x33FFD23F), spotColor = Color(0x66FFD23F)),
            shape = RoundedCornerShape(27.dp),
            color = FoxyColors.Sun,
            onClick = {
                Toast.makeText(context, "Hệ thống gia hạn sẽ sớm kích hoạt!", Toast.LENGTH_SHORT).show()
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Gia hạn thêm ngày",
                    color = FoxyColors.Violet,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
