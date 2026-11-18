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

    // ─── drawEsp: Skeleton ESP posture-aware — xuyên tường, xa=xanh, gần=đỏ ──

    private fun drawEsp(canvas: Canvas, snapshot: EspEntityStore.Snapshot) {
        val W = width.toFloat()
        val H = height.toFloat()
        if (W <= 0f || H <= 0f) return

        val now = System.currentTimeMillis()
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

            // ── Màu theo team + khoảng cách ───────────────────────
            val isTeammate = localTeam >= 0 && entity.team == localTeam && entity.team != 0
            val isKnocked  = entity.knocked
            val alpha = boxAlpha

            val skelColor = when {
                isKnocked  -> Color.argb(alpha, 255, 165,   0)
                isTeammate -> Color.argb(alpha,  80, 200, 255)
                dist < 50f -> Color.argb(alpha, 255,  50,  50)
                dist < 100f-> Color.argb(alpha, 255, 200,  50)
                else       -> Color.argb(alpha,  50, 100, 255)
            }

            // ── Posture — đọc từ action_flag thật ─────────────────
            // poseHeightMult: 1.0=đứng, 0.65=ngồi, 0.3=nằm
            val ph  = entity.poseHeightMult
            // Shoulder/hand width thu hẹp khi prone
            val shW = if (entity.isProne) 0.15f else 0.3f
            val hdW = if (entity.isProne) 0.3f  else 0.5f

            // Helper project
            fun proj(wx: Float, wy: Float, wz: Float): FloatArray? =
                PacketProjection.project(W, H, snapshot, wx, wy, wz)?.let {
                    floatArrayOf(it[0], it[1])
                }

            // ── Project skeleton 11 điểm theo posture ─────────────
            val sHEAD    = proj(ex,       ey + 1.75f * ph, ez)
            val sNECK    = proj(ex,       ey + 1.55f * ph, ez)
            val sCHEST   = proj(ex,       ey + 1.35f * ph, ez)
            val sBELLY   = proj(ex,       ey + 1.00f * ph, ez)
            val sPELVIS  = proj(ex,       ey + 0.75f * ph, ez)
            val sLSHOULD = proj(ex - shW, ey + 1.40f * ph, ez)
            val sRSHOULD = proj(ex + shW, ey + 1.40f * ph, ez)
            val sLHAND   = proj(ex - hdW, ey + 0.90f * ph, ez)
            val sRHAND   = proj(ex + hdW, ey + 0.90f * ph, ez)
            val sLFOOT   = proj(ex - 0.2f, ey + 0.0f,     ez)
            val sRFOOT   = proj(ex + 0.2f, ey + 0.0f,     ez)

            if (sHEAD == null) {
                drawEdge(canvas, snapshot, entity, dist, W, H)
                continue
            }
            val headScrX = sHEAD[0]
            val headScrY = sHEAD[1]
            seen += entity.id

            val footScrX = sLFOOT?.get(0) ?: (sRFOOT?.get(0) ?: headScrX)
            val footScrY = sLFOOT?.get(1) ?: (sRFOOT?.get(1) ?: headScrY + 80f)
            smoothFrames[entity.id] = floatArrayOf(footScrX, footScrY, headScrX, headScrY)

            // ── Setup Paint skeleton ───────────────────────────────
            paint.style       = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color       = skelColor
            paint.strokeCap   = Paint.Cap.ROUND
            paint.strokeJoin  = Paint.Join.ROUND

            fun bone(a: FloatArray?, b: FloatArray?) {
                if (a == null || b == null) return
                canvas.drawLine(a[0], a[1], b[0], b[1], paint)
            }

            // ══ SKELETON ═══════════════════════════════════════════
            bone(sHEAD,    sNECK)
            bone(sNECK,    sCHEST)
            bone(sCHEST,   sBELLY)
            bone(sBELLY,   sPELVIS)
            bone(sCHEST,   sLSHOULD); bone(sLSHOULD, sLHAND)
            bone(sCHEST,   sRSHOULD); bone(sRSHOULD, sRHAND)
            bone(sPELVIS,  sLFOOT)
            bone(sPELVIS,  sRFOOT)

            // Vòng đầu
            if (sNECK != null) {
                val headR = kotlin.math.hypot(headScrX - sNECK[0], headScrY - sNECK[1]) * 0.5f
                if (headR in 1f..60f) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(1.5f)
                    canvas.drawCircle(headScrX, headScrY, headR.coerceIn(3f, 24f), paint)
                }
            }

            // ══ HP BAR (bên trái, dọc) ═════════════════════════════
            val footRef = sLFOOT ?: sRFOOT
            if (footRef != null) {
                val barL  = minOf(headScrX, footRef[0]) - dp(8f)
                val barR  = barL + dp(3f)
                val barT  = headScrY
                val barB  = footRef[1]
                if (barB > barT + 2f) {
                    val hp = entity.hpRatio
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(160, 18, 18, 24)
                    canvas.drawRoundRect(RectF(barL, barT, barR, barB), dp(2f), dp(2f), paint)
                    paint.color = when {
                        hp > 0.6f -> Color.rgb(60, 220, 120)
                        hp > 0.3f -> Color.rgb(255, 200, 40)
                        else      -> Color.rgb(255, 70, 70)
                    }
                    val fillT = barB - hp * (barB - barT)
                    canvas.drawRoundRect(RectF(barL, fillT, barR, barB), dp(2f), dp(2f), paint)
                }
            }

            // ══ NAMEPLATE NGANG (phía trên đầu) ════════════════════
            drawNameplate(canvas, entity, headScrX, headScrY - dp(4f), dist, skelColor, isTeammate)
        }

        poseFrames.keys.retainAll(seen)
        smoothFrames.keys.retainAll(seen)
    }

    // ─── Nameplate: thanh ngang [WEAPON] NAME STATE_BADGE ────────

    private fun drawNameplate(
        canvas: Canvas,
        entity: EspEntityStore.Entity,
        cx: Float,
        headTop: Float,
        dist: Float,
        skelColor: Int,
        isTeammate: Boolean,
    ) {
        val displayName = entity.name.ifBlank { "?${entity.id}" }.let {
            if (it.length > 14) it.take(12) + "…" else it
        }

        // Chọn background màu theo team (giống ảnh tham khảo)
        // Teammate = xanh đậm, địch = đỏ đậm
        val bgColor = if (isTeammate)
            Color.argb(210,  20,  60, 160)   // Xanh đậm
        else
            Color.argb(210, 160,  20,  20)   // Đỏ đậm

        val accentColor = if (isTeammate)
            Color.argb(255,  60, 160, 255)   // Accent xanh
        else
            Color.argb(255, 255,  60,  60)   // Accent đỏ

        // ── State badge text + màu ─────────────────────────────
        val stateTxt = entity.stateLabel
        val stateColor = when {
            entity.knocked    -> Color.rgb(255, 165, 0)    // Cam = knocked
            entity.isFiring   -> Color.rgb(255,  60, 60)   // Đỏ = bắn
            entity.isAiming   -> Color.rgb(255, 215, 0)    // Vàng = ngắm
            entity.isProne    -> Color.rgb(0,   210, 210)  // Cyan = nằm
            entity.isCrouching-> Color.rgb(0,   210, 210)  // Cyan = ngồi
            entity.isSprinting-> Color.rgb(160, 160, 160)  // Xám = chạy
            else              -> 0
        }

        // ── Measure text ───────────────────────────────────────
        text.typeface  = android.graphics.Typeface.DEFAULT_BOLD
        text.textSize  = dp(9f)
        text.textAlign = Paint.Align.LEFT

        val nameW   = text.measureText(displayName)
        val wLabel  = entity.weaponLabel
        val wW      = if (wLabel.isNotEmpty()) text.measureText(wLabel) + dp(4f) else 0f
        val stateW  = if (stateTxt.isNotEmpty()) text.measureText(stateTxt) + dp(10f) else 0f

        val padH    = dp(3f)
        val padV    = dp(2.5f)
        val barH    = dp(14f)   // Chiều cao thanh nameplate
        val barW    = wW + dp(6f) + nameW + (if (stateW > 0) stateW + dp(4f) else 0f) + dp(8f)
        val accentW = dp(4f)    // Viền màu trái

        val barL = cx - barW * 0.5f
        val barR = barL + barW
        val barT = headTop - barH - padV
        val barB = headTop - padV

        // ── Vẽ background thanh ────────────────────────────────
        paint.style = Paint.Style.FILL
        paint.color = bgColor
        canvas.drawRoundRect(RectF(barL, barT, barR, barB), dp(3f), dp(3f), paint)

        // ── Accent strip trái ──────────────────────────────────
        paint.color = accentColor
        canvas.drawRoundRect(RectF(barL, barT, barL + accentW, barB), dp(2f), dp(2f), paint)

        // ── Vẽ nội dung từ trái sang phải ────────────────────
        var curX = barL + accentW + dp(5f)
        val textY = barT + barH * 0.65f   // Baseline nằm 65% chiều cao bar

        // Icon vũ khí (text viết tắt)
        if (wLabel.isNotEmpty()) {
            text.textSize  = dp(7.5f)
            text.color     = Color.argb(220, 220, 220, 220)
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(wLabel, curX, textY, text)
            curX += text.measureText(wLabel) + dp(5f)
        }

        // Tên nhân vật
        text.textSize  = dp(9f)
        text.typeface  = android.graphics.Typeface.DEFAULT_BOLD
        text.color     = Color.WHITE
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(displayName, curX, textY, text)
        curX += text.measureText(displayName) + dp(4f)

        // State badge (nền riêng + text)
        if (stateTxt.isNotEmpty() && stateColor != 0) {
            val badgeW  = text.measureText(stateTxt) + dp(8f)
            val badgeL  = curX
            val badgeR  = badgeL + badgeW
            val badgeT  = barT + dp(2f)
            val badgeB  = barB - dp(2f)

            // Nền badge
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(180,
                Color.red(stateColor),
                Color.green(stateColor),
                Color.blue(stateColor))
            canvas.drawRoundRect(RectF(badgeL, badgeT, badgeR, badgeB), dp(2f), dp(2f), paint)

            // Text badge
            text.textSize  = dp(7f)
            text.typeface  = android.graphics.Typeface.DEFAULT_BOLD
            text.color     = Color.WHITE
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(stateTxt, badgeL + badgeW * 0.5f, textY, text)
        }

        // ── Khoảng cách phía trên thanh ───────────────────────
        text.typeface  = android.graphics.Typeface.DEFAULT
        text.textSize  = dp(7.5f)
        text.textAlign = Paint.Align.CENTER
        text.setShadowLayer(dp(1.5f), 0f, dp(1f), Color.BLACK)
        text.color     = Color.argb(200, 200, 200, 200)
        canvas.drawText("${dist.toInt()}m", cx, barT - dp(2f), text)
        text.clearShadowLayer()
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
