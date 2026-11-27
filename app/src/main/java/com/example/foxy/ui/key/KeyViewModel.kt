package com.example.foxy.ui.key

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.KeyStore
import com.example.foxy.data.ResilientLicenseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeyUiState(
    val raw: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    /** Chỉ chuyển sang true khi server check.php trả về status=success & result=OK. */
    val activated: Boolean = false,
)

class KeyViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = KeyStore(application)
    private val licenseClient = ResilientLicenseClient()

    private val _state = MutableStateFlow(KeyUiState())
    val state: StateFlow<KeyUiState> = _state.asStateFlow()

    fun onKeyChange(input: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(raw = input.trim(), error = null) }
    }

    fun onActivate(inputKey: String = _state.value.raw) {
        val trimmed = inputKey.trim()
        val current = _state.value
        if (current.isLoading) return

        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "Vui lòng nhập key để tiếp tục") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(raw = trimmed, isLoading = true, error = null) }
            val signature = keyStore.getOrCreateDeviceSignature()
            val res = licenseClient.verify(
                endpointUrl = ResilientLicenseClient.CHECK_KEY_URL,
                licenseKey = trimmed,
                deviceSignature = signature,
            )
            if (res.status == ResilientLicenseClient.LicenseStatus.VALID) {
                keyStore.save(
                    key = res.key.ifBlank { trimmed },
                    type = res.type,
                    expiresAt = res.expiresAt,
                )
                _state.update { it.copy(isLoading = false, activated = true, error = null) }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        activated = false,
                        error = res.message ?: "Key không hợp lệ hoặc không thể kết nối máy chủ",
                    )
                }
            }
        }
    }
}
