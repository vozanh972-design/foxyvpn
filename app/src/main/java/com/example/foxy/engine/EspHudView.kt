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
    // Alpha = 255 (fully opaque) — gốc dùng opacity(100) = min(255, round(100*2.55)) = 255
    @Volatile var boxAlpha   = 255
    @Volatile var lineAlpha  = 255
    @Volatile var radarEnabled = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poseFrames:   HashMap<Int, EspPoseInterpolator> = HashMap()
    private val smoothFrames: HashMap<Int, FloatArray>          = HashMap()
    private var lastFrame = 0L

    // Cache localPlayer cuối cùng biết — giúp ESP không tắt lúc chết/hồi sinh
    @Volatile private var lastKnownLocalPlayer: EspEntityStore.Entity? = null

    // ─── onDraw: giống bản gốc (line 680-700 EspHudView.java) ───

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snap = EspEntityStore.snapshot()

        // Gốc: drawEsp khi localPlayer != null
        // Ta thêm: dùng last known khi localPlayer tạm null (vd: lúc chết chờ hồi sinh)
        val effectiveSnap = if (snap.localPlayer != null) {
            lastKnownLocalPlayer = snap.localPlayer
            snap
        } else if (lastKnownLocalPlayer != null) {
            snap.copy(localPlayer = lastKnownLocalPlayer)
        } else {
            null
        }

        if (effectiveSnap != null) {
            if (espEnabled) drawEsp(canvas, effectiveSnap)
        } else {
            // Không có localPlayer nào cả → clear caches (giống gốc)
            smoothFrames.clear()
            poseFrames.clear()
            lastFrame = 0L
        }
        // Chỉ invalidate khi đang bật — ĐÚNG như bản gốc (espEnabled || radarEnabled)
        if (espEnabled) invalidate()
    }

    // ─── drawEsp: Skeleton ESP — xuyên tường, xa=xanh, gần=đỏ ──

    private fun drawEsp(canvas: Canvas, snapshot: EspEntityStore.Snapshot) {
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return

        val now = System.currentTimeMillis()
        val dt  = if (lastFrame == 0L) 100L else now - lastFrame
        lastFrame = now

        val seen = HashSet<Int>()
        val localTeam = snapshot.localPlayer?.team ?: -1

        for (entity in snapshot.entities) {
            val dx   = entity.x - (snapshot.localPlayer?.x ?: 0f)
            val dy   = entity.y - (snapshot.localPlayer?.y ?: 0f)
            val dz   = entity.z - (snapshot.localPlayer?.z ?: 0f)
            val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 0.5f) continue

            // Smooth interpolation vị trí
            val interp = poseFrames.getOrPut(entity.id) { EspPoseInterpolator() }
            val pos = interp.sample(entity.x, entity.y, entity.z, entity.updatedAt, now, smoothEnabled)
            val ex = pos[0]; val ey = pos[1]; val ez = pos[2]

            // ── Xác định màu skeleton dựa trên trạng thái + khoảng cách ──
            val isTeammate = localTeam >= 0 && entity.team == localTeam && entity.team != 0
            val isKnocked  = entity.knocked
            val alpha = boxAlpha

            val skelColor = when {
                isKnocked  -> Color.argb(alpha, 255, 165,   0)  // Cam = knocked
                isTeammate -> Color.argb(alpha,  80, 200, 255)  // Cyan = teammate
                dist < 50f -> Color.argb(alpha, 255,  50,  50)  // Đỏ = địch gần (<50m)
                dist < 100f-> Color.argb(alpha, 255, 200,  50)  // Vàng = địch trung (50-100m)
                else       -> Color.argb(alpha,  50, 100, 255)  // Xanh = địch xa (>100m)
            }

            // Helper: project world → floatArrayOf(sx, sy), null nếu sau camera
            fun proj(wx: Float, wy: Float, wz: Float): FloatArray? =
                PacketProjection.project(W, H, snapshot, wx, wy, wz)?.let {
                    floatArrayOf(it[0], it[1])
                }

            // ── Project 11 điểm xương skeleton ──────────────────────────
            // Toạ độ world: (ex, ey, ez) là chân entity, ey+N là từng khớp
            val sHEAD    = proj(ex,        ey + 1.75f, ez)
            val sNECK    = proj(ex,        ey + 1.55f, ez)
            val sCHEST   = proj(ex,        ey + 1.35f, ez)
            val sBELLY   = proj(ex,        ey + 1.00f, ez)
            val sPELVIS  = proj(ex,        ey + 0.75f, ez)
            val sLSHOULD = proj(ex - 0.3f, ey + 1.4f,  ez)
            val sRSHOULD = proj(ex + 0.3f, ey + 1.4f,  ez)
            val sLHAND   = proj(ex - 0.5f, ey + 0.9f,  ez)
            val sRHAND   = proj(ex + 0.5f, ey + 0.9f,  ez)
            val sLFOOT   = proj(ex - 0.2f, ey + 0.0f,  ez)
            val sRFOOT   = proj(ex + 0.2f, ey + 0.0f,  ez)

            // Nếu không project được đầu → vẽ chấm rìa thay thế
            if (sHEAD == null) {
                drawEdge(canvas, snapshot, entity, dist, W, H)
                continue
            }
            val headScrX = sHEAD[0]
            val headScrY = sHEAD[1]
            seen += entity.id

            // Smooth frames cache (dùng head + foot)
            val footScrX = sLFOOT?.get(0) ?: (sRFOOT?.get(0) ?: headScrX)
            val footScrY = sLFOOT?.get(1) ?: (sRFOOT?.get(1) ?: headScrY + 80f)
            smoothFrames[entity.id] = floatArrayOf(footScrX, footScrY, headScrX, headScrY)

            // ── Setup Paint cho skeleton ───────────────────────────────
            paint.style       = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color       = skelColor
            paint.strokeCap   = Paint.Cap.ROUND
            paint.strokeJoin  = Paint.Join.ROUND

            // Helper vẽ đoạn xương A → B
            fun bone(a: FloatArray?, b: FloatArray?) {
                if (a == null || b == null) return
                canvas.drawLine(a[0], a[1], b[0], b[1], paint)
            }

            // ══ VẼ SKELETON ══════════════════════════════════════════
            // Cột sống: HEAD → NECK → CHEST → BELLY → PELVIS
            bone(sHEAD,   sNECK)
            bone(sNECK,   sCHEST)
            bone(sCHEST,  sBELLY)
            bone(sBELLY,  sPELVIS)

            // Tay trái: CHEST → L_SHOULD → L_HAND
            bone(sCHEST,  sLSHOULD)
            bone(sLSHOULD, sLHAND)

            // Tay phải: CHEST → R_SHOULD → R_HAND
            bone(sCHEST,  sRSHOULD)
            bone(sRSHOULD, sRHAND)

            // Chân trái: PELVIS → L_FOOT
            bone(sPELVIS, sLFOOT)

            // Chân phải: PELVIS → R_FOOT
            bone(sPELVIS, sRFOOT)

            // Vòng tròn đầu (skull)
            if (sHEAD != null && sNECK != null) {
                val headRadius = kotlin.math.hypot(sHEAD[0] - sNECK[0], sHEAD[1] - sNECK[1]) * 0.5f
                if (headRadius in 1f..60f) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(1.5f)
                    canvas.drawCircle(sHEAD[0], sHEAD[1], headRadius.coerceIn(3f, 24f), paint)
                }
            }

            // ══ HP BAR (bên trái skeleton, dọc từ head→foot) ════════
            if (sHEAD != null && sLFOOT != null) {
                val barLeft  = minOf(sHEAD[0], sLFOOT[0]) - dp(8f)
                val barRight = barLeft + dp(3f)
                val barTop   = sHEAD[1]
                val barBot   = sLFOOT[1]
                if (barBot > barTop + 2f) {
                    val hpRatio = entity.hpRatio
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(160, 18, 18, 24)
                    canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBot), dp(2f), dp(2f), paint)
                    paint.color = when {
                        hpRatio > 0.6f -> Color.rgb(60, 220, 120)   // Xanh lá
                        hpRatio > 0.3f -> Color.rgb(255, 200, 40)   // Vàng
                        else           -> Color.rgb(255, 70,  70)   // Đỏ
                    }
                    val hpFillTop = barBot - hpRatio * (barBot - barTop)
                    canvas.drawRoundRect(RectF(barLeft, hpFillTop, barRight, barBot), dp(2f), dp(2f), paint)
                }
            }

            // ══ TÊN ĐỊCH (phía trên đầu skeleton) ══════════════════
            val nameScrX = sHEAD?.get(0) ?: headScrX
            val nameScrY = (sHEAD?.get(1) ?: headScrY) - dp(16f)

            val displayName = entity.name.ifBlank { "?${entity.id}" }.let {
                if (it.length > 14) it.take(12) + "…" else it
            }
            text.typeface  = android.graphics.Typeface.DEFAULT_BOLD
            text.textSize  = dp(9f)
            text.textAlign = Paint.Align.CENTER
            text.setShadowLayer(dp(2f), 0f, dp(1f), Color.BLACK)
            text.color     = skelColor
            canvas.drawText(displayName, nameScrX, nameScrY, text)

            // ══ KHOẢNG CÁCH + WEAPON (phía trên tên) ═══════════════
            text.typeface  = android.graphics.Typeface.DEFAULT
            text.textSize  = dp(8f)
            text.setShadowLayer(dp(1.5f), 0f, dp(0.8f), Color.BLACK)
            text.color     = Color.argb(lineAlpha, 200, 200, 200)
            val wLabel     = entity.weaponLabel
            val infoLabel  = if (wLabel.isNotEmpty()) "${dist.toInt()}m · $wLabel" else "${dist.toInt()}m"
            canvas.drawText(infoLabel, nameScrX, nameScrY - dp(10f), text)
            text.clearShadowLayer()

            // ══ KNOCKED BADGE ════════════════════════════════════════
            if (isKnocked) {
                val footScrYn = sLFOOT?.get(1) ?: (headScrY + 80f)
                text.typeface  = android.graphics.Typeface.DEFAULT_BOLD
                text.textSize  = dp(7.5f)
                text.textAlign = Paint.Align.CENTER
                text.setShadowLayer(dp(2f), 0f, dp(1f), Color.BLACK)
                text.color     = Color.argb(220, 255, 165, 0)
                canvas.drawText("KNOCKED", nameScrX, footScrYn + dp(12f), text)
                text.clearShadowLayer()
            }
        }

        poseFrames.keys.retainAll(seen)
        smoothFrames.keys.retainAll(seen)
    }

    // ─── drawEdge: chấm rìa màn hình với màu team ────────────────

    private fun drawEdge(
        canvas: Canvas, snapshot: EspEntityStore.Snapshot,
        entity: EspEntityStore.Entity, dist: Float, W: Float, H: Float,
    ) {
        val lp = snapshot.localPlayer ?: return
        val localTeam = lp.team
        val isTeammate = localTeam >= 0 && entity.team == localTeam && entity.team != 0
        val isKnocked  = entity.knocked

        val relX  = entity.x - lp.x
        val relZ  = entity.z - lp.z
        val angle = atan2(
            (relX * cos(snapshot.yawRadians) - relZ * sin(snapshot.yawRadians)).toDouble(),
            (relX * sin(snapshot.yawRadians) + relZ * cos(snapshot.yawRadians)).toDouble()
        ).toFloat()

        val ex = (W * 0.5f + sin(angle) * (W * 0.5f - dp(24f))).coerceIn(dp(8f), W - dp(8f))
        val ey = (H * 0.5f - cos(angle) * (H * 0.5f - dp(24f))).coerceIn(dp(8f), H - dp(8f))

        // Màu chấm theo team
        val dotColor = when {
            isKnocked  -> Color.argb(lineAlpha, 255, 165,  0)  // Cam = knocked
            isTeammate -> Color.argb(lineAlpha,  80, 160, 255) // Xanh = teammate
            else       -> Color.argb(lineAlpha, 255,  60,  60) // Đỏ = địch
        }
        paint.style = Paint.Style.FILL
        paint.color = dotColor
        canvas.drawCircle(ex, ey, dp(3.5f), paint)

        // Viền trắng mỏng quanh chấm
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = dp(0.8f)
        paint.color       = Color.argb(120, 255, 255, 255)
        canvas.drawCircle(ex, ey, dp(3.5f), paint)

        // Khoảng cách + tên ngắn
        text.typeface  = android.graphics.Typeface.DEFAULT
        text.textSize  = dp(8.5f)
        text.textAlign = Paint.Align.CENTER
        text.setShadowLayer(dp(1.5f), 0f, dp(1f), Color.BLACK)
        text.color     = Color.argb(lineAlpha, 220, 220, 220)
        val nameShort  = entity.name.take(8).ifBlank { "?${entity.id}" }
        canvas.drawText("${dist.toInt()}m", ex, ey + dp(13f), text)
        canvas.drawText(nameShort, ex, ey + dp(22f), text)
        text.clearShadowLayer()
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
// EspPoseInterpolator — Copy y chang tu FULL_LOGIC/04_ESP_RENDER.java
class EspPoseInterpolator {
    private var x = 0f; private var y = 0f; private var z = 0f
    private var px = 0f; private var py = 0f; private var pz = 0f
    private var time = 0L; private var previousTime = 0L
    private var ready = false

    fun sample(newX: Float, newY: Float, newZ: Float,
               entityTime: Long, now: Long, smooth: Boolean): FloatArray {

        // === RESET CONDITIONS (copy tu FULL_LOGIC) ===
        val shouldReset = !ready
            || entityTime < this.time
            || (now - this.time) > 1000L           // Gap > 1 giay (entity mat tich)
            || dist2(newX-x, newY-y, newZ-z) > 625f // Gốc line 181: sqrt(dist2) > 25 = dist > 25m (25²=625)

        if (shouldReset) {
            px = newX; x = newX
            py = newY; y = newY
            pz = newZ; z = newZ
            previousTime = entityTime; time = entityTime
            ready = true
            return floatArrayOf(newX, newY, newZ)
        }

        // === UPDATE KEYFRAME ===
        if (entityTime > this.time) {
            px = x; py = y; pz = z; previousTime = this.time
            x = newX; y = newY; z = newZ; this.time = entityTime
        }

        // === INTERPOLATE ===
        val dt = this.time - this.previousTime
        if (dt <= 0 || dt > 250L) {
            return floatArrayOf(this.x, this.y, this.z)
        }

        // Delay offset: smooth=100ms, linear=80ms (copy tu FULL_LOGIC)
        val offset = if (smooth) 100L else 80L
        var t = (now - offset - this.previousTime).toFloat() / dt.toFloat()
        t = t.coerceIn(0f, 1f)  // Clamp [0,1] nhu FULL_LOGIC

        // Smooth-step hoac linear
        val f = if (smooth) (3f - 2f * t) * t * t else t

        return floatArrayOf(
            px + (x - px) * f,
            py + (y - py) * f,
            pz + (z - pz) * f
        )
    }

    private fun dist2(dx: Float, dy: Float, dz: Float) = dx*dx + dy*dy + dz*dz
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
