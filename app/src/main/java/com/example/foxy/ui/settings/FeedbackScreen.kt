package com.example.foxy.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R

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

/**
 * Màn hình Gửi phản hồi (Submit Feedback) chuẩn 100% theo Ảnh 3 và layout 1.1.1.1 gốc
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean = false,
    currentLanguage: String = "vi",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shareDebugInfo by remember { mutableStateOf(true) }

    val isVi = currentLanguage == "vi"
    val bgColor = if (isDarkTheme) Color(0xFF141414) else Color(0xFFFFFFFF)
    val inkColor = if (isDarkTheme) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
    val ink2Color = if (isDarkTheme) Color(0xFFA0A0A0) else Color(0xFF555555)
    val footerBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val brandBlue = Color(0xFF0053D0)

    // Nội dung text theo ngôn ngữ
    val titleText = if (isVi) "Gửi phản hồi" else "Submit Feedback"
    val descText = if (isVi) {
        "Trung tâm trợ giúp của chúng tôi luôn được cập nhật với các câu trả lời cho các câu hỏi thường gặp. Xem trung tâm trợ giúp."
    } else {
        "Our help is updated with answers to common questions. Visit Help center."
    }
    val includeLabelText = if (isVi) "Trong phản hồi của bạn vui lòng bao gồm:" else "In your feedback please include:"
    val step1Text = if (isVi) "1. Các bước cần thiết để tái hiện sự cố." else "1. Steps required to reproduce the issue."
    val step2Text = if (isVi) "2. Những gì bạn mong đợi sẽ xảy ra." else "2. What you expected to happen."
    val step3Text = if (isVi) "3. Bất kỳ thông tin nào khác có thể giúp ích cho đội ngũ phát triển." else "3. Any other information that may help the development team."
    val shareDebugText = if (isVi) "Chia sẻ thông tin gỡ lỗi" else "Share debug information"
    val viewLogsText = if (isVi) "Xem nhật ký" else "View Logs"
    val feedbackText = if (isVi) "Phản hồi" else "Feedback"

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
            Spacer(Modifier.height(10.dp))

            // Ảnh minh họa gốc ic_notification_permission_and_submit_feedback từ 1.1.1.1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_notification_permission_and_submit_feedback),
                    contentDescription = "Submit Feedback Illustration",
                    modifier = Modifier.size(width = 130.dp, height = 96.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Tiêu đề
            Text(
                text = titleText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = inkColor,
            )

            Spacer(Modifier.height(14.dp))

            // Mô tả
            Text(
                text = descText,
                fontSize = 15.sp,
                color = ink2Color,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(20.dp))

            // Dòng hướng dẫn
            Text(
                text = includeLabelText,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = inkColor,
            )

            Spacer(Modifier.height(12.dp))

            // 3 gạch đầu dòng
            Text(
                text = step1Text,
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = step2Text,
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = step3Text,
                fontSize = 14.5.sp,
                color = ink2Color,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(30.dp))
        }

        // Chân trang ButtonsContainer (như trong layout XML của 1.1.1.1 & Ảnh 3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(footerBg)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Cụm Switch Share debug information CĂN GIỮA MÀN HÌNH
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { shareDebugInfo = !shareDebugInfo }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = shareDebugInfo,
                    onCheckedChange = { shareDebugInfo = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF24C00), // Màu cam chuẩn 1.1.1.1
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isDarkTheme) Color(0xFF444444) else Color(0xFFCCCCCC),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = shareDebugText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = inkColor,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 2. Cụm 2 nút bấm: View Logs (không viền) và Feedback (khối xanh #0053D0 góc phẳng) CĂN GIỮA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nút View Logs: text button phẳng hoàn toàn không viền, căn thẳng hàng
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .clickable {
                            val logMsg = if (isVi) "Nhật ký hệ thống: Foxy Core VPN v1.0.0 [OK]" else "System Log: Foxy Core VPN v1.0.0 [OK]"
                            Toast.makeText(context, logMsg, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = viewLogsText,
                        color = inkColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Nút Feedback: khối xanh #0053D0 góc phẳng chuẩn 0dp, chứa url ẩn https://t.me/lunexchitgame
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(brandBlue)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/lunexchitgame"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Không thể mở liên kết: https://t.me/lunexchitgame", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = feedbackText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
