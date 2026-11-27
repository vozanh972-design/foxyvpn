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
    @Volatile var boxEnabled   = false
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

    private val isRenderActive: Boolean
        get() = espEnabled || boxEnabled || radarEnabled

    // ─── Tốc độ render: target 60fps = 16ms, dùng Choreographer ──
    // postInvalidateOnAnimation() sync với vsync của màn hình thay vì invalidate() loop thô
    private val frameCallback: android.view.Choreographer.FrameCallback =
        android.view.Choreographer.FrameCallback { _ ->
            if (isRenderActive) {
                invalidate()
                android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snap = EspEntityStore.snapshot()

        val effectiveSnap = if (snap.localPlayer != null) {
            lastKnownLocalPlayer = snap.localPlayer
            snap
        } else if (lastKnownLocalPlayer != null) {
            snap.copy(localPlayer = lastKnownLocalPlayer)
        } else {
            null
        }

        if (effectiveSnap != null) {
            if (isRenderActive) drawEsp(canvas, effectiveSnap)
        } else {
            smoothFrames.clear()
            poseFrames.clear()
            lastFrame = 0L
        }
    }

    /** Bật/tắt Mắt thần ([ID] Xm + HP + chấm rìa) độc lập */
    fun setEsp(on: Boolean) {
        espEnabled = on
        syncRenderLoop()
    }

    /** Bật/tắt Khung 4 cạnh (Box) độc lập */
    fun setBox(on: Boolean) {
        boxEnabled = on
        syncRenderLoop()
    }

    /** Bật/tắt Radar độc lập */
    fun setRadar(on: Boolean) {
        radarEnabled = on
        syncRenderLoop()
    }

    fun syncRenderLoop() {
        val ch = android.view.Choreographer.getInstance()
        ch.removeFrameCallback(frameCallback)
        if (isRenderActive) {
            ch.postFrameCallback(frameCallback)
        } else {
            invalidate()
        }
    }

    /** Bật ESP — giữ tương thích */
    fun enable() = setEsp(true)

    /** Tắt ESP — giữ tương thích */
    fun disable() = setEsp(false)

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

            // Xa = trắng, gần = đỏ | knocked = cam | teammate = xanh
            val skelColor = when {
                isKnocked  -> Color.argb(alpha, 255, 165,   0)   // cam
                isTeammate -> Color.argb(alpha,  80, 200, 255)   // xanh
                dist < 30f -> Color.argb(alpha, 255,  40,  40)   // đỏ đậm
                dist < 60f -> Color.argb(alpha, 255, 100,  60)   // đỏ cam
                dist < 100f-> Color.argb(alpha, 255, 200, 120)   // vàng nhạt
                dist < 150f-> Color.argb(alpha, 200, 220, 200)   // trắng xanh
                else       -> Color.argb(alpha, 255, 255, 255)   // trắng
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

            // ── Project skeleton 11 điểm: thử bone thực → fallback ước tính ──
            // Đọc bone thực từ memory nếu có boneBaseAddr
            val bone: BoneSkeleton? = if (entity.boneBaseAddr > 0L)
                BoneMemReader.readSkeleton(entity.boneBaseAddr)
            else null

            val sHEAD:    FloatArray?
            val sNECK:    FloatArray?
            val sCHEST:   FloatArray?
            val sBELLY:   FloatArray?
            val sPELVIS:  FloatArray?
            val sLSHOULD: FloatArray?
            val sRSHOULD: FloatArray?
            val sLHAND:   FloatArray?
            val sRHAND:   FloatArray?
            val sLFOOT:   FloatArray?
            val sRFOOT:   FloatArray?

            if (bone != null) {
                // ── Dùng bone positions thực từ memory (world XYZ) ──
                fun bProj(b: FloatArray?) = b?.let { proj(it[0], it[1], it[2]) }
                sHEAD    = bProj(bone.head)
                sNECK    = bProj(bone.neck)
                sCHEST   = bProj(bone.spine1)      // bone_Spine1 = ngực
                sBELLY   = bProj(bone.spine)        // bone_Spine  = bụng
                sPELVIS  = bProj(bone.hips)         // bone_Hips   = hông/pelvis
                sLSHOULD = bProj(bone.leftClav)     // bone_LeftClav  = vai trái
                sRSHOULD = bProj(bone.rightClav)    // bone_RightClav = vai phải
                sLHAND   = bProj(bone.leftHand)     // bone_LeftHand
                sRHAND   = bProj(bone.rightHand)    // bone_RightHand
                sLFOOT   = bProj(bone.leftAnkle)    // bone_LeftAnkle
                sRFOOT   = bProj(bone.rightAnkle)   // bone_RightAnkle
            } else {
                // ── Fallback: ước tính tương đối (logic gốc) ──
                sHEAD    = proj(ex,       ey + 1.75f * ph, ez)
                sNECK    = proj(ex,       ey + 1.55f * ph, ez)
                sCHEST   = proj(ex,       ey + 1.35f * ph, ez)
                sBELLY   = proj(ex,       ey + 1.00f * ph, ez)
                sPELVIS  = proj(ex,       ey + 0.75f * ph, ez)
                sLSHOULD = proj(ex - shW, ey + 1.40f * ph, ez)
                sRSHOULD = proj(ex + shW, ey + 1.40f * ph, ez)
                sLHAND   = proj(ex - hdW, ey + 0.90f * ph, ez)
                sRHAND   = proj(ex + hdW, ey + 0.90f * ph, ez)
                sLFOOT   = proj(ex - 0.2f, ey + 0.0f,     ez)
                sRFOOT   = proj(ex + 0.2f, ey + 0.0f,     ez)
            }

            if (sHEAD == null) {
                continue
            }
            val headScrX = sHEAD[0]
            val headScrY = sHEAD[1]
            seen += entity.id

            val footScrX = sLFOOT?.get(0) ?: (sRFOOT?.get(0) ?: headScrX)
            val footScrY = sLFOOT?.get(1) ?: (sRFOOT?.get(1) ?: headScrY + 80f)
            smoothFrames[entity.id] = floatArrayOf(footScrX, footScrY, headScrX, headScrY)

            // ══ KHUNG 4 CẠNH (chỉ vẽ khi boxEnabled = true) ══════════
            if (boxEnabled) {
                drawSilhouette(canvas, entity,
                    sHEAD, sNECK, sCHEST, sBELLY, sPELVIS,
                    sLSHOULD, sRSHOULD, sLHAND, sRHAND,
                    sLFOOT, sRFOOT,
                    skelColor, alpha)
            }

            // ══ MẮT THẦN: [ID] Xm + HP bar (chỉ vẽ khi espEnabled = true) ═
            if (espEnabled) {
                val footRef2 = sLFOOT ?: sRFOOT
                drawNameplate(canvas, entity, headScrX, headScrY - dp(22f), dist, skelColor, isTeammate, footRef2)
            }
        }

        poseFrames.keys.retainAll(seen)
        smoothFrames.keys.retainAll(seen)

        // ══ RADAR: chỉ hiện chấm tròn khi radarEnabled = true ════════
        if (radarEnabled) {
            drawRadar(canvas, snapshot, W, H)
        }
    }

    // ─── drawSilhouette → chỉ vẽ khung 4 góc khi boxEnabled = true ──

    private fun drawSilhouette(
        canvas: Canvas, entity: EspEntityStore.Entity,
        sHEAD: FloatArray?, sNECK: FloatArray?, sCHEST: FloatArray?,
        sBELLY: FloatArray?, sPELVIS: FloatArray?,
        sLSHOULD: FloatArray?, sRSHOULD: FloatArray?,
        sLHAND: FloatArray?, sRHAND: FloatArray?,
        sLFOOT: FloatArray?, sRFOOT: FloatArray?,
        skelColor: Int, alpha: Int,
    ) {
        if (!boxEnabled || sHEAD == null) return
        val hx = sHEAD[0]; val hy = sHEAD[1]

        // Foot position (dùng để tính chiều cao box)
        val footL = sLFOOT ?: sRFOOT ?: return
        val fy = footL[1]

        val boxH   = kotlin.math.abs(fy - hy).coerceAtLeast(20f)
        val boxW   = boxH * 0.42f
        val left   = hx - boxW * 0.5f
        val right  = hx + boxW * 0.5f
        val top    = hy - dp(4f)
        val bot    = fy + dp(2f)
        val corner = (boxW * 0.22f).coerceIn(dp(4f), dp(12f))

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.color = Color.argb((alpha * 0.85f).toInt(),
            Color.red(skelColor), Color.green(skelColor), Color.blue(skelColor))

        // Vẽ 4 góc (corner bracket style)
        canvas.drawLine(left,  top,  left + corner,  top,  paint)   // top-left H
        canvas.drawLine(left,  top,  left,  top + corner,  paint)   // top-left V
        canvas.drawLine(right, top,  right - corner, top,  paint)   // top-right H
        canvas.drawLine(right, top,  right, top + corner,  paint)   // top-right V
        canvas.drawLine(left,  bot,  left + corner,  bot,  paint)   // bot-left H
        canvas.drawLine(left,  bot,  left,  bot - corner,  paint)   // bot-left V
        canvas.drawLine(right, bot,  right - corner, bot,  paint)   // bot-right H
        canvas.drawLine(right, bot,  right, bot - corner,  paint)   // bot-right V

        paint.strokeCap = Paint.Cap.BUTT
    }

    // ─── Nameplate gọn nhẹ: [ID] Xm + HP bar mảnh bên dưới ─────────────────

    private fun drawNameplate(
        canvas: Canvas,
        entity: EspEntityStore.Entity,
        cx: Float,
        headTop: Float,
        dist: Float,
        skelColor: Int,
        isTeammate: Boolean,
        footRef: FloatArray?,
    ) {
        val badgeText = entity.id.toString()
        val distTxt = "${dist.toInt()}m"

        val badgeColor = when {
            entity.knocked -> Color.rgb(255, 165, 0)
            isTeammate     -> Color.rgb(50, 150, 255)
            else           -> Color.rgb(220, 55, 55)
        }

        val badgeSize = dp(13f)
        val gap = dp(4f)

        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        text.textSize = dp(10f)
        val distW = text.measureText(distTxt)

        val totalW = badgeSize + gap + distW
        val startX = cx - totalW * 0.5f
        val tagBottom = headTop - dp(5f)
        val tagTop = tagBottom - badgeSize

        // Badge vuông nhỏ chứa số ID
        paint.style = Paint.Style.FILL
        paint.color = badgeColor
        canvas.drawRoundRect(
            RectF(startX, tagTop, startX + badgeSize, tagBottom),
            dp(2.5f), dp(2.5f), paint
        )

        text.textSize = dp(8f)
        text.color = Color.WHITE
        text.textAlign = Paint.Align.CENTER
        text.clearShadowLayer()
        val fmBadge = text.fontMetrics
        val badgeTextY = tagTop + badgeSize * 0.5f - (fmBadge.ascent + fmBadge.descent) * 0.5f
        canvas.drawText(badgeText, startX + badgeSize * 0.5f, badgeTextY, text)

        // Khoảng cách Xm kế bên badge (có bóng đen nhẹ cho dễ đọc)
        text.textSize = dp(10f)
        text.color = Color.WHITE
        text.textAlign = Paint.Align.LEFT
        text.setShadowLayer(dp(1.5f), 0f, dp(1f), Color.BLACK)
        val fmDist = text.fontMetrics
        val distTextY = tagTop + badgeSize * 0.5f - (fmDist.ascent + fmDist.descent) * 0.5f
        canvas.drawText(distTxt, startX + badgeSize + gap, distTextY, text)
        text.clearShadowLayer()

        // Thanh HP mảnh gọn đúng bằng bề rộng tag (tối thiểu 26dp)
        val hpW = totalW.coerceAtLeast(dp(26f))
        val hpL = cx - hpW * 0.5f
        val hpR = hpL + hpW
        val hpT = tagBottom + dp(2f)
        val hpB = hpT + dp(2.2f)
        val hpRatio = entity.hpRatio

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 15, 15, 15)
        canvas.drawRoundRect(RectF(hpL, hpT, hpR, hpB), dp(1f), dp(1f), paint)

        paint.color = when {
            hpRatio > 0.6f -> Color.rgb(60, 220, 100)
            hpRatio > 0.3f -> Color.rgb(255, 190, 40)
            else           -> Color.rgb(255, 60, 60)
        }
        canvas.drawRoundRect(RectF(hpL, hpT, hpL + hpW * hpRatio, hpB), dp(1f), dp(1f), paint)
    }

    // ─── drawRadar: gom các chấm tròn vào Radar (chỉ hiện khi radarEnabled) ───

    private fun drawRadar(canvas: Canvas, snapshot: EspEntityStore.Snapshot, W: Float, H: Float) {
        val lp = snapshot.localPlayer ?: return
        val localTeam = lp.team

        val radius = dp(46f)
        val cx = dp(62f)
        val cy = dp(186f).coerceAtMost(H - radius - dp(16f))
        val maxRange = 160f  // mét tối đa hiển thị trong vòng radar

        // Nền tròn tối bán trong suốt
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(145, 14, 20, 24)
        canvas.drawCircle(cx, cy, radius, paint)

        // Viền ngoài + vòng trong
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = Color.argb(130, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.strokeWidth = dp(0.7f)
        paint.color = Color.argb(45, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius * 0.5f, paint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)

        // Chấm bản thân ở tâm
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(2.6f), paint)

        // Chấm tròn các entity trên radar (chỉ hiện chấm tròn, không chữ)
        val maxDotOffset = radius - dp(3.5f)
        for (entity in snapshot.entities) {
            val relX = entity.x - lp.x
            val relZ = entity.z - lp.z
            val rot = PacketProjection.radar(relX, relZ, snapshot.yawRadians)
            // rot[0] = right, rot[1] = forward
            var dx = (rot[0] / maxRange) * radius
            var dy = -(rot[1] / maxRange) * radius
            val len = sqrt(dx * dx + dy * dy)
            if (len > maxDotOffset && len > 0.001f) {
                val scale = maxDotOffset / len
                dx *= scale
                dy *= scale
            }

            val isTeammate = localTeam >= 0 && entity.team == localTeam && entity.team != 0
            paint.style = Paint.Style.FILL
            paint.color = when {
                entity.knocked -> Color.rgb(255, 165, 0)
                isTeammate     -> Color.rgb(80, 180, 255)
                else           -> Color.rgb(255, 55, 55)
            }
            canvas.drawCircle(cx + dx, cy + dy, dp(3f), paint)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private fun dp(v: Float) = resources.displayMetrics.density * v
    private fun clamp(v: Float, min: Float, max: Float) = v.coerceIn(min, max)
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
