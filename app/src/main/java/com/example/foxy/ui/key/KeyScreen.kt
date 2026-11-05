package com.example.foxy.ui.key

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.ui.theme.FoxyColors
import com.example.foxy.ui.theme.FoxyTheme

private val PlaceholderColor = Color(0xFFCBC3EE)
private val ErrorColor = Color(0xFFFFB3BF)

/** Màn nhập key. Gọi [onActivated] khi kích hoạt thành công. */
@Composable
fun KeyScreen(
    onActivated: () -> Unit,
    onBuyClick: () -> Unit,
    viewModel: KeyViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.activated) {
        if (state.activated) onActivated()
    }

    KeyContent(
        state = state,
        onKeyChange = viewModel::onKeyChange,
        onActivate = viewModel::onActivate,
        onBuyClick = onBuyClick,
    )
}

/** Phiên bản không giữ trạng thái, dùng cho Preview và test. */
@Composable
fun KeyContent(
    state: KeyUiState,
    onKeyChange: (String) -> Unit,
    onActivate: () -> Unit,
    onBuyClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FoxyColors.Violet2,
                    0.5f to FoxyColors.Violet,
                    1f to FoxyColors.VioletDeep,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            Text("Foxy", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Kích hoạt gói của bạn",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nhập key 16 ký tự bạn nhận được khi mua gói để bắt đầu.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(20.dp))

                KeyTicket(raw = state.raw, onKeyChange = onKeyChange, onSubmit = onActivate)

                Text(
                    text = state.error.orEmpty(),
                    color = ErrorColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp)
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onActivate,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FoxyColors.Sun,
                    contentColor = FoxyColors.Violet,
                    disabledContainerColor = FoxyColors.Sun.copy(alpha = 0.7f),
                    disabledContentColor = FoxyColors.Violet,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(10.dp, RoundedCornerShape(29.dp), ambientColor = FoxyColors.Sun, spotColor = FoxyColors.Sun),
            ) {
                Text(
                    if (state.isLoading) "Đang xác thực…" else "Kích hoạt",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Chưa có key? ", color = Color.White.copy(alpha = 0.7f), fontSize = 12.5.sp)
                Text(
                    "Mua gói",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onBuyClick),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Thẻ vé chứa 4 ô nhập
// ---------------------------------------------------------------------------

@Composable
private fun KeyTicket(raw: String, onKeyChange: (String) -> Unit, onSubmit: () -> Unit) {
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    // Chiều cao phần trên của thẻ => vị trí hai chỗ khuyết nằm đúng đường chấm đứt
    var topHeightPx by remember { mutableStateOf(0f) }
    val shape = remember(topHeightPx) {
        TicketShape(
            cornerRadius = 24.dp,
            notchRadius = 11.dp,
            notchCenterYPx = topHeightPx + with(density) { 1.dp.toPx() },
        )
    }

    Surface(
        shape = shape,
        color = FoxyColors.Sheet,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() },
    ) {
        Column {
            // --- Phần trên: nhãn + 4 ô ---
            Column(
                Modifier
                    .onSizeChanged { topHeightPx = it.height.toFloat() }
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            ) {
                Text(
                    "MÃ KÍCH HOẠT",
                    color = FoxyColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(12.dp))

                // Một ô nhập ẩn duy nhất, 4 ô bên dưới chỉ để hiển thị.
                // Cách này giúp gõ, xoá và dán đều tự nhảy ô mà không cần quản lý focus từng ô.
                BasicTextField(
                    value = raw,
                    onValueChange = onKeyChange,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = TextStyle(color = Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        KeyBoxes(raw = raw, focused = focused)
                        Box(Modifier.height(0.dp)) { inner() }
                    },
                )
            }

            // --- Đường chấm đứt ---
            Canvas(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                drawLine(
                    color = FoxyColors.Line,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                )
            }

            // --- Phần dưới: nút dán ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Dán key từ bộ nhớ",
                    color = FoxyColors.Violet,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(FoxyColors.PingChip)
                        .clickable {
                            clipboard.getText()?.text?.let(onKeyChange)
                            focusRequester.requestFocus()
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyBoxes(raw: String, focused: Boolean) {
    val active = (raw.length / KEY_GROUP).coerceAtMost(3)
    val boxShape = RoundedCornerShape(14.dp)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            val chunk = raw.drop(i * KEY_GROUP).take(KEY_GROUP)
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(boxShape)
                    .background(Color.White)
                    .border(
                        1.5.dp,
                        if (focused && i == active) FoxyColors.Violet2 else FoxyColors.Line,
                        boxShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chunk.ifEmpty { "XXXX" },
                    color = if (chunk.isEmpty()) PlaceholderColor else FoxyColors.Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
            }
            if (i < 3) Text("-", color = PlaceholderColor, fontWeight = FontWeight.Bold)
        }
    }
}

private const val KEY_GROUP = 4

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun KeyEmptyPreview() {
    FoxyTheme { KeyContent(KeyUiState(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun KeyErrorPreview() {
    FoxyTheme {
        KeyContent(
            KeyUiState(raw = "ABCD1234EF", error = "Key cần đủ 16 ký tự, ví dụ: ABCD-1234-EFGH-5678"),
            {}, {}, {},
        )
    }
}
