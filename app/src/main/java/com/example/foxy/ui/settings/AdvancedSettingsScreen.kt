package com.example.foxy.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Màn hình Cài đặt nâng cao (Advanced) chuẩn theo Ảnh 3 và activity_settings.xml của 1.1.1.1
 */
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    currentLanguage: String = "vi",
    onLanguageChange: (String) -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Color(0xFF141414) else Color(0xFFFFFFFF)
    val inkColor = if (isDarkTheme) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
    val ink2Color = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF757575)
    val lineSoft = if (isDarkTheme) Color(0xFF262626) else Color(0xFFEBEAE6)
    val foxyOrange = Color(0xFFF24C00)

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
            Text(
                text = "Cài đặt",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = inkColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(lineSoft),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 1. Ngôn ngữ (thay cho Trung tâm trợ giúp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Ngôn ngữ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = inkColor,
                )
                Text(
                    text = if (currentLanguage == "vi") "Tiếng Việt" else "English",
                    fontSize = 14.5.sp,
                    color = ink2Color,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineSoft),
            )

            // 2. Gửi phản hồi (Send feedback -> Mở màn hình Submit Feedback)
            AdvancedTextRow(
                title = "Gửi phản hồi",
                inkColor = inkColor,
                onClick = onFeedbackClick,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineSoft),
            )

            // 3. Giao diện tối (Use dark theme) - có Switch hoạt động thực tế
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Giao diện tối",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = inkColor,
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onDarkThemeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = foxyOrange,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isDarkTheme) Color(0xFF444444) else Color(0xFFE0E0E0),
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineSoft),
            )

            // 4. Tùy chọn kết nối (Connection options)
            AdvancedTextRow(
                title = "Tùy chọn kết nối",
                inkColor = inkColor,
                onClick = {},
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineSoft),
            )

            // 5. Chẩn đoán (Diagnostics)
            AdvancedTextRow(
                title = "Chẩn đoán",
                inkColor = inkColor,
                onClick = {},
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineSoft),
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // Dialog chọn ngôn ngữ
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = "Chọn ngôn ngữ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = inkColor,
                )
            },
            text = {
                Column {
                    LanguageOptionItem(
                        title = "Tiếng Việt (Mặc định)",
                        selected = currentLanguage == "vi",
                        inkColor = inkColor,
                        onSelect = {
                            onLanguageChange("vi")
                            showLanguageDialog = false
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    LanguageOptionItem(
                        title = "English",
                        selected = currentLanguage == "en",
                        inkColor = inkColor,
                        onSelect = {
                            onLanguageChange("en")
                            showLanguageDialog = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = "Đóng", color = foxyOrange)
                }
            },
            containerColor = if (isDarkTheme) Color(0xFF222222) else Color.White,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun LanguageOptionItem(
    title: String,
    selected: Boolean,
    inkColor: Color,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFF24C00),
                unselectedColor = Color.Gray,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            color = inkColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AdvancedTextRow(
    title: String,
    inkColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = inkColor,
        )
    }
}
