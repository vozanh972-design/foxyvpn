package com.example.foxy.ui.profile

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val name: String = "Robert Fox",
    val email: String = "robert.fox@gmail.com",
    val planLabel: String = "Miễn phí",
    val protocol: String = "Tự động",
    val autoConnect: Boolean = true,
    val language: String = "Tiếng Việt",
)

class ProfileViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onAutoConnectChange(enabled: Boolean) {
        _state.update { it.copy(autoConnect = enabled) }
        // TODO: lưu lựa chọn (DataStore) và áp dụng cho service VPN.
    }
}
