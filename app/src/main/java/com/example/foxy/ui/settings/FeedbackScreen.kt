package com.example.foxy.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BackArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color(0xFF1A1A1A)),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 19f)
        lineTo(8f, 12f)
        lineTo(15f, 5f)
    }.build()
}

// Icon Feedback minh họa chuẩn hộp thoại hỗ trợ
private val FeedbackIllustrationIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FeedbackIllustration",
        defaultWidth = 80.dp,
        defaultHeight = 80.dp,
        viewportWidth = 80f,
        viewportHeight = 80f,
    ).path(
        stroke = SolidColor(Color(0xFF0052CC)),
        strokeLineWidth = 3.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Hộp thoại chat tròn to
        moveTo(20f, 24f)
        lineTo(60f, 24f)
        lineTo(60f, 52f)
        lineTo(36f, 52f)
        lineTo(24f, 62f)
        lineTo(24f, 52f)
        lineTo(20f, 52f)
        close()
        // Dấu chấm hỏi / thông tin ở giữa
        moveTo(40f, 32f)
        lineTo(40f, 40f)
        moveTo(40f, 45f)
        lineTo(40f, 46.5f)
    }.build()
}

/**
 * Màn hình Gửi phản hồi (Submit Feedback) chuẩn Ảnh 3 và activity_submit_feedback_interstitial.xml của 1.1.1.1
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shareDebugInfo by remember { mutableStateOf(true) }

    val bgColor = if (isDarkTheme) Color(0xFF141414) else Color(0xFFFFFFFF)
    val inkColor = if (isDarkTheme) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
    val ink2Color = if (isDarkTheme) Color(0xFFA0A0A0) else Color(0xFF555555)
    val footerBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF9F9F9)
    val brandBlue = Color(0xFF0052CC)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
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
                    tint = inkColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Nội dung cuộn
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // Hình ảnh minh họa ở giữa
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(brandBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = FeedbackIllustrationIcon,
                        contentDescription = null,
                        tint = brandBlue,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tiêu đề: Submit Feedback
            Text(
                text = "Submit Feedback",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = inkColor,
            )

            Spacer(Modifier.height(14.dp))

            // Mô tả Visit Help center
            Text(
                text = "Our help is updated with answers to common questions. Visit Help center.",
                fontSize = 15.sp,
                color = ink2Color,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(20.dp))

            // In your feedback please include:
            Text(
                text = "In your feedback please include:",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = inkColor,
            )

            Spacer(Modifier.height(12.dp))

            // 3 gạch đầu dòng chuẩn XML
            Text(
                text = "1. Steps required to reproduce the issue.",
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "2. What you expected to happen.",
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "3. Any other information that may help the development team.",
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(30.dp))
        }

        // Chân trang ButtonsContainer (như trong layout XML của 1.1.1.1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(footerBg)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Share debug information switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { shareDebugInfo = !shareDebugInfo }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = shareDebugInfo,
                    onCheckedChange = { shareDebugInfo = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = brandBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCCCCCC),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Share debug information",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = inkColor,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Cụm 2 nút bấm: View Logs & Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "Log hệ thống: Foxy Core VPN v1.0.0 OK", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Text(
                        text = "View Logs",
                        color = inkColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }

                Button(
                    onClick = {
                        Toast.makeText(context, "Cảm ơn bạn! Phản hồi đã được gửi thành công.", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandBlue),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "Feedback",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
