package com.example.foxy.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R
import com.example.foxy.ui.home.GameOption

private val BgColor = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1A1A1A)
private val Ink2 = Color(0xFF757575)
private val LineSoft = Color(0xFFEBEAE6)
private val FoxyOrange = Color(0xFFF24C00)
private val WarpBlue = Color(0xFF0052CC)

private val BackArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Ink),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 19f)
        lineTo(8f, 12f)
        lineTo(15f, 5f)
    }.build()
}

private val CheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(FoxyOrange),
        strokeLineWidth = 2.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 13f)
        lineTo(9f, 18f)
        lineTo(20f, 6f)
    }.build()
}

@Composable
fun SettingsScreen(
    selectedGame: GameOption,
    onSelectGame: (GameOption) -> Unit,
    onAccountClick: () -> Unit,
    onBack: () -> Unit,
    isVip: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding(),
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BackArrowIcon,
                    contentDescription = "Quay lại",
                    tint = Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "Cài đặt",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LineSoft),
        )

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Option 1: Free Fire
            GameOptionRow(
                logoRes = R.drawable.logo_freefire,
                title = "Free Fire",
                description = "Tối ưu đường truyền và hỗ trợ kết nối riêng cho Garena Free Fire.",
                isSelected = selectedGame == GameOption.FreeFire,
                onClick = { onSelectGame(GameOption.FreeFire) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LineSoft),
            )

            // Option 2: Free Fire Max
            GameOptionRow(
                logoRes = R.drawable.logo_freefire_max,
                title = "Free Fire Max",
                description = "Tối ưu đường truyền đồ họa cao và giảm giật lag cho Free Fire Max.",
                isSelected = selectedGame == GameOption.FreeFireMax,
                onClick = { onSelectGame(GameOption.FreeFireMax) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LineSoft),
            )

            // Promo Banner (phong cách WARP+)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(WarpBlue)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column {
                    Text(
                        text = if (isVip) "Foxy VIP Activated" else "Foxy Free Activated",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isVip) {
                            "Đã kích hoạt gói đường truyền ưu tiên tốc độ cao và ổn định."
                        } else {
                            "Đã kích hoạt gói đường truyền miễn phí."
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Mục Tài khoản (Account)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAccountClick)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Tài khoản",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                    Text(
                        text = "Xem thông tin gói cước, cài đặt và đăng xuất",
                        fontSize = 13.sp,
                        color = Ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Ink2,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LineSoft),
            )

            // Phiên bản ứng dụng
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Phiên bản",
                    fontSize = 14.sp,
                    color = Ink2,
                )
                Text(
                    text = "1.0.0 (5722)",
                    fontSize = 14.sp,
                    color = Ink2,
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun GameOptionRow(
    logoRes: Int,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 12.dp),
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Ink2,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isSelected) {
            Icon(
                imageVector = CheckIcon,
                contentDescription = "Đang chọn",
                tint = FoxyOrange,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp),
            )
        }
    }
}
