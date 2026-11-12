package com.example.foxy.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.foxy.ui.theme.VeilTheme
import com.example.foxy.vpn.FreefireVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Service hiển thị GameOverlay nổi trên game (cần quyền "Hiển thị trên ứng dụng khác").
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var expanded by mutableStateOf(false)

    private val params by lazy {
        val d = resources.displayMetrics.density
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (14 * d).toInt(); y = (10 * d).toInt() }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun showOverlay() {
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                val state by stateFlow.collectAsState()
                VeilTheme {
                    GameOverlay(
                        state = state,
                        expanded = expanded,
                        onExpandedChange = ::updateExpanded,
                        onDisconnect = {
                            startService(
                                Intent(this@OverlayService, FreefireVpnService::class.java).apply {
                                    action = FreefireVpnService.ACTION_STOP
                                }
                            )
                            stopSelf()
                        },
                        onPriorityChange = { checked -> stateFlow.update { it.copy(gamePriority = checked) } },
                        onServerClick = { /* TODO: mở màn chọn máy chủ */ },
                        onLanguageClick = { /* TODO: đổi ngôn ngữ */ },
                    )
                }
            }
        }
        overlayView = view
        windowManager.addView(view, params)
    }

    /** Mở rộng thì đưa bảng ra giữa màn hình, thu gọn thì đưa chip về góc trên trái. */
    private fun updateExpanded(value: Boolean) {
        expanded = value
        val d = resources.displayMetrics.density
        params.gravity = if (value) Gravity.CENTER else Gravity.TOP or Gravity.START
        params.x = if (value) 0 else (14 * d).toInt()
        params.y = if (value) 0 else (10 * d).toInt()
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun startAsForeground() {
        val channelId = "veil_overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Bảng thông tin trong game", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Foxy VPN đang bảo vệ kết nối")
            .setContentText("Bảng thông tin trong game đang bật")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::windowManager.isInitialized) {
            overlayView?.let { windowManager.removeView(it) }
        }
        overlayView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2002
        val stateFlow = MutableStateFlow(GameOverlayState())

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
