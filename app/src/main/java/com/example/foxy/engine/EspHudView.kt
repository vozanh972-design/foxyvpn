package com.example.foxy.engine

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * EspHudView — Rewrite từ EspHudView.java gốc (Pigboy).
 *
 * Thay đổi duy nhất so với bản gốc:
 *   - Line color: trắng thay vì xanh lá (0,255,0)
 *   - Box color:  trắng thay vì xanh lá
 *   - Label: bỏ "PIG BOY ~ Main Account Safe", chỉ hiện khoảng cách "Xm"
 *
 * Logic drawing 100% từ code gốc:
 *   - Line từ (W/2, dp(44)) → đầu entity   [gốc: 0.5f*width, dp(44)]
 *   - Box: RoundRect, 0.46*height x height, corner dp(5)
 *   - Health bar: bên phải box, dọc, xanh/đỏ
 *   - invalidate() chỉ gọi khi espEnabled = true
 */
class EspHudView(context: Context) : View(context) {

    // Các field giống bản gốc
    @Volatile var espEnabled   = false
    @Volatile var smoothEnabled = true
    @Volatile var boxAlpha   = 220  // 0-255 (gốc dùng 100→255)
    @Volatile var lineAlpha  = 200
    @Volatile var radarEnabled = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poseFrames:   HashMap<Int, EspPoseInterpolator> = HashMap()
    private val smoothFrames: HashMap<Int, FloatArray>          = HashMap()
    private var lastFrame = 0L

    // ─── onDraw: giống bản gốc (line 680-700 EspHudView.java) ───

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snap = EspEntityStore.snapshot()
        if (snap.localPlayer != null) {
            if (espEnabled) {
                drawEsp(canvas, snap)
            }
            // radarEnabled: bỏ qua vì overlay riêng
        } else {
            // Không có local player → clear caches
            smoothFrames.clear()
            poseFrames.clear()
            lastFrame = 0L
        }
        // Chỉ invalidate khi đang bật — ĐÚNG như bản gốc
        if (espEnabled) {
            invalidate()
        }
    }

    // ─── drawEsp: từ lines 81-166 EspHudView.java ────────────────

    private fun drawEsp(canvas: Canvas, snapshot: EspEntityStore.Snapshot) {
        val W   = width.toFloat()
        val H   = height.toFloat()
        if (W <= 0f || H <= 0f) return

        val now = System.currentTimeMillis()
        val dt  = if (lastFrame == 0L) 100L else now - lastFrame
        lastFrame = now

        val seen = HashSet<Int>()

        for (entity in snapshot.entities) {
            // Không filter dead — user muốn thấy cả knocked players

            val dx = entity.x - (snapshot.localPlayer?.x ?: 0f)
            val dy = entity.y - (snapshot.localPlayer?.y ?: 0f)
            val dz = entity.z - (snapshot.localPlayer?.z ?: 0f)
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 0.5f) continue

            // Smooth interpolation (giống bản gốc)
            val interp = poseFrames.getOrPut(entity.id) { EspPoseInterpolator() }
            val pos = interp.sample(entity.x, entity.y, entity.z, entity.updatedAt, now, smoothEnabled)

            // Project foot và head (y+1.8f — giống bản gốc line 108)
            val foot = PacketProjection.project(W, H, snapshot, pos[0], pos[1],        pos[2])
            val head = PacketProjection.project(W, H, snapshot, pos[0], pos[1] + 1.8f,  pos[2])

            if (foot == null || head == null) {
                drawEdge(canvas, snapshot, entity, dist, W, H)
                continue
            }

            // Check on-screen (giống line 111)
            if (foot[0] < 0 || foot[0] > W || head[1] < 0 || foot[1] > H) {
                drawEdge(canvas, snapshot, entity, dist, W, H)
                continue
            }

            seen += entity.id

            // Smooth frames (giống line 115-120)
            val rawPts = floatArrayOf(foot[0], foot[1], head[0], head[1])
            val prevPts = smoothFrames[entity.id]
            val pts = if (prevPts != null && smoothEnabled) {
                val alpha = (dt / 50f).coerceIn(0f, 1f)
                FloatArray(4) { i -> prevPts[i] + (rawPts[i] - prevPts[i]) * alpha }
            } else rawPts
            smoothFrames[entity.id] = pts

            // Tính box (giống lines 121-127)
            val centerX = (pts[0] + pts[2]) * 0.5f
            val yTop    = min(pts[1], pts[3])
            val yBot    = max(pts[1], pts[3])
            val boxH    = clamp(yBot - yTop, 2f, 3f * H)
            val boxW    = 0.46f * boxH
            val centerY = (yTop + yBot) * 0.5f
            val rectF   = RectF(centerX - boxW * 0.5f, centerY - boxH * 0.5f, centerX + boxW * 0.5f, centerY + boxH * 0.5f)

            // ══ 1. VẼ BOX (màu TRẮNG thay vì xanh) — line 128-131 ══
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.4f)
            paint.color = Color.argb(boxAlpha, 255, 255, 255)   // ← TRẮNG (gốc: 0,255,0)
            canvas.drawRoundRect(rectF, dp(5f), dp(5f), paint)

            // ══ 2. VẼ LINE từ (W/2, dp(44)) → head — line 132-134 ══
            // QUAN TRỌNG: gốc là dp(44) từ TOP, không phải từ bottom!
            paint.strokeWidth = dp(1f)
            paint.color = Color.argb(lineAlpha, 255, 255, 255)  // ← TRẮNG (gốc: 0,255,0)
            canvas.drawLine(0.5f * W, dp(44f), head[0], head[1], paint)

            // ══ 3. VẼ HEALTH BAR (bên phải box, dọc) — lines 136-141 ══
            val hpRatio = (entity.health.coerceIn(0, 200).toFloat() / 200f).coerceAtLeast(0f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(150, 18, 18, 24)
            canvas.drawRoundRect(
                RectF(rectF.right - dp(5f), rectF.top, rectF.right - dp(2f), rectF.bottom),
                dp(2f), dp(2f), paint
            )
            paint.color = if (hpRatio > 0.45f) Color.rgb(83, 222, 154) else Color.rgb(255, 105, 125)
            val barTop = rectF.bottom - hpRatio * rectF.height()
            canvas.drawRoundRect(
                RectF(rectF.right - dp(5f), barTop, rectF.right - dp(2f), rectF.bottom),
                dp(2f), dp(2f), paint
            )

            // ══ 4. VẼ LABEL: CHỈ KHOẢNG CÁCH — line 142-146 ══
            // Gốc: "PIG BOY ~ Main Account Safe " + dist + "m"
            // Của chúng ta: chỉ "${dist.toInt()}m"
            text.color  = Color.argb(lineAlpha, 255, 255, 255)
            text.textAlign = Paint.Align.CENTER
            text.textSize = dp(10f)
            val label = "${dist.toInt()}m"
            canvas.drawText(label, centerX, rectF.top - dp(5f), text)
        }

        // Dọn cache cũ (line 152-153)
        poseFrames.keys.retainAll(seen)
        smoothFrames.keys.retainAll(seen)
    }

    // ─── drawEdge — giống lines 64-79 ────────────────────────────

    private fun drawEdge(
        canvas: Canvas, snapshot: EspEntityStore.Snapshot,
        entity: EspEntityStore.Entity, dist: Float, W: Float, H: Float,
    ) {
        val lp = snapshot.localPlayer ?: return
        val relX = entity.x - lp.x
        val relZ = entity.z - lp.z
        val angle = atan2(
            (relX * cos(snapshot.yawRadians) - relZ * sin(snapshot.yawRadians)).toDouble(),
            (relX * sin(snapshot.yawRadians) + relZ * cos(snapshot.yawRadians)).toDouble()
        ).toFloat()

        val ex = (W * 0.5f + sin(angle) * (W * 0.5f - dp(24f))).coerceIn(dp(8f), W - dp(8f))
        val ey = (H * 0.5f - cos(angle) * (H * 0.5f - dp(24f))).coerceIn(dp(8f), H - dp(8f))

        // Chấm đỏ nhỏ ở rìa (giống bản gốc: FILL circle màu đỏ)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(lineAlpha, 255, 0, 0)  // gốc: boxAlpha, 255, 0, 0
        canvas.drawCircle(ex, ey, dp(3f), paint)

        // Khoảng cách
        text.textSize = dp(9f)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.argb(lineAlpha, 255, 255, 255)
        canvas.drawText("${dist.toInt()}m", ex, ey + dp(12f), text)
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private fun dp(v: Float) = resources.displayMetrics.density * v
    private fun clamp(v: Float, min: Float, max: Float) = v.coerceIn(min, max)

    /** Gọi khi bật esp từ bên ngoài để restart drawing loop */
    fun enable() { espEnabled = true; invalidate() }
    fun disable() { espEnabled = false }
}

// ─────────────────────────────────────────────────────────────────
// EspPoseInterpolator — Nội suy vị trí giữa 2 keyframe (giống gốc)
// ─────────────────────────────────────────────────────────────────
class EspPoseInterpolator {
    private var x = 0f; private var y = 0f; private var z = 0f
    private var px = 0f; private var py = 0f; private var pz = 0f
    private var time = 0L; private var prevTime = 0L
    private var ready = false

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

// ─────────────────────────────────────────────────────────────────
// PacketProjection — Từ PacketProjection.java gốc
// Camera TPP: back=3.0, side=0.35, eye=1.6, hfov=91.49°
// ─────────────────────────────────────────────────────────────────
object PacketProjection {

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

    fun project(W: Float, H: Float, snap: EspEntityStore.Snapshot,
                wx: Float, wy: Float, wz: Float): FloatArray? {
        val lp = snap.localPlayer ?: return null
        if (W <= 0f || H <= 0f) return null

        var dx = snap.dirX; var dy = snap.dirY; var dz = snap.dirZ
        if (dx == 0f && dy == 0f && dz == 0f) {
            dx = sin(snap.yawRadians); dy = 0f; dz = cos(snap.yawRadians)
        }
        val dmag = sqrt(dx*dx + dy*dy + dz*dz)
        if (dmag < 1e-5f) return null
        dx /= dmag; dy /= dmag; dz /= dmag

        val hMag = sqrt(dx*dx + dz*dz)
        if (hMag < 1e-5f) return null
        val rx =  dz / hMag
        val rz = -dx / hMag

        val cx = lp.x - camBack*dx + camSide*rx
        val cy = lp.y + camEye - camBack*dy
        val cz = lp.z - camBack*dz + camSide*rz

        val ex = wx - cx; val ey = wy - cy; val ez = wz - cz
        val depth = dx*ex + dy*ey + dz*ez
        if (depth < 0.5f) return null

        // vFovHalfTan = tan(hFov/2) / 1.7778 (matches original line 76)
        val vFovHalfTan = (tan(Math.toRadians(hFovDeg * 0.5)) / 1.7777777777777778).toFloat()
        val aspect = W / H

        // Right projection (screenX) — denominator = aspect * vFovHalfTan (= hFovHalfTan cho 16:9)
        val screenX = W * 0.5f + W * 0.5f * (rx*ex + rz*ez) / (depth * maxOf(0.05f, aspect * vFovHalfTan))

        // Up vector = look × right (cross product, NOT Gram-Schmidt)
        // upX = dy*rz,  upY = dz*rx - dx*rz,  upZ = -dy*rx
        val upX = dy * rz
        val upY = dz * rx - dx * rz
        val upZ = -dy * rx
        val upDot = upX*ex + upY*ey + upZ*ez

        // screenY denominator = vFovHalfTan (vertical FOV)
        val screenY = H * 0.5f - H * 0.5f * upDot / (depth * maxOf(0.05f, vFovHalfTan))

        if (!screenX.isFinite() || !screenY.isFinite()) return null
        return floatArrayOf(screenX, screenY, depth)
    }

    fun radar(relX: Float, relZ: Float, yawRad: Float): FloatArray {
        val s = sin(yawRad); val c = cos(yawRad)
        return floatArrayOf(relX*c - relZ*s, relX*s + relZ*c)
    }
}
