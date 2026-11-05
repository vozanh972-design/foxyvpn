package com.example.foxy.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.ui.theme.FoxyColors

/**
 * ServerPickerSheet:
 * Tái hiện chuẩn xác 1:1 từ HTML/CSS class `.pick`:
 * - Slide-in từ dưới lên (translateY(102%) -> none, cubic-bezier(0.2, 0.8, 0.2, 1)).
 * - Header `.ph`: Tiêu đề "Chọn vị trí" + nút đóng tròn `.x` 38x38px.
 * - Thanh tìm kiếm `.q`: input bo góc 16px viền 1.5px nền trắng.
 * - Danh sách `.list`:
 *   + Nút "Ngẫu nhiên" (🌐) hiển thị khi không tìm kiếm.
 *   + Danh sách máy chủ với cờ, tên quốc gia, thành phố, ping ms màu chuẩn `.g` / `.o` / `.r`.
 *   + Item được chọn có nền `#ece6ff` (`.srv.sel`).
 */
@Composable
fun ServerPickerSheet(
    isOpen: Boolean,
    currentServer: Server?,
    isAuto: Boolean,
    onClose: () -> Unit,
    onPick: (Server?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(450, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(450, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        ),
        modifier = modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(24.dp),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = FoxyColors.Sheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp),
            ) {
                // 1. Header .ph: Tiêu đề + Nút đóng .x
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Chọn vị trí",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                        color = FoxyColors.Ink,
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFECE6FF))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoxyColors.Violet,
                        )
                    }
                }

                // 2. Ô tìm kiếm .q: Tìm quốc gia hoặc thành phố
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.5.dp, FoxyColors.Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        cursorBrush = SolidColor(FoxyColors.Violet2),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = FoxyColors.Ink,
                            fontWeight = FontWeight.Medium,
                        ),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Tìm quốc gia hoặc thành phố",
                                    fontSize = 14.sp,
                                    color = FoxyColors.Muted,
                                )
                            }
                            innerTextField()
                        },
                    )
                }

                // Lọc danh sách theo query (v[1] + v[2]).toLowerCase().includes(f)
                val cleanQuery = query.trim().lowercase()
                val filteredServers = remember(cleanQuery) {
                    if (cleanQuery.isEmpty()) {
                        ServerRepository.all
                    } else {
                        ServerRepository.all.filter {
                            (it.country + it.city).lowercase().contains(cleanQuery)
                        }
                    }
                }

                // 3. Danh sách .list
                if (cleanQuery.isNotEmpty() && filteredServers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(
                            text = "Không có máy chủ phù hợp. Thử từ khoá khác.",
                            fontSize = 14.sp,
                            color = FoxyColors.Muted,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Mục "Ngẫu nhiên" hiển thị khi không tìm kiếm (f ? "" : ...)
                        if (cleanQuery.isEmpty()) {
                            item {
                                ServerRow(
                                    flag = "🌐",
                                    title = "Ngẫu nhiên",
                                    subtitle = "Tự động chọn máy chủ xa",
                                    trailingText = "Auto",
                                    trailingColor = FoxyColors.Violet2,
                                    isSelected = isAuto,
                                    onClick = {
                                        onPick(null)
                                        Toast.makeText(context, "Đã chọn: Ngẫu nhiên", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }

                        // Danh sách máy chủ
                        items(filteredServers, key = { it.country + it.city }) { server ->
                            val isSelected = !isAuto && currentServer?.country == server.country && currentServer?.city == server.city

                            // Phân màu ping ms chuẩn HTML CSS: .g (<60) | .o (<150) | .r (>=150)
                            val pingColor = when {
                                server.pingMs < 60 -> Color(0xFF14A85A)   // .g
                                server.pingMs < 150 -> Color(0xFFE08A00)  // .o
                                else -> Color(0xFFD6455D)                 // .r
                            }

                            ServerRow(
                                flag = server.flag,
                                title = server.country,
                                subtitle = server.city,
                                trailingText = "${server.pingMs} ms",
                                trailingColor = pingColor,
                                isSelected = isSelected,
                                onClick = {
                                    onPick(server)
                                    Toast.makeText(context, "Đã chọn ${server.country}", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Một hàng máy chủ .srv:
 * - flag 30px
 * - b (15px 600) + small (12px var(--mut))
 * - span.ms (12px 600)
 * - isSelected: background #ece6ff
 */
@Composable
private fun ServerRow(
    flag: String,
    title: String,
    subtitle: String,
    trailingText: String,
    trailingColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFFECE6FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = flag,
            fontSize = 26.sp,
            lineHeight = 26.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = FoxyColors.Ink,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = FoxyColors.Muted,
            )
        }
        Text(
            text = trailingText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = trailingColor,
        )
    }
}
