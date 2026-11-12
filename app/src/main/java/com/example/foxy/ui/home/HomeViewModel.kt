package com.example.foxy.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.ui.overlay.OverlayService
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

enum class GameOption(
    val displayName: String,
    val packageName: String,
    val activityName: String,
    val code: String,
) {
    FreeFire("Free Fire", "com.dts.freefireth", "com.dts.freefireth.FFMainActivity", "FF"),
    FreeFireMax("Free Fire Max", "com.dts.freefiremax", "com.dts.freefiremax.FFMainActivity", "FFMAX"),
}

data class HomeUiState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    val isAuto: Boolean = true,
    val selectedGame: GameOption = GameOption.FreeFire,
    val selected: Server? = null,
    val current: Server? = null,
    val elapsedSeconds: Long = 0,
    val downMbps: Double = 0.0,
    val upMbps: Double = 0.0,
    val needsVpnPermission: Boolean = false,
    val needsOverlayPermission: Boolean = false,
    val serverName: String = GameOption.FreeFire.displayName,
    val serverHint: String = "Tự động",
    val serverCountryCode: String = GameOption.FreeFire.code,
    val ipAddress: String = "254.759.36.54",
)

/**
 * HomeViewModel — kết nối UI với FreefireVpnService và OverlayService.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var elapsedJob: Job? = null
    private var connectTimeoutJob: Job? = null

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != FreefireVpnService.BROADCAST_STATE) return
            val connected = intent.getBooleanExtra(FreefireVpnService.EXTRA_CONNECTED, false)
            connectTimeoutJob?.cancel()
            viewModelScope.launch {
                if (connected && _state.value.status == ConnectionStatus.Connecting) {
                    onVpnConnected()
                } else if (!connected) {
                    onVpnStopped()
                }
            }
        }
    }

    init {
        val filter = android.content.IntentFilter(FreefireVpnService.BROADCAST_STATE)
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
            ConnectionStatus.Idle -> requestConnectWithOverlay()
            ConnectionStatus.Connecting, ConnectionStatus.Connected -> requestDisconnect()
        }
    }

    private fun requestConnectWithOverlay() {
        val ctx = getApplication<Application>()
        if (!Settings.canDrawOverlays(ctx)) {
            _state.update { it.copy(needsOverlayPermission = true) }
        } else {
            requestConnect()
        }
    }

    fun onOverlayPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsOverlayPermission = false) }
        if (granted) requestConnect()
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

    // ─── Start / Stop VPN + Overlay ────────────────────────────────────────────

    private fun startVpnService() {
        val ctx = getApplication<Application>()
        val s = _state.value
        val target = (if (s.isAuto) null else s.selected) ?: pickRandomFar(s.current)

        _state.update {
            it.withServerDisplay(if (s.isAuto) null else target).copy(
                status = ConnectionStatus.Connecting,
                current = target,
                elapsedSeconds = 0,
            )
        }

        val intent = Intent(ctx, FreefireVpnService::class.java).apply {
            action = FreefireVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)

        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(6000)
            if (_state.value.status == ConnectionStatus.Connecting) {
                onVpnConnected()
            }
        }
    }

    private suspend fun onVpnConnected() {
        val ctx = getApplication<Application>()
        _state.update { it.copy(status = ConnectionStatus.Connected) }
        syncOverlayState()
        OverlayService.start(ctx)
        startElapsedTimer()
        startFakeStats()
        delay(300)
        launchFreeFire(ctx)
    }

    private fun requestDisconnect() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, FreefireVpnService::class.java).apply {
                action = FreefireVpnService.ACTION_STOP
            }
        )
        onVpnStopped()
    }

    private fun onVpnStopped() {
        OverlayService.stop(getApplication())
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

    // ─── Mở game đang chọn (Free Fire / Free Fire Max) ───────────────────────

    private fun launchFreeFire(ctx: Context) {
        val game = _state.value.selectedGame
        try {
            val intent = Intent().apply {
                component = ComponentName(game.packageName, game.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = ctx.packageManager.getLaunchIntentForPackage(game.packageName)
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(fallback)
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Chọn game & server ───────────────────────────────────────────────────

    fun selectGame(game: GameOption) {
        _state.update {
            it.copy(
                selectedGame = game,
                serverName = game.displayName,
                serverHint = "Tự động",
                serverCountryCode = game.code,
            )
        }
        syncOverlayState()
    }

    fun selectServer(server: Server?) {
        val wasActive = _state.value.status != ConnectionStatus.Idle
        _state.update {
            it.withServerDisplay(server).copy(isAuto = server == null, selected = server)
        }
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
                    val down = Random.nextDouble(60.0, 110.0)
                    val up = Random.nextDouble(20.0, 50.0)
                    _state.update { it.copy(downMbps = down, upMbps = up) }
                    val basePing = _state.value.current?.pingMs ?: 38
                    val nextPing = (basePing + Random.nextInt(-5, 6)).coerceAtLeast(12)
                    OverlayService.stateFlow.update { ov ->
                        ov.copy(
                            downloadMbps = down.toFloat(),
                            uploadMbps = up.toFloat(),
                            pingMs = nextPing,
                            pingHistory = (ov.pingHistory.drop(1) + nextPing),
                        )
                    }
                }
            }
        }
    }

    private fun syncOverlayState() {
        val s = _state.value
        val ping = s.current?.pingMs ?: 38
        OverlayService.stateFlow.update {
            it.copy(
                serverName = s.serverName,
                serverCountryCode = s.serverCountryCode,
                pingMs = ping,
                downloadMbps = s.downMbps.toFloat(),
                uploadMbps = s.upMbps.toFloat(),
            )
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

    private fun HomeUiState.withServerDisplay(server: Server?): HomeUiState = copy(
        serverName = selectedGame.displayName,
        serverHint = "Tự động",
        serverCountryCode = selectedGame.code,
    )

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unregisterReceiver(vpnReceiver) } catch (_: Exception) {}
        connectTimeoutJob?.cancel()
        elapsedJob?.cancel()
        statsJob?.cancel()
    }

    private companion object {
        val COUNTRY_CODES = mapOf(
            "Singapore" to "SG", "Việt Nam" to "VN", "Nhật Bản" to "JP",
            "Hàn Quốc" to "KR", "Hồng Kông" to "HK", "Thái Lan" to "TH",
            "Úc" to "AU", "Hoa Kỳ" to "US", "Anh" to "GB",
            "Đức" to "DE", "Brazil" to "BR",
        )
    }
}
