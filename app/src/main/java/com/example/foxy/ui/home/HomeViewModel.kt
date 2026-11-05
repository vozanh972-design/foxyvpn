package com.example.foxy.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.vpn.FreefireVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionStatus { Idle, Connecting, Connected }

data class HomeUiState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    /** true = tự động chọn máy chủ ngẫu nhiên — chip "Auto" ở màn hình chính. */
    val isAuto: Boolean = true,
    /** Máy chủ người dùng tự chọn (chỉ dùng khi isAuto = false). */
    val selected: Server? = null,
    /** Máy chủ đang kết nối hoặc đã kết nối. */
    val current: Server? = null,
    val elapsedSeconds: Long = 0,
    val downMbps: Double = 0.0,
    val upMbps: Double = 0.0,
    /** true = cần hỏi quyền VPN từ người dùng (startActivityForResult). */
    val needsVpnPermission: Boolean = false,
)

/**
 * HomeViewModel — kết nối UI với FreefireVpnService thật.
 *
 * Logic giống Pigboy:
 *   1. Bấm nút nguồn → kiểm tra quyền VPN
 *   2. Nếu có quyền → gửi Intent START cho FreefireVpnService
 *   3. VpnService bật TUN interface (PacketTunnelEngine)
 *   4. Nhận broadcast stats (down/up Mbps) → cập nhật UI
 *   5. Sau khi kết nối → tự động mở game Free Fire
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Đếm giây đã kết nối
    private var elapsedJob: kotlinx.coroutines.Job? = null

    // BroadcastReceiver nhận stats từ FreefireVpnService (giống cách Pigboy gửi stats)
    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != FreefireVpnService.BROADCAST_STATE) return
            val connected = intent.getBooleanExtra(FreefireVpnService.EXTRA_CONNECTED, false)
            val downMbps  = intent.getDoubleExtra(FreefireVpnService.EXTRA_DOWN_MBPS, 0.0)
            val upMbps    = intent.getDoubleExtra(FreefireVpnService.EXTRA_UP_MBPS, 0.0)

            if (connected) {
                _state.update {
                    it.copy(
                        status   = ConnectionStatus.Connected,
                        downMbps = downMbps,
                        upMbps   = upMbps,
                    )
                }
                startElapsedTimer()
            } else {
                onVpnStopped()
            }
        }
    }

    init {
        // Đăng ký lắng nghe broadcast từ VpnService
        val filter = IntentFilter(FreefireVpnService.BROADCAST_STATE)
        ContextCompat.registerReceiver(
            app,
            vpnReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ─── Nút nguồn ─────────────────────────────────────────────────────────────

    fun onPowerClick() {
        when (_state.value.status) {
            ConnectionStatus.Idle       -> requestConnect()
            ConnectionStatus.Connected  -> requestDisconnect()
            ConnectionStatus.Connecting -> Unit   // đang kết nối, bỏ qua
        }
    }

    /**
     * Kiểm tra quyền VPN trước khi kết nối.
     * Nếu chưa có quyền → báo UI cần gọi startActivityForResult().
     * Nếu đã có → start VpnService ngay.
     */
    fun requestConnect() {
        val ctx = getApplication<Application>()
        val permIntent = VpnService.prepare(ctx)
        if (permIntent != null) {
            // Cần hỏi user cấp quyền — UI sẽ bắt flag này
            _state.update { it.copy(needsVpnPermission = true) }
        } else {
            startVpnService()
        }
    }

    /** Gọi sau khi user đồng ý cấp quyền VPN (onActivityResult OK). */
    fun onVpnPermissionGranted() {
        _state.update { it.copy(needsVpnPermission = false) }
        startVpnService()
    }

    fun onVpnPermissionDenied() {
        _state.update { it.copy(needsVpnPermission = false) }
    }

    // ─── Start/Stop VpnService ─────────────────────────────────────────────────

    private fun startVpnService() {
        val ctx = getApplication<Application>()
        val s = _state.value
        val target = (if (s.isAuto) null else s.selected) ?: pickRandomFar(s.current)

        _state.update {
            it.copy(
                status  = ConnectionStatus.Connecting,
                current = target,
            )
        }

        // Gửi Intent START giống Pigboy gọi startOrRestartTunnel()
        val intent = Intent(ctx, FreefireVpnService::class.java).apply {
            action = FreefireVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)

        // Mở Free Fire sau khi bắt đầu khởi động VPN (giống Pigboy tự động mở game)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2800)   // đợi kết nối ổn định
            launchFreeFire(ctx)
        }
    }

    private fun requestDisconnect() {
        val ctx = getApplication<Application>()
        // Gửi Intent STOP giống Pigboy gọi stopTunnel()
        val intent = Intent(ctx, FreefireVpnService::class.java).apply {
            action = FreefireVpnService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    private fun onVpnStopped() {
        elapsedJob?.cancel()
        elapsedJob = null
        _state.update {
            it.copy(
                status        = ConnectionStatus.Idle,
                elapsedSeconds = 0,
                downMbps      = 0.0,
                upMbps        = 0.0,
            )
        }
    }

    // ─── Mở Free Fire ─────────────────────────────────────────────────────────

    /**
     * Mở game Free Fire — giống Pigboy gọi intent đến com.dts.freefireth.FFMainActivity.
     * Thử ComponentName trước, fallback về getLaunchIntentForPackage.
     */
    private fun launchFreeFire(ctx: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.dts.freefireth",
                    "com.dts.freefireth.FFMainActivity",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = ctx.packageManager
                    .getLaunchIntentForPackage("com.dts.freefireth")
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(fallback)
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Chọn server ──────────────────────────────────────────────────────────

    fun selectServer(server: Server?) {
        val wasActive = _state.value.status != ConnectionStatus.Idle
        _state.update { it.copy(isAuto = server == null, selected = server) }
        if (wasActive) {
            requestDisconnect()
            startVpnService()
        }
    }

    // ─── Timer đếm giây ───────────────────────────────────────────────────────

    private fun startElapsedTimer() {
        if (elapsedJob?.isActive == true) return
        elapsedJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun pickRandomFar(previous: Server?): Server =
        ServerRepository.far.filter { it != previous }.random()

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(vpnReceiver)
        } catch (_: Exception) {}
        elapsedJob?.cancel()
    }
}
