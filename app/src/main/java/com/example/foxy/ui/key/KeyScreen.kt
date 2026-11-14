package com.example.foxy.ui.key

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ---------- Màu (lấy đúng từ bản mẫu) ----------
private val BgPaper = Color(0xFFFBFBFA)
private val Ink = Color(0xFF1C1F1D)
private val Ink2 = Color(0xFF6B6F6C)
private val LineSoft = Color(0xFFE4E2DC)
private val CardShadow = Color(0x551E0A3C)
private val PlaceholderGrey = Color(0xFF9A9D98)

// Key hợp lệ: 3 nhóm, mỗi nhóm 4 ký tự chữ hoặc số, nối bằng gạch ngang
private val KEY_REGEX = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")

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
        tint = Ink,
        strokeWidth = 1.8f,
        strokes = listOf("M15 6l-6 6 6 6"),
    )
}

/** Chuẩn hoá khi gõ: chỉ giữ chữ và số, viết hoa, tối đa 12 ký tự, tự thêm gạch sau mỗi 4 ký tự. */
private fun formatKey(input: String): String =
    input.uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(12)
        .chunked(4)
        .joinToString("-")

/**
 * Màn hình nhập key chính:
 * - Mặc định hiển thị IntroScreen.
 * - Khi bấm "Tôi đã có key" hoặc "Lấy key" -> chuyển sang EnterKeyScreen chuẩn mẫu.
 */
@Composable
fun KeyScreen(
    onActivated: () -> Unit,
    onBuyClick: () -> Unit = {},
    viewModel: KeyViewModel = viewModel(),
) {
    val context = LocalContext.current
    var showEnterKey by remember { mutableStateOf(false) }

    Crossfade(targetState = showEnterKey, label = "KeyScreenTransition") { inEnterKey ->
        if (inEnterKey) {
            EnterKeyScreen(
                onBack = { showEnterKey = false },
                onApply = { key ->
                    viewModel.onKeyChange(key)
                    viewModel.onActivate()
                    onActivated()
                },
            )
        } else {
            IntroScreen(
                onBack = { /* Không cần nút back ở màn chính */ },
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
                    showEnterKey = true
                },
                onHaveKey = {
                    showEnterKey = true
                },
            )
        }
    }
}

// ---------- Màn hình 1: IntroScreen ----------
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

// ---------- Màn hình 2: EnterKeyScreen ----------
@Composable
fun EnterKeyScreen(
    onBack: () -> Unit = {},
    onApply: (String) -> Unit = {},
) {
    var value by remember { mutableStateOf("") }
    val valid = KEY_REGEX.matches(value)
    val keyboard = LocalSoftwareKeyboardController.current

    fun apply() {
        if (!valid) return
        keyboard?.hide()
        onApply(value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPaper)
            .statusBarsPadding(),
    ) {
        // Nút quay lại
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .offset(x = (-8).dp)
                .padding(top = 4.dp),
        ) {
            Icon(
                imageVector = BackIcon,
                contentDescription = "Quay lại",
                tint = Ink,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp),
        ) {
            Text(
                text = "Nhập key",
                color = Ink2,
                fontSize = 13.sp,
            )

            Text(
                text = "Nhập key để nhận 5 giờ",
                modifier = Modifier.padding(top = 14.dp),
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
            )

            // Ô nhập key
            BasicTextField(
                value = value,
                onValueChange = { value = formatKey(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
                    .height(60.dp),
                textStyle = TextStyle(
                    color = Ink,
                    fontSize = 26.sp,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { apply() }),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "XXXX - XXXX - XXXX",
                                color = PlaceholderGrey,
                                fontSize = 26.sp,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        inner()
                    }
                },
            )

            // Gạch chân dưới ô nhập
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Ink),
            )

            Text(
                text = if (valid) "Key hợp lệ" else "Gồm 12 ký tự, chia thành 3 nhóm.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = Ink2,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        // Nút Áp dụng: sáng khi key đủ định dạng, mờ khi chưa đủ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Ink.copy(alpha = if (valid) 1f else 0.45f))
                .clickable(enabled = valid, onClick = { apply() }),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Áp dụng",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
