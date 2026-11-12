package com.example.foxy.engine

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * EspHudView — Custom Android View vẽ ESP overlay.
 *
 * Thay đổi so với Pigboy gốc:
 *   - Line color: TRẮNG thay vì xanh lá
 *   - Bỏ watermark "PIG BOY ~ Main Account Safe"
 *   - Tên feature: "Mắt thần"
 */
class EspHudView(context: Context) : View(context) {

    // Settings (set từ OverlayService)
    @Volatile var espEnabled   = false
    @Volatile var smoothEnabled = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val poseFrames   = HashMap<Int, EspPoseInterpolator>()
    private val smoothFrames = HashMap<Int, FloatArray>()
    private var lastFrame    = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!espEnabled) {
            postInvalidateDelayed(16)
            return
        }
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0 || H <= 0) { postInvalidateDelayed(16); return }

        val now = System.currentTimeMillis()
        val frameDt = if (lastFrame == 0L) 100L else now - lastFrame
        lastFrame = now

        val snap = EspEntityStore.snapshot()
        drawEsp(canvas, snap, W, H, now, frameDt)

        postInvalidateDelayed(16)   // ~60fps
    }

    private fun drawEsp(
        canvas: Canvas, snap: EspEntityStore.Snapshot,
        W: Float, H: Float, now: Long, frameDt: Long,
    ) {
        val lp = snap.localPlayer ?: return
        val onScreen = HashSet<Int>()

        for (entity in snap.entities) {
            val dx = entity.x - lp.x
            val dy = entity.y - lp.y
            val dz = entity.z - lp.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 0.5f) continue

            // Smooth position
            val interp = poseFrames.getOrPut(entity.id) { EspPoseInterpolator() }
            val pos = interp.sample(entity.x, entity.y, entity.z, entity.updatedAt, now, smoothEnabled)

            // Project foot (y) và head (y+1.8m)
            val foot = PacketProjection.project(W, H, snap, pos[0], pos[1],       pos[2])
            val head = PacketProjection.project(W, H, snap, pos[0], pos[1] + 1.8f, pos[2])

            if (foot == null || head == null || foot[0] < 0 || foot[0] > W || head[1] < 0 || foot[1] > H) {
                drawEdgeIndicator(canvas, snap, lp, entity, dist, W, H)
                continue
            }

            onScreen += entity.id

            // Smooth box
            val prevBox = smoothFrames[entity.id]
            val rawBox  = floatArrayOf(foot[0], foot[1], head[0], head[1])
            val box = smoothBox(prevBox, rawBox, frameDt)
            smoothFrames[entity.id] = box

            val cx      = (box[0] + box[2]) * 0.5f
            val yTop    = min(box[1], box[3])
            val yBot    = max(box[1], box[3])
            val boxH    = yBot - yTop
            val boxW    = 0.46f * boxH
            val midY    = (yTop + yBot) * 0.5f

            val rect = RectF(cx - boxW / 2, midY - boxH / 2, cx + boxW / 2, midY + boxH / 2)

            // ═══ VẼ ESP BOX (màu TRẮNG) ═══
            paint.style  = Paint.Style.STROKE
            paint.strokeWidth = dp(1.4f)
            paint.color  = Color.argb(220, 255, 255, 255)   // ← TRẮNG
            canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)

            // ═══ VẼ LINE từ trung tâm màn → đầu (màu TRẮNG) ═══
            paint.strokeWidth = dp(1f)
            paint.color  = Color.argb(180, 255, 255, 255)   // ← TRẮNG
            canvas.drawLine(W * 0.5f, dp(44f), head[0], head[1], paint)

            // ═══ VẼ HEALTH BAR (bên phải box) ═══
            val hpRatio = entity.health.coerceIn(0, 200) / 200f
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(150, 18, 18, 24)
            canvas.drawRoundRect(RectF(rect.right - dp(5f), rect.top, rect.right - dp(2f), rect.bottom), dp(2f), dp(2f), paint)
            paint.color = if (hpRatio > 0.45f) Color.rgb(83, 222, 154) else Color.rgb(255, 105, 125)
            val barH = hpRatio * rect.height()
            canvas.drawRoundRect(RectF(rect.right - dp(5f), rect.bottom - barH, rect.right - dp(2f), rect.bottom), dp(2f), dp(2f), paint)

            // ═══ VẼ LABEL: TÊN + KHOẢNG CÁCH (không watermark) ═══
            textPaint.textSize  = dp(10f)
            textPaint.color     = Color.argb(220, 255, 255, 255)
            val label = if (entity.name.isNotEmpty()) "${entity.name}  ${dist.toInt()}m"
                        else "${dist.toInt()}m"
            canvas.drawText(label, cx, rect.top - dp(5f), textPaint)
        }

        // Dọn dẹp interpolators cho entities đã biến mất
        poseFrames.keys.retainAll(onScreen)
        smoothFrames.keys.retainAll(onScreen)
    }

    // ─── Edge indicator khi entity ngoài màn ─────────────────

    private fun drawEdgeIndicator(
        canvas: Canvas, snap: EspEntityStore.Snapshot,
        lp: EspEntityStore.Entity, entity: EspEntityStore.Entity,
        dist: Float, W: Float, H: Float,
    ) {
        val relX = entity.x - lp.x
        val relZ = entity.z - lp.z
        val sin  = sin(snap.yawRadians)
        val cos  = cos(snap.yawRadians)
        val rx   =  relX * cos - relZ * sin
        val ry   =  relX * sin + relZ * cos
        val angle = atan2(rx, ry)

        val edgeX = W / 2 + sin(angle) * (W / 2 - dp(24f))
        val edgeY = H / 2 - cos(angle) * (H / 2 - dp(24f))

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 255, 80, 80)
        canvas.drawCircle(edgeX, edgeY, dp(3f), paint)

        textPaint.textSize = dp(9f)
        textPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawText("${dist.toInt()}m", edgeX, edgeY + dp(12f), textPaint)
    }

    // ─── Smooth box lerp ──────────────────────────────────────

    private fun smoothBox(prev: FloatArray?, cur: FloatArray, frameDt: Long): FloatArray {
        if (prev == null || !smoothEnabled) return cur
        val alpha = (frameDt / 50f).coerceIn(0f, 1f)
        return FloatArray(4) { i -> prev[i] + (cur[i] - prev[i]) * alpha }
    }

    private fun dp(v: Float): Float = resources.displayMetrics.density * v
}

/**
 * EspPoseInterpolator — Nội suy vị trí giữa hai keyframes.
 * Tránh ESP box giật khi game update 20Hz nhưng màn hình 60Hz.
 */
class EspPoseInterpolator {
    private var x  = 0f; private var y  = 0f; private var z  = 0f; private var time = 0L
    private var px = 0f; private var py = 0f; private var pz = 0f; private var prevTime = 0L
    private var ready = false

    fun sample(nx: Float, ny: Float, nz: Float, entityTime: Long, now: Long, smooth: Boolean): FloatArray {
        val shouldReset = !ready || entityTime < time || (now - time) > 1000L ||
            (nx-x)*(nx-x)+(ny-y)*(ny-y)+(nz-z)*(nz-z) > 25f

        if (shouldReset) {
            x=nx; y=ny; z=nz; px=nx; py=ny; pz=nz
            time=entityTime; prevTime=entityTime; ready=true
            return floatArrayOf(nx, ny, nz)
        }
        if (entityTime > time) {
            px=x; py=y; pz=z; prevTime=time
            x=nx; y=ny; z=nz; time=entityTime
        }
        val dt = time - prevTime
        if (dt <= 0L || dt > 250L) return floatArrayOf(x, y, z)
        val offset = if (smooth) 100L else 80L
        val t = ((now - offset - prevTime).toFloat() / dt).coerceIn(0f, 1f)
        val f = if (smooth) (3 - 2 * t) * t * t else t
        return floatArrayOf(px + (x-px)*f, py + (y-py)*f, pz + (z-pz)*f)
    }
}

/**
 * PacketProjection — Chuyển tọa độ 3D world → 2D screen.
 * Dùng camera model TPP của game.
 */
object PacketProjection {

    data class Camera(
        val back: Float = 3.0f,
        val side: Float = 0.35f,
        val eye:  Float = 1.6f,
        val hfov: Float = 91.49f,
    )

    @Volatile var camera = Camera()

    fun setCamera(back: Float, side: Float, eye: Float, fovDeg: Float) {
        camera = Camera(
            back.coerceIn(0f, 6f),
            side.coerceIn(-2f, 2f),
            eye.coerceIn(0.5f, 3f),
            fovDeg.coerceIn(50f, 120f),
        )
    }

    fun project(W: Float, H: Float, snap: EspEntityStore.Snapshot, wx: Float, wy: Float, wz: Float): FloatArray? {
        val lp = snap.localPlayer ?: return null
        if (W <= 0 || H <= 0) return null

        var dirX = snap.dirX; var dirY = snap.dirY; var dirZ = snap.dirZ
        if (dirX == 0f && dirY == 0f && dirZ == 0f) {
            val yaw = snap.yawRadians
            dirX = sin(yaw); dirY = 0f; dirZ = cos(yaw)
        }
        val mag = sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ)
        if (mag < 1e-4f) return null
        dirX /= mag; dirY /= mag; dirZ /= mag

        val hMag = sqrt(dirZ*dirZ + dirX*dirX)
        if (hMag < 1e-4f) return null
        val rightX =  dirZ / hMag
        val rightZ = -dirX / hMag

        val cam = camera
        val camX = lp.x - cam.back * dirX + cam.side * rightX
        val camY = lp.y + cam.eye  - cam.back * dirY
        val camZ = lp.z - cam.back * dirZ + cam.side * rightZ

        val dx = wx - camX; val dy = wy - camY; val dz = wz - camZ
        val depth = dirX*dx + dirY*dy + dirZ*dz
        if (depth < 0.5f) return null

        val fovRad   = Math.toRadians(cam.hfov.toDouble())
        val fovScale = tan(fovRad * 0.5) / 1.7777777777777777

        val sX = ((rightX*dx + rightZ*dz) / depth / (W/H * fovScale)).toFloat() * W * 0.5f + W * 0.5f
        val dotUp = ((-rightZ * dirY) * dx + (-dirY * rightX) * dz) / depth
        val sY = H * 0.5f - (dotUp / fovScale).toFloat() * H * 0.5f

        if (!sX.isFinite() || !sY.isFinite()) return null
        if (sX < -W || sX > 2*W || sY < -H || sY > 2*H) return null
        return floatArrayOf(sX, sY, depth)
    }

    fun radar(relX: Float, relZ: Float, yawRad: Float): FloatArray {
        val s = sin(yawRad); val c = cos(yawRad)
        return floatArrayOf(relX*c - relZ*s, relX*s + relZ*c)
    }
}
