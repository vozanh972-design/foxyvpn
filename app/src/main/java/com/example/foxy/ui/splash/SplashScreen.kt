package com.example.foxy.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.foxy.R
import kotlinx.coroutines.delay

private val BgPaper = Color(0xFFFBFBFA)

/**
 * Màn splash: logo cáo cam trên nền giấy kem.
 * Sau [holdMillis] thì gọi [onFinished] để chuyển sang màn tiếp theo.
 */
@Composable
fun SplashScreen(
    onFinish: () -> Unit = {},
    onFinished: () -> Unit = onFinish,
    holdMillis: Long = 1200L,
) {
    LaunchedEffect(Unit) {
        delay(holdMillis)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPaper),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Đẩy logo lên để tâm nằm khoảng 42% chiều cao màn hình, khớp ảnh xem trước
        Image(
            painter = painterResource(R.drawable.ic_foxy_mark),
            contentDescription = "Logo Foxy",
            modifier = Modifier
                .offset(y = (-70).dp)
                .size(width = 118.dp, height = 170.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Splash",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
private fun SplashPreview() {
    SplashScreen(onFinished = {})
}
