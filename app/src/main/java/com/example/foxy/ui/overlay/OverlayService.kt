package com.example.foxy.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.foxy.engine.EspHudView
import com.example.foxy.engine.FreezeController
import com.example.foxy.ui.theme.VeilTheme
import com.example.foxy.vpn.FreefireVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * OverlayService — Hiển thị GameOverlay + EspHudView nổi trên game.
 *
 * Mắt thần toggle → bật/tắt EspHudView
 * Ghim (Boost tab) → FreezeController.setManualFreeze()
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry    = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store                = ViewModelStore()
    override val lifecycle:          Lifecycle          get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore:     ViewModelStore     get() = store

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var espHudView:  EspHudView?  = null    // ← NEW: ESP overlay

    private var expanded    by mutableStateOf(false)
    private var panelScale  by mutableFloatStateOf(PANEL_SCALE)
    private var widthScale  by mutableFloatStateOf(1f)
    private var heightScale by mutableFloatStateOf(1f)
    private var tab         by mutableStateOf(OverlayTab.Home)
    private var boost       by mutableStateOf(BoostState())

    private var chipX = 0; private var chipY = 0
    private var panelX = -1; private var panelY = -1

    private val params by lazy {
        val d = resources.displayMetrics.density
        chipX = (14 * d).toInt(); chipY = (10 * d).toInt()
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = chipX; y = chipY }
    }

    // ─── Lifecycle ────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 1. Add full-screen ESP HUD (phía dưới overlay)
        addEspHud()

        // 2. Add game overlay menu
        showOverlay()

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    // ─── ESP HUD View ─────────────────────────────────────────

    private fun addEspHud() {
        val hud = EspHudView(this)
        // Lấy state hiện tại
        hud.espEnabled    = stateFlow.value.eagleEyeEnabled
        hud.smoothEnabled = true

        val espParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or   // Không chặn touch
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(hud, espParams)
        espHudView = hud
    }

    // ─── Overlay menu (Compose) ───────────────────────────────

    private fun showOverlay() {
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by stateFlow.collectAsState()
                VeilTheme {
                    GameOverlay(
                        state           = state,
                        expanded        = expanded,
                        onExpandedChange= ::updateExpanded,
                        onDisconnect    = {
                            startService(Intent(this@OverlayService, FreefireVpnService::class.java).apply {
                                action = FreefireVpnService.ACTION_STOP
                            })
                            stopSelf()
                        },
                        onDismissOverlay= { stopSelf() },
                        onPriorityChange= { checked -> stateFlow.update { it.copy(gamePriority = checked) } },
                        onServerClick   = {},
                        onLanguageClick = {},
                        tab             = tab,
                        onTabSelected   = { tab = it },
                        boost           = boost,
                        onBoostChange   = { newBoost ->
                            boost = newBoost
                            // ═══ GHIM (Chế độ ghim) → FreezeController ═══
                            FreezeController.available    = newBoost.pinEnabled
                            FreezeController.applyManualFreeze(newBoost.pinEnabled)
                        },
                        onEagleEyeChange= { checked ->
                            // ═══ MẮT THẦN → bật/tắt EspHudView ═══
                            stateFlow.update { it.copy(eagleEyeEnabled = checked) }
                            if (checked) espHudView?.enable() else espHudView?.disable()
                        },
                        widthScale      = widthScale,
                        heightScale     = heightScale,
                        onSizeChange    = ::updatePanelSize,
                        panelScale      = panelScale,
                    )
                }
            }
        }

        val container = DraggableOverlayContainer(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            addView(compose)
        }
        overlayView = container
        windowManager.addView(container, params)
    }

    // ─── Window management ────────────────────────────────────

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::windowManager.isInitialized || overlayView == null) return
        applyWindowBounds(centerIfUnset = false)
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun updateExpanded(value: Boolean) {
        expanded = value
        applyWindowBounds(centerIfUnset = true)
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun updatePanelSize(newW: Float, newH: Float) {
        widthScale = newW; heightScale = newH
        if (expanded) { applyWindowBounds(false); overlayView?.let { windowManager.updateViewLayout(it, params) } }
    }

    private fun applyWindowBounds(centerIfUnset: Boolean) {
        val (screenW, screenH) = screenSizePx()
        if (expanded) {
            val d = resources.displayMetrics.density
            val designW = 700f * widthScale; val designH = 350f * heightScale
            val scale   = minOf(PANEL_SCALE, (screenW * 0.96f) / (designW * d), (screenH * 0.92f) / (designH * d)).coerceAtLeast(0.42f)
            val wPx = ceil(designW * d * scale).toInt(); val hPx = ceil(designH * d * scale).toInt()
            panelScale = scale; params.width = wPx; params.height = hPx
            if (panelX < 0 && centerIfUnset) { panelX = ((screenW - wPx) / 2).coerceAtLeast(0); panelY = ((screenH - hPx) / 2).coerceAtLeast(0) }
            panelX = panelX.coerceIn(0, (screenW - wPx).coerceAtLeast(0)); panelY = panelY.coerceIn(0, (screenH - hPx).coerceAtLeast(0))
            params.x = panelX; params.y = panelY
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT; params.height = WindowManager.LayoutParams.WRAP_CONTENT
            chipX = chipX.coerceIn(0, (screenW - currentViewWidthPx()).coerceAtLeast(0)); chipY = chipY.coerceIn(0, (screenH - currentViewHeightPx()).coerceAtLeast(0))
            params.x = chipX; params.y = chipY
        }
    }

    private fun moveOverlayTo(rx: Int, ry: Int) {
        val (sw, sh) = screenSizePx()
        val cx = rx.coerceIn(0, (sw - currentViewWidthPx()).coerceAtLeast(0))
        val cy = ry.coerceIn(0, (sh - currentViewHeightPx()).coerceAtLeast(0))
        params.x = cx; params.y = cy
        if (expanded) { panelX = cx; panelY = cy } else { chipX = cx; chipY = cy }
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun currentViewWidthPx()  = if (params.width  > 0) params.width  else overlayView?.width?.takeIf  { it > 0 } ?: (96 * resources.displayMetrics.density).toInt()
    private fun currentViewHeightPx() = if (params.height > 0) params.height else overlayView?.height?.takeIf { it > 0 } ?: (36 * resources.displayMetrics.density).toInt()
    private fun screenSizePx(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val b = windowManager.currentWindowMetrics.bounds; b.width() to b.height() }
        else { val dm = resources.displayMetrics; dm.widthPixels to dm.heightPixels }

    private inner class DraggableOverlayContainer(context: Context) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private var downRawX = 0f; private var downRawY = 0f
        private var startX = 0;    private var startY = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN  -> { downRawX = ev.rawX; downRawY = ev.rawY; startX = params.x; startY = params.y; dragging = false }
                MotionEvent.ACTION_MOVE  -> { if (!dragging && hypot((ev.rawX - downRawX).toDouble(), (ev.rawY - downRawY).toDouble()) > touchSlop) { dragging = true; return true } }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return false
        }
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (dragging) moveOverlayTo(startX + (ev.rawX - downRawX).toInt(), startY + (ev.rawY - downRawY).toInt())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }

    private fun startAsForeground() {
        val channelId = "veil_overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId, "Bảng thông tin trong game", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Foxy VPN đang bảo vệ kết nối")
            .setContentText("Mắt thần đang hoạt động")
            .setOngoing(true).build()
        ServiceCompat.startForeground(this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::windowManager.isInitialized) {
            espHudView?.let   { runCatching { windowManager.removeView(it) } }
            overlayView?.let  { runCatching { windowManager.removeView(it) } }
        }
        espHudView = null; overlayView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2002
        val stateFlow = MutableStateFlow(GameOverlayState())

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }
        fun stop(context: Context) { context.stopService(Intent(context, OverlayService::class.java)) }
    }
}
