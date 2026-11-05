package com.example.foxy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.foxy.data.GeoPoint
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.ui.theme.FoxyColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Điểm chấm trên quả địa cầu
// ---------------------------------------------------------------------------

private const val POINT_COUNT = 1900

private class GlobeDots(
    val x0: FloatArray,
    val y0: FloatArray,
    val z0: FloatArray,
    val land: BooleanArray,
)

private class Ellipse(val lat: Double, val lon: Double, val rLat: Double, val rLon: Double)

/** Các hình elip thô để mô phỏng lục địa (không cần dữ liệu bản đồ thật). */
private val landEllipses = listOf(
    Ellipse(45.0, -100.0, 20.0, 35.0), Ellipse(62.0, -105.0, 10.0, 35.0),
    Ellipse(20.0, -100.0, 10.0, 12.0), Ellipse(72.0, -40.0, 8.0, 16.0),
    Ellipse(-12.0, -60.0, 22.0, 16.0), Ellipse(-35.0, -66.0, 14.0, 8.0),
    Ellipse(50.0, 15.0, 11.0, 25.0), Ellipse(5.0, 20.0, 28.0, 22.0),
    Ellipse(-20.0, 25.0, 12.0, 14.0), Ellipse(50.0, 90.0, 22.0, 55.0),
    Ellipse(30.0, 95.0, 15.0, 30.0), Ellipse(22.0, 78.0, 12.0, 10.0),
    Ellipse(14.0, 104.0, 10.0, 8.0), Ellipse(-2.0, 115.0, 6.0, 18.0),
    Ellipse(37.0, 138.0, 7.0, 4.0), Ellipse(-25.0, 134.0, 12.0, 20.0),
)

private fun isLand(lat: Double, lon: Double): Boolean = landEllipses.any { e ->
    var dl = abs(lon - e.lon)
    if (dl > 180.0) dl = 360.0 - dl
    ((lat - e.lat) / e.rLat).pow(2) + (dl / e.rLon).pow(2) < 1.0
}

private fun buildDots(): GlobeDots {
    val x = FloatArray(POINT_COUNT)
    val y = FloatArray(POINT_COUNT)
    val z = FloatArray(POINT_COUNT)
    val land = BooleanArray(POINT_COUNT)
    for (i in 0 until POINT_COUNT) {
        // Rải đều các điểm lên mặt cầu (Fibonacci sphere)
        val latDeg = Math.toDegrees(asin(1.0 - 2.0 * (i + 0.5) / POINT_COUNT))
        val lonDeg = (i * 137.508) % 360.0 - 180.0
        val la = Math.toRadians(latDeg)
        val lo = Math.toRadians(lonDeg)
        x[i] = (cos(la) * sin(lo)).toFloat()
        y[i] = sin(la).toFloat()
        z[i] = (cos(la) * cos(lo)).toFloat()
        land[i] = isLand(latDeg, lonDeg)
    }
    return GlobeDots(x, y, z, land)
}

/** Vector đơn vị của một toạ độ khi quả địa cầu chưa xoay (yaw = 0). */
private fun geoVec(p: GeoPoint): FloatArray {
    val la = Math.toRadians(p.lat)
    val lo = Math.toRadians(p.lon)
    return floatArrayOf(
        (cos(la) * sin(lo)).toFloat(),
        sin(la).toFloat(),
        (cos(la) * cos(lo)).toFloat(),
    )
}

// ---------------------------------------------------------------------------
// Hoạt ảnh: xoay địa cầu + vẽ dần đường cung
// ---------------------------------------------------------------------------

/** Độ lệch ngắn nhất (-180..180) từ góc [from] tới góc [to]. */
private fun shortestDelta(from: Float, to: Float): Float =
    (((to - from) % 360f) + 540f) % 360f - 180f

private fun midLon(a: Float, b: Float): Float = a + shortestDelta(a, b) / 2f

class GlobeAnimState {
    /** Kinh độ đang nằm giữa màn hình (độ). */
    val yaw = Animatable(ServerRepository.userLocation.lon.toFloat())

    /** 0 = chưa vẽ đường cung, 1 = vẽ xong. */
    val progress = Animatable(0f)
}

@Composable
fun rememberGlobeAnim(status: ConnectionStatus, server: Server?): GlobeAnimState {
    val g = remember { GlobeAnimState() }
    val userLon = ServerRepository.userLocation.lon.toFloat()

    LaunchedEffect(status, server) {
        when (status) {
            ConnectionStatus.Idle -> {
                launch { g.progress.animateTo(0f, tween(600)) }
                g.yaw.animateTo(g.yaw.value + shortestDelta(g.yaw.value, userLon), tween(900))
                // Tự xoay chậm khi chưa kết nối (~3.6 độ/giây)
                var last = 0L
                while (true) {
                    val now = withFrameNanos { it }
                    if (last != 0L) g.yaw.snapTo(g.yaw.value + 3.6f * (now - last) / 1_000_000_000f)
                    last = now
                }
            }

            ConnectionStatus.Connecting -> {
                val target = server ?: return@LaunchedEffect
                g.progress.snapTo(0f)
                // 1) Xoay tới máy chủ ở xa
                g.yaw.animateTo(
                    g.yaw.value + shortestDelta(g.yaw.value, target.location.lon.toFloat()),
                    tween(800, easing = FastOutSlowInEasing),
                )
                // 2) Kéo đường cung từ máy chủ về phía người dùng, địa cầu xoay theo
                coroutineScope {
                    launch { g.progress.animateTo(1f, tween(1700, easing = FastOutSlowInEasing)) }
                    launch {
                        g.yaw.animateTo(
                            g.yaw.value + shortestDelta(g.yaw.value, userLon),
                            tween(1700, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }

            ConnectionStatus.Connected -> {
                val target = server ?: return@LaunchedEffect
                g.progress.snapTo(1f)
                // Đưa điểm giữa của đường cung vào giữa màn hình để thấy cả hai đầu
                val mid = midLon(userLon, target.location.lon.toFloat())
                g.yaw.animateTo(g.yaw.value + shortestDelta(g.yaw.value, mid), tween(800))
            }
        }
    }
    return g
}

// ---------------------------------------------------------------------------
// Vẽ
// ---------------------------------------------------------------------------

@Composable
fun GlobeCanvas(
    anim: GlobeAnimState,
    user: GeoPoint,
    server: Server?,
    modifier: Modifier = Modifier,
) {
    val dots = remember { buildDots() }
    val pulse by rememberInfiniteTransition(label = "pin").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "pulse",
    )

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.36f
        val r = size.width * 0.44f

        val yawRad = Math.toRadians(anim.yaw.value.toDouble())
        val cosY = cos(yawRad).toFloat()
        val sinY = sin(yawRad).toFloat()

        fun rotate(v: FloatArray): FloatArray = floatArrayOf(
            v[0] * cosY - v[2] * sinY,
            v[1],
            v[2] * cosY + v[0] * sinY,
        )

        // Vầng sáng phía sau
        drawCircle(
            brush = Brush.radialGradient(
                0.48f to FoxyColors.Glow.copy(alpha = 0.45f),
                1f to FoxyColors.Glow.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = r * 1.25f,
            ),
            radius = r * 1.25f,
            center = Offset(cx, cy),
        )

        // Các chấm
        val landSize = 2.1.dp.toPx()
        val seaSize = 1.3.dp.toPx()
        val seaColor = Color(200, 185, 255)
        for (i in dots.x0.indices) {
            val x = dots.x0[i] * cosY - dots.z0[i] * sinY
            val z = dots.z0[i] * cosY + dots.x0[i] * sinY
            if (z < 0f) continue
            val y = dots.y0[i]
            val isLand = dots.land[i]
            val s = if (isLand) landSize else seaSize
            val color = if (isLand) Color.White.copy(alpha = 0.35f + 0.6f * z)
            else seaColor.copy(alpha = 0.12f + 0.25f * z)
            drawRect(color, Offset(cx + x * r - s / 2f, cy - y * r - s / 2f), Size(s, s))
        }

        // Đường cung từ máy chủ về phía người dùng
        val progress = anim.progress.value
        if (server != null && progress > 0.02f) {
            val a = geoVec(user)
            val b = geoVec(server.location)
            val d = acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f))
                .coerceAtLeast(1e-4f)
            val sd = sin(d)
            val glowStroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val lineStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path()
            var drawing = false

            fun flush() {
                if (drawing) {
                    drawPath(path, FoxyColors.Sun.copy(alpha = 0.3f), style = glowStroke)
                    drawPath(path, FoxyColors.Sun, style = lineStroke)
                    path.reset()
                    drawing = false
                }
            }

            val steps = 60
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                if (t > progress) break
                val u = sin((1f - t) * d) / sd
                val v = sin(t * d) / sd
                val lift = 1f + 0.28f * sin(PI.toFloat() * t) // đường cung nhô lên khỏi mặt cầu
                val q = rotate(
                    floatArrayOf(
                        (b[0] * u + a[0] * v) * lift,
                        (b[1] * u + a[1] * v) * lift,
                        (b[2] * u + a[2] * v) * lift,
                    ),
                )
                if (q[2] < -0.1f) { // khuất sau địa cầu
                    flush()
                    continue
                }
                val px = cx + q[0] * r
                val py = cy - q[1] * r
                if (!drawing) {
                    path.moveTo(px, py)
                    drawing = true
                } else {
                    path.lineTo(px, py)
                }
            }
            flush()
        }

        // Hai đầu ghim
        fun pin(p: GeoPoint, color: Color, radiusDp: Float) {
            val q = rotate(geoVec(p))
            if (q[2] < 0f) return
            val center = Offset(cx + q[0] * r, cy - q[1] * r)
            val rad = radiusDp.dp.toPx()
            drawCircle(color, rad, center)
            drawCircle(
                color = color.copy(alpha = 1f - pulse),
                radius = rad + pulse * 16.dp.toPx(),
                center = center,
                style = Stroke(1.5.dp.toPx()),
            )
        }
        pin(user, Color.White, 5f)
        if (server != null && progress > 0.05f) pin(server.location, FoxyColors.Sun, 7f)
    }
}
