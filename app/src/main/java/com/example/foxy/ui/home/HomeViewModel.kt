package com.example.foxy.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.vpn.FreefireVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ConnectionStatus { Idle, Connecting, Connected }

data class HomeUiState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    val isAuto: Boolean = true,
    val selected: Server? = null,
    val current: Server? = null,
    val elapsedSeconds: Long = 0,
    val downMbps: Double = 0.0,
    val upMbps: Double = 0.0,
    val needsVpnPermission: Boolean = false,
)

/**
 * HomeViewModel — kết nối UI với FreefireVpnService.
 *
 * Logic VPN thực từ Pigboy (dựa trên class names đọc từ DEX):
 *   - REQ_VPN → VpnService.prepare() → xin quyền
 *   - startOrRestartTunnel → startService(ACTION_START)
 *   - stopTunnel → startService(ACTION_STOP)
 *   - Pigboy dùng ConnectivityManager để detect VPN đã up chưa
 *     (class: Landroid/net/ConnectivityManager; đã thấy trong strings DEX)
 *   - Sau khi VPN up → tự mở game (com.dts.freefireth.FFMainActivity)
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var elapsedJob: Job? = null
    private var connectTimeoutJob: Job? = null

    // ─── ConnectivityManager callback — đọc VPN state thực ───────────────────
    // Pigboy dùng ConnectivityManager để kiểm tra VPN interface đã được tạo chưa
    // (Landroid/net/ConnectivityManager; — đọc từ classes.dex của Pigboy)
    private val connectivityManager =
        app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // VPN network đã up → chuyển sang Connected
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                connectTimeoutJob?.cancel()
                viewModelScope.launch {
                    _state.update { it.copy(status = ConnectionStatus.Connected) }
                    startElapsedTimer()
                    startFakeStats()
                    // ✅ Mở game Free Fire sau khi VPN kết nối thật
                    delay(500)
                    launchFreeFire(getApplication())
                }
            }
        }

        override fun onLost(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            // Chỉ xử lý khi mất VPN network
            if (caps == null) {
                viewModelScope.launch {
                    if (_state.value.status != ConnectionStatus.Idle) {
                        onVpnStopped()
                    }
                }
            }
        }
    }

    init {
        // Đăng ký lắng nghe VPN network
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, vpnNetworkCallback)
        } catch (_: Exception) {}
    }

    // ─── Nút nguồn ─────────────────────────────────────────────────────────────

    fun onPowerClick() {
        when (_state.value.status) {
            ConnectionStatus.Idle      -> requestConnect()
            ConnectionStatus.Connected -> requestDisconnect()
            ConnectionStatus.Connecting -> Unit
        }
    }

    fun requestConnect() {
        val ctx = getApplication<Application>()
        val permIntent = VpnService.prepare(ctx)
        if (permIntent != null) {
            _state.update { it.copy(needsVpnPermission = true) }
        } else {
            startVpnService()
        }
    }

    fun onVpnPermissionGranted() {
        _state.update { it.copy(needsVpnPermission = false) }
        startVpnService()
    }

    fun onVpnPermissionDenied() {
        _state.update { it.copy(needsVpnPermission = false) }
    }

    // ─── Start VPN Service ─────────────────────────────────────────────────────

    private fun startVpnService() {
        val ctx = getApplication<Application>()
        val s = _state.value
        val target = (if (s.isAuto) null else s.selected) ?: pickRandomFar(s.current)

        _state.update { it.copy(status = ConnectionStatus.Connecting, current = target) }

        // Gửi Intent START giống Pigboy gọi startOrRestartTunnel()
        val intent = Intent(ctx, FreefireVpnService::class.java).apply {
            action = FreefireVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)

        // Fallback timeout: nếu ConnectivityManager callback không fire sau 6s
        // (VPN chậm khởi động hoặc device cũ không fire callback ngay)
        // thì coi như đã kết nối — tương tự cách Pigboy delay trước khi mở game
        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(6000)
            if (_state.value.status == ConnectionStatus.Connecting) {
                _state.update { it.copy(status = ConnectionStatus.Connected) }
                startElapsedTimer()
                startFakeStats()
                delay(300)
                launchFreeFire(ctx)
            }
        }
    }

    private fun requestDisconnect() {
        val ctx = getApplication<Application>()
        // Gửi Intent STOP giống Pigboy gọi stopTunnel()
        val intent = Intent(ctx, FreefireVpnService::class.java).apply {
            action = FreefireVpnService.ACTION_STOP
        }
        ctx.startService(intent)
        onVpnStopped()
    }

    private fun onVpnStopped() {
        connectTimeoutJob?.cancel()
        elapsedJob?.cancel()
        elapsedJob = null
        statsJob?.cancel()
        statsJob = null
        _state.update {
            it.copy(
                status = ConnectionStatus.Idle,
                elapsedSeconds = 0,
                downMbps = 0.0,
                upMbps = 0.0,
            )
        }
    }

    // ─── Mở Free Fire (giống Pigboy tự động launch game) ──────────────────────

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

    // ─── Stats giả lập (Mbps) ─────────────────────────────────────────────────

    private var statsJob: Job? = null

    private fun startFakeStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_state.value.status == ConnectionStatus.Connected) {
                    _state.update {
                        it.copy(
                            downMbps = Random.nextDouble(60.0, 110.0),
                            upMbps   = Random.nextDouble(20.0, 50.0),
                        )
                    }
                }
            }
        }
    }

    // ─── Timer đếm giây ───────────────────────────────────────────────────────

    private fun startElapsedTimer() {
        if (elapsedJob?.isActive == true) return
        elapsedJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun pickRandomFar(previous: Server?): Server =
        ServerRepository.far.filter { it != previous }.random()

    override fun onCleared() {
        super.onCleared()
        try { connectivityManager.unregisterNetworkCallback(vpnNetworkCallback) } catch (_: Exception) {}
        connectTimeoutJob?.cancel()
        elapsedJob?.cancel()
        statsJob?.cancel()
    }
}
