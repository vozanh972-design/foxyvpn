package com.example.foxy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
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
    /** true = tự động chọn máy chủ ngẫu nhiên ở xa khi bấm kết nối. */
    val isAuto: Boolean = true,
    /** Máy chủ người dùng tự chọn (chỉ dùng khi isAuto = false). */
    val selected: Server? = null,
    /** Máy chủ đang kết nối hoặc đã kết nối. */
    val current: Server? = null,
    val elapsedSeconds: Long = 0,
    val downMbps: Double = 0.0,
    val upMbps: Double = 0.0,
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onPowerClick() {
        when (_state.value.status) {
            ConnectionStatus.Idle -> connect()
            ConnectionStatus.Connected -> disconnect()
            ConnectionStatus.Connecting -> Unit
        }
    }

    /** server = null nghĩa là chọn chế độ "Ngẫu nhiên". */
    fun selectServer(server: Server?) {
        val wasActive = _state.value.status != ConnectionStatus.Idle
        _state.update { it.copy(isAuto = server == null, selected = server) }
        if (wasActive) {
            disconnect()
            connect()
        }
    }

    private fun connect() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = _state.value
            val target = (if (s.isAuto) null else s.selected) ?: pickRandomFar(s.current)
            _state.update { it.copy(status = ConnectionStatus.Connecting, current = target) }

            // TODO: thay đoạn chờ này bằng việc dựng kết nối VPN thật (VpnService / WireGuard...).
            delay(CONNECT_DURATION_MS)

            _state.update {
                it.copy(
                    status = ConnectionStatus.Connected,
                    elapsedSeconds = 0,
                    downMbps = Random.nextDouble(60.0, 100.0),
                    upMbps = Random.nextDouble(20.0, 45.0),
                )
            }
            while (true) {
                delay(1000)
                _state.update {
                    it.copy(
                        elapsedSeconds = it.elapsedSeconds + 1,
                        downMbps = Random.nextDouble(60.0, 100.0),
                        upMbps = Random.nextDouble(20.0, 45.0),
                    )
                }
            }
        }
    }

    private fun disconnect() {
        job?.cancel()
        job = null
        _state.update {
            it.copy(status = ConnectionStatus.Idle, elapsedSeconds = 0, downMbps = 0.0, upMbps = 0.0)
        }
    }

    private fun pickRandomFar(previous: Server?): Server =
        ServerRepository.far.filter { it != previous }.random()

    private companion object {
        const val CONNECT_DURATION_MS = 2800L
    }
}
