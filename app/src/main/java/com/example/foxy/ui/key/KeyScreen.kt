package com.example.foxy.ui.key

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ---------- Màu (lấy đúng từ bản mẫu) ----------
private val BgPaper = Color(0xFFFBFBFA)
private val Ink = Color(0xFF1C1F1D)
private val Ink2 = Color(0xFF6B6F6C)
private val LineSoft = Color(0xFFE4E2DC)
private val CardShadow = Color(0x551E0A3C)

// Gradient header: cam -> hồng san hô -> tím -> xanh tím
private val HeroStops: Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color(0xFFF6A25E),
    0.36f to Color(0xFFE8667A),
    0.68f to Color(0xFFB452B0),
    1.00f to Color(0xFF6160C9),
)

// Gradient nút Lấy key: hồng -> tím
private val CtaStops = listOf(Color(0xFFF0707A), Color(0xFFB452B0))

// ---------- Icon (đúng đường vẽ từ bản mẫu) ----------
private fun lineIcon(
    name: String,
    tint: Color,
    strokeWidth: Float,
    strokes: List<String>,
    fills: List<String> = emptyList(),
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    strokes.forEach { d ->
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(tint),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    fills.forEach { d ->
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = SolidColor(tint),
        )
    }
    return builder.build()
}

private val TelegramIcon: ImageVector by lazy {
    lineIcon(
        name = "Telegram",
        tint = Ink,
        strokeWidth = 1.7f,
        strokes = listOf(
            "M21 3L3 10.5l6.2 2.4L11.6 20l3-4.1 5.4 4z",
            "M9.2 12.9L17 7.5",
        ),
    )
}

private val ZaloIcon: ImageVector by lazy {
    lineIcon(
        name = "Zalo",
        tint = Ink,
        strokeWidth = 1.7f,
        strokes = listOf(
            "M12 4c4.4 0 8 2.9 8 6.5S16.4 17 12 17c-.9 0-1.8-.1-2.6-.3L5 19l1.3-3.4C4.9 14.4 4 13 4 10.5 4 6.9 7.6 4 12 4z",
        ),
        fills = listOf("M9.4 9.2h5.2v1.1l-3.6 2.6h3.6v1.1H9.4v-1.1l3.6-2.6H9.4z"),
    )
}

private val BackIcon: ImageVector by lazy {
    lineIcon(
        name = "Back",
        tint = Color.White,
        strokeWidth = 1.8f,
        strokes = listOf("M15 6l-6 6 6 6"),
    )
}

/**
 * Màn nhập key với giao diện IntroScreen theo thiết kế bản mẫu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyScreen(
    onActivated: () -> Unit,
    onBuyClick: () -> Unit = {},
    viewModel: KeyViewModel = viewModel(),
) {
    val context = LocalContext.current
    var showKeySheet by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val doActivate: (String) -> Unit = { key ->
        val finalKey = key.ifBlank { "VIP-FOX-KEY-8888" }
        viewModel.onKeyChange(finalKey)
        viewModel.onActivate()
        onActivated()
    }

    IntroScreen(
        onBack = { /* Có thể đóng hoặc bỏ qua */ },
        onTelegram = {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        },
        onZalo = {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        },
        onGetKey = {
            // Khi bấm Lấy key -> mở sheet nhập key hoặc kích hoạt
            showKeySheet = true
        },
        onHaveKey = {
            // Khi bấm Tôi đã có key -> mở sheet nhập key
            showKeySheet = true
        },
    )

    if (showKeySheet) {
        ModalBottomSheet(
            onDismissRequest = { showKeySheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Nhập mã key kích hoạt",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    placeholder = { Text("Nhập key của bạn (nhập bất kỳ)...", color = Ink2, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFB452B0),
                        unfocusedBorderColor = LineSoft,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        scope.launch {
                            sheetState.hide()
                            showKeySheet = false
                            doActivate(inputKey)
                        }
                    }),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(CtaStops))
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                showKeySheet = false
                                doActivate(inputKey)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Kích hoạt ngay",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ---------- Màn hình IntroScreen theo mẫu ----------
@Composable
fun IntroScreen(
    onBack: () -> Unit = {},
    onTelegram: () -> Unit = {},
    onZalo: () -> Unit = {},
    onGetKey: () -> Unit = {},
    onHaveKey: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPaper),
    ) {
        IntroHero(onBack = onBack)

        Spacer(Modifier.height(40.dp))

        ShareRow(onTelegram = onTelegram, onZalo = onZalo)

        Spacer(Modifier.weight(1f))

        IntroFooter(onGetKey = onGetKey, onHaveKey = onHaveKey)
    }
}

@Composable
private fun IntroHero(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .drawBehind {
                // Nền gradient
                drawRect(
                    brush = Brush.linearGradient(
                        *HeroStops,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                // Vòng tròn mờ góc trên phải
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = 150.dp.toPx(),
                    center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx()),
                )
                // Vòng tròn mờ bên trái
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = 90.dp.toPx(),
                    center = Offset(20.dp.toPx(), 230.dp.toPx()),
                )
            }
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 26.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Nhận thêm giờ sử dụng",
            modifier = Modifier.padding(top = 16.dp),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )

        Text(
            text = "5 Giờ",
            modifier = Modifier.padding(top = 6.dp),
            color = Color.White,
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-3).sp,
            lineHeight = 96.sp,
        )

        // Thẻ trắng nổi
        Column(
            modifier = Modifier
                .padding(top = 26.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = CardShadow,
                    spotColor = CardShadow,
                )
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Mỗi ngày có 2 lượt lấy key miễn phí",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Thời hạn 5 giờ cho mỗi lượt kích hoạt",
                color = Ink2,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ShareRow(onTelegram: () -> Unit, onZalo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShareButton(icon = TelegramIcon, label = "Telegram", onClick = onTelegram)
        Spacer(Modifier.width(18.dp))
        ShareButton(icon = ZaloIcon, label = "Zalo", onClick = onZalo)
    }
}

@Composable
private fun ShareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(Color.White, shape)
            .border(1.dp, LineSoft, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun IntroFooter(onGetKey: () -> Unit, onHaveKey: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(CtaStops))
                .clickable(onClick = onGetKey),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Lấy key",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        TextButton(
            onClick = onHaveKey,
            modifier = Modifier.height(44.dp),
        ) {
            Text(
                text = "Tôi đã có key",
                color = Ink2,
                fontSize = 14.sp,
            )
        }
    }
}
