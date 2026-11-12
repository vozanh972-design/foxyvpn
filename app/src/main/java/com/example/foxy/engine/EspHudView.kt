package com.example.foxy.engine

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * EspHudView — Vẽ ESP overlay đúng chuẩn Pigboy.
 *
 * Cấu trúc giống bản gốc:
 *   - Ô box (rectangle outline) quanh enemy
 *   - Line từ bottom-center màn hình → enemy
 *   - Label chỉ có khoảng cách (bỏ "PIG BOY ~ Main Account Safe")
 *   - Tất cả màu TRẮNG thay vì xanh lá
 *
 * Fix "lúc có lúc không": postInvalidate() liên tục + snapshot stable
 */
class EspHudView(context: Context) : View(context) {

    @Volatile var espEnabled    = false
    @Volatile var smoothEnabled = true

    // ─── Paint objects (tái sử dụng, không tạo trong onDraw) ──

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 255, 255)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color   = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color   = Color.argb(160, 0, 0, 0)
        style   = Paint.Style.FILL_AND_STROKE
    }

    private val interps  = HashMap<Int, EspPoseInterpolator>()
    private var lastTime = 0L
    private val tmpRect  = RectF()

    // ─── Draw ─────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Luôn post lại dù esp tắt (để bật tức thì)
        postInvalidateDelayed(16)

        if (!espEnabled) return

        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return

        val now   = System.currentTimeMillis()
        val dt    = if (lastTime == 0L) 16L else (now - lastTime).coerceIn(1L, 100L)
        lastTime  = now

        val d     = resources.displayMetrics.density
        val snap  = EspEntityStore.snapshot()
        val lp    = snap.localPlayer ?: return

        val seen  = HashSet<Int>()

        for (entity in snap.entities) {
            val dx = entity.x - lp.x
            val dy = entity.y - lp.y
            val dz = entity.z - lp.z
            val dist = sqrt(dx*dx + dy*dy + dz*dz)
            if (dist < 0.3f || dist > 500f) continue

            val interp = interps.getOrPut(entity.id) { EspPoseInterpolator() }
            val pos    = interp.sample(entity.x, entity.y, entity.z, entity.updatedAt, now, smoothEnabled)

            // Project foot (y) và head (y+1.75m = chiều cao TB nhân vật)
            val foot = PacketProjection.project(W, H, snap, pos[0], pos[1],        pos[2])
            val head = PacketProjection.project(W, H, snap, pos[0], pos[1] + 1.75f, pos[2])

            seen += entity.id

            if (foot == null || head == null) {
                // Ngoài màn → vẽ indicator ở rìa
                drawEdgeArrow(canvas, snap, lp, entity, dist, W, H, d)
                continue
            }

            // ── Tính kích thước box (tỉ lệ theo chiều cao nhân vật) ──
            val screenTop  = min(head[1], foot[1])
            val screenBot  = max(head[1], foot[1])
            val boxH = (screenBot - screenTop).coerceAtLeast(8f * d)
            val boxW = boxH * 0.42f   // Tỉ lệ người (~6:14)
            val cx   = (foot[0] + head[0]) * 0.5f

            tmpRect.set(cx - boxW/2, screenTop, cx + boxW/2, screenBot)

            // ── Lọc box quá nhỏ / quá to ──
            if (boxH < 4f * d || boxH > H * 0.95f) continue

            val alpha = if (dist < 100f) 230 else (230 * (500f - dist) / 400f).toInt().coerceIn(80, 230)

            // ══ 1. VẼ LINE: bottom-center → đỉnh box (giống Pigboy) ══
            linePaint.strokeWidth = 1.2f * d
            linePaint.alpha = alpha
            canvas.drawLine(W * 0.5f, H, cx, screenTop, linePaint)

            // ══ 2. VẼ BOX (outline trắng) ══
            boxPaint.strokeWidth = 1.5f * d
            boxPaint.alpha = alpha
            canvas.drawRect(tmpRect, boxPaint)

            // ══ 3. VẼ HEALTH BAR (bên phải box, dọc) ══
            val hpRatio  = entity.health.coerceIn(0, 200) / 200f
            val barW     = 3f * d
            val barLeft  = tmpRect.right + 2f * d
            val barRight = barLeft + barW
            // Background bar
            fillPaint.color = Color.argb(120, 0, 0, 0)
            canvas.drawRect(barLeft, tmpRect.top, barRight, tmpRect.bottom, fillPaint)
            // HP bar color: xanh lá → vàng → đỏ
            val hpColor = when {
                hpRatio > 0.6f -> Color.rgb(80, 220, 120)
                hpRatio > 0.3f -> Color.rgb(255, 200, 50)
                else           -> Color.rgb(255, 80, 80)
            }
            fillPaint.color = Color.argb(200, Color.red(hpColor), Color.green(hpColor), Color.blue(hpColor))
            val barTop = tmpRect.bottom - boxH * hpRatio
            canvas.drawRect(barLeft, barTop, barRight, tmpRect.bottom, fillPaint)

            // ══ 4. VẼ LABEL: chỉ khoảng cách (bỏ "PIG BOY ~ Main Account Safe") ══
            textPaint.textSize = (10f * d).coerceIn(22f, 36f)
            textPaint.alpha = alpha
            val label = "${dist.toInt()}m"
            // Shadow để dễ đọc
            shadowPaint.textSize = textPaint.textSize
            shadowPaint.strokeWidth = 2.5f * d
            canvas.drawText(label, cx, tmpRect.top - 4f * d, shadowPaint)
            canvas.drawText(label, cx, tmpRect.top - 4f * d, textPaint)

            // ══ 5. VẼ ĐẦU (chấm tròn nhỏ) — giống bản gốc ══
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(head[0], head[1], 3f * d, fillPaint)
        }

        // Dọn interpolators cũ
        interps.keys.retainAll(seen)
    }

    // ─── Edge arrow khi enemy ngoài màn ──────────────────────

    private fun drawEdgeArrow(
        canvas: Canvas,
        snap: EspEntityStore.Snapshot,
        lp: EspEntityStore.Entity,
        entity: EspEntityStore.Entity,
        dist: Float, W: Float, H: Float, d: Float,
    ) {
        val relX = entity.x - lp.x
        val relZ = entity.z - lp.z
        val sin  = sin(snap.yawRadians)
        val cos  = cos(snap.yawRadians)
        val rx   =  relX * cos - relZ * sin
        val ry   =  relX * sin + relZ * cos
        val angle = atan2(rx.toDouble(), ry.toDouble()).toFloat()
        val margin = 20f * d
        val ex = (W/2 + sin(angle) * (W/2 - margin)).coerceIn(margin, W - margin)
        val ey = (H/2 - cos(angle) * (H/2 - margin)).coerceIn(margin, H - margin)

        // Chấm đỏ nhỏ ở rìa + khoảng cách
        fillPaint.color = Color.argb(200, 255, 80, 80)
        canvas.drawCircle(ex, ey, 4f * d, fillPaint)
        textPaint.textSize = 9f * d
        textPaint.alpha = 180
        canvas.drawText("${dist.toInt()}m", ex, ey + 12f * d, textPaint)
    }
}


// ─────────────────────────────────────────────────────────────
// EspPoseInterpolator — Nội suy vị trí giữa 2 keyframe
// ─────────────────────────────────────────────────────────────
class EspPoseInterpolator {
    private var x  = 0f; private var y  = 0f; private var z  = 0f
    private var px = 0f; private var py = 0f; private var pz = 0f
    private var time     = 0L
    private var prevTime = 0L
    private var ready    = false

    fun sample(nx: Float, ny: Float, nz: Float, entityTs: Long, nowMs: Long, smooth: Boolean): FloatArray {
        val jump = (nx-x)*(nx-x) + (ny-y)*(ny-y) + (nz-z)*(nz-z) > 400f
        val stale = nowMs - time > 2000L

        if (!ready || jump || stale) {
            px = nx; py = ny; pz = nz; x = nx; y = ny; z = nz
            prevTime = entityTs; time = entityTs; ready = true
            return floatArrayOf(nx, ny, nz)
        }
        if (entityTs > time) {
            px = x; py = y; pz = z; prevTime = time
            x = nx; y = ny; z = nz; time = entityTs
        }
        val dt = (time - prevTime).coerceIn(1L, 500L)
        val lag = if (smooth) 80L else 0L
        val t   = ((nowMs - lag - prevTime).toFloat() / dt).coerceIn(0f, 1.2f)
        val f   = if (smooth) t * t * (3f - 2f * t) else t  // smoothstep
        return floatArrayOf(px + (x-px)*f, py + (y-py)*f, pz + (z-pz)*f)
    }
}


// ─────────────────────────────────────────────────────────────
// PacketProjection — World 3D → Screen 2D
// ─────────────────────────────────────────────────────────────
object PacketProjection {

    // TPP camera params (Free Fire)
    @Volatile private var camBack = 3.0f
    @Volatile private var camSide = 0.35f
    @Volatile private var camEye  = 1.6f
    @Volatile private var hFovDeg = 91.49f

    fun setCamera(back: Float, side: Float, eye: Float, fovDeg: Float) {
        camBack = back.coerceIn(0.5f, 8f)
        camSide = side.coerceIn(-2f, 2f)
        camEye  = eye.coerceIn(0.5f, 3f)
        hFovDeg = fovDeg.coerceIn(50f, 120f)
    }

    /**
     * Project world point (wx,wy,wz) → screen (sx, sy, depth).
     * Returns null nếu behind camera.
     */
    fun project(W: Float, H: Float, snap: EspEntityStore.Snapshot,
                wx: Float, wy: Float, wz: Float): FloatArray? {
        val lp = snap.localPlayer ?: return null
        if (W <= 0f || H <= 0f) return null

        // Camera direction
        var dx = snap.dirX; var dy = snap.dirY; var dz = snap.dirZ
        if (dx == 0f && dy == 0f && dz == 0f) {
            dx = sin(snap.yawRadians); dy = 0f; dz = cos(snap.yawRadians)
        }
        val dmag = sqrt(dx*dx + dy*dy + dz*dz)
        if (dmag < 1e-5f) return null
        dx /= dmag; dy /= dmag; dz /= dmag

        // Right vector (horizontal only)
        val hMag = sqrt(dx*dx + dz*dz)
        if (hMag < 1e-5f) return null
        val rx =  dz / hMag
        val rz = -dx / hMag

        // Camera position (TPP offset)
        val cx = lp.x - camBack*dx + camSide*rx
        val cy = lp.y + camEye - camBack*dy
        val cz = lp.z - camBack*dz + camSide*rz

        // Delta from camera to point
        val ex = wx - cx; val ey = wy - cy; val ez = wz - cz

        // Depth = dot(forward, delta)
        val depth = dx*ex + dy*ey + dz*ez
        if (depth < 0.3f) return null

        // Screen projection
        val fovHalfTan = tan(Math.toRadians(hFovDeg * 0.5)).toFloat()
        val aspect     = W / H

        val screenX = W * 0.5f + W * 0.5f * (rx*ex + rz*ez) / (depth * aspect * fovHalfTan)

        // Up vector = forward × right
        val ux = dy*rz - dz*(0f) // simplified (ry=0 for horizontal camera)
        val uy = dz*rx - dx*rz   // actually: forward × right
        val uz = dx*(0f) - dy*rx
        // Simpler: up is just (0,1,0) rotated by pitch
        // For Free Fire TPP, approximate up as world-up projected
        val upDotE = ey - dy*(dx*ex + dy*ey + dz*ez) / (dx*dx + dy*dy + dz*dz)
        val screenY = H * 0.5f - H * 0.5f * upDotE / (depth * fovHalfTan)

        if (!screenX.isFinite() || !screenY.isFinite()) return null
        return floatArrayOf(screenX, screenY, depth)
    }

    fun radar(relX: Float, relZ: Float, yawRad: Float): FloatArray {
        val s = sin(yawRad); val c = cos(yawRad)
        return floatArrayOf(relX*c - relZ*s, relX*s + relZ*c)
    }
}
