package com.example.foxy

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.data.KeyStore
import com.example.foxy.ui.drawer.AppDrawer
import com.example.foxy.ui.home.ConnectionStatus
import com.example.foxy.ui.home.HomeScreen
import com.example.foxy.ui.home.HomeViewModel
import com.example.foxy.ui.home.ServerPickerSheet
import com.example.foxy.ui.key.KeyScreen
import com.example.foxy.ui.splash.SplashScreen
import com.example.foxy.ui.theme.FoxyTheme
import com.example.foxy.ui.theme.ResponsiveContainer

class MainActivity : ComponentActivity() {

    // Launcher xử lý kết quả xin quyền VPN từ hệ thống Android
    // Giống cách Pigboy dùng REQ_VPN để xin quyền VpnService
    private lateinit var homeViewModel: HomeViewModel

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User đồng ý → bật VPN + mở game
            homeViewModel.onVpnPermissionGranted()
        } else {
            // User từ chối
            homeViewModel.onVpnPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            FoxyTheme {
                ResponsiveContainer {
                    val context    = LocalContext.current
                    val keyStore   = remember { KeyStore(context) }
                    var showSplash by remember { mutableStateOf(true) }
                    var isActivated by remember { mutableStateOf(keyStore.hasKey()) }

                    Crossfade(targetState = showSplash, label = "SplashTransition") { inSplash ->
                        if (inSplash) {
                            SplashScreen(onFinish = { showSplash = false })
                        } else {
                            Crossfade(targetState = isActivated, label = "ScreenTransition") { activated ->
                                if (!activated) {
                                    KeyScreen(
                                        onActivated = { isActivated = true },
                                        onBuyClick  = {},
                                    )
                                } else {
                                    // Khởi tạo ViewModel dùng chung cho cả Activity
                                    val vm: HomeViewModel = viewModel()
                                    homeViewModel = vm  // giữ ref để vpnPermissionLauncher truy cập

                                    val homeState by vm.state.collectAsState()
                                    var showPicker by remember { mutableStateOf(false) }
                                    var showDrawer by remember { mutableStateOf(false) }

                                    // Theo dõi flag needsVpnPermission — khi true thì xin quyền
                                    if (homeState.needsVpnPermission) {
                                        val permIntent = VpnService.prepare(context)
                                        if (permIntent != null) {
                                            vpnPermissionLauncher.launch(permIntent)
                                        } else {
                                            vm.onVpnPermissionGranted()
                                        }
                                    }

                                    HomeScreen(
                                        viewModel      = vm,
                                        onMenuClick    = { showDrawer = true },
                                        onLocationClick = { showPicker = true },
                                    )

                                    ServerPickerSheet(
                                        isOpen        = showPicker,
                                        currentServer = if (homeState.isAuto) null else homeState.selected,
                                        isAuto        = homeState.isAuto,
                                        onClose       = { showPicker = false },
                                        onPick        = {
                                            vm.selectServer(it)
                                            showPicker = false
                                        },
                                    )

                                    AppDrawer(
                                        isOpen   = showDrawer,
                                        onClose  = { showDrawer = false },
                                        onSignOut = {
                                            if (homeState.status == ConnectionStatus.Connected) {
                                                vm.onPowerClick()
                                            }
                                            keyStore.clear()
                                            isActivated = false
                                            showDrawer  = false
                                        },
                                        userKey = keyStore.get(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
