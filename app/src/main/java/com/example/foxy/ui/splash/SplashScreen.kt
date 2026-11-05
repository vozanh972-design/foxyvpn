package com.example.foxy.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    // Trạng thái hiệu ứng theo đúng CSS @keyframes
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(10f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo animation: pop .7s cubic-bezier(.2,1.4,.4,1) both
        val popEasing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)
        launch {
            logoScale.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = popEasing))
        }
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 700))
        }

        // Text animation: fup .7s .25s both (delay 250ms)
        delay(250)
        launch {
            textOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 700))
        }
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(durationMillis = 700))
        }

        // Giữ màn hình load khoảng 2 giây rồi tự động chuyển vào màn tiếp theo
        delay(2000)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xFF5A2EE0), // #5a2ee0
                    0.55f to Color(0xFF2A1086), // var(--vi) 55%
                    1.0f to Color(0xFF150647), // #150647
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Logo (.mk: 112px x 112px)
            Image(
                painter = painterResource(id = R.drawable.ic_foxy_logo),
                contentDescription = "Foxy Logo",
                modifier = Modifier
                    .size(112.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            )

            // Tên app (.nm: 36px, font-weight: 800, letter-spacing: -1.4px, color: #fff)
            Text(
                text = "Foxy",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold, // 800
                letterSpacing = (-1.4).sp,
                modifier = Modifier
                    .offset { IntOffset(0, textOffsetY.value.dp.roundToPx()) }
                    .alpha(textAlpha.value),
            )
        }
    }
}
