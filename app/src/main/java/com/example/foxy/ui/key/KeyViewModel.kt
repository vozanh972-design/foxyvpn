package com.example.foxy.ui.key

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foxy.data.KeyStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val KEY_LENGTH = 16

data class KeyUiState(
    /** Key đã bỏ dấu gạch, viết hoa, tối đa 16 ký tự. */
    val raw: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    /** Chuyển sang true khi kích hoạt thành công. */
    val activated: Boolean = false,
)

class KeyViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = KeyStore(application)

    private val _state = MutableStateFlow(KeyUiState())
    val state: StateFlow<KeyUiState> = _state.asStateFlow()

    /** Nhận chuỗi người dùng gõ hoặc dán vào, tự làm sạch. */
    fun onKeyChange(input: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(raw = sanitize(input), error = null) }
    }

    fun onActivate() {
        val current = _state.value
        if (current.isLoading) return

        if (current.raw.isBlank()) {
            _state.update { it.copy(error = "Vui lòng nhập key để tiếp tục") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val signature = keyStore.getOrCreateDeviceSignature()
            val valid = validateKey(current.raw, signature)
            if (valid) {
                keyStore.save(current.raw)
                _state.update { it.copy(isLoading = false, activated = true) }
            } else {
                _state.update { it.copy(isLoading = false, error = "Key không hợp lệ hoặc đã hết hạn") }
            }
        }
    }

    /**
     * Xác thực key kèm chữ ký thiết bị trọn đời (sau này sẽ gửi lên API server: key + deviceSignature).
     */
    private suspend fun validateKey(raw: String, deviceSignature: String): Boolean {
        delay(600)
        return raw.isNotBlank() && deviceSignature.isNotEmpty()
    }

    private fun sanitize(input: String): String =
        input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(KEY_LENGTH)
}
