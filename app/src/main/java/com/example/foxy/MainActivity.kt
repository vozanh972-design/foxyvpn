package com.example.foxy

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.data.KeyStore
import com.example.foxy.ui.home.HomeRoute
import com.example.foxy.ui.home.HomeTab
import com.example.foxy.ui.home.HomeViewModel
import com.example.foxy.ui.home.ServerPickerSheet
import com.example.foxy.ui.key.KeyScreen
import com.example.foxy.ui.splash.SplashScreen
import com.example.foxy.ui.theme.FoxyTheme
import com.example.foxy.ui.theme.ResponsiveContainer
import com.example.foxy.ui.theme.VeilTheme

class MainActivity : ComponentActivity() {

    private lateinit var homeViewModel: HomeViewModel

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        homeViewModel.onOverlayPermissionResult(Settings.canDrawOverlays(this))
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            homeViewModel.onVpnPermissionGranted()
        } else {
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
            val context    = LocalContext.current
            val view       = LocalView.current
            val keyStore   = remember { KeyStore(context) }
            var showSplash by remember { mutableStateOf(true) }
            var isActivated by remember { mutableStateOf(keyStore.hasKey()) }

            val isHomeVisible = !showSplash && isActivated
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isHomeVisible
                }
            }

            FoxyTheme {
                ResponsiveContainer {
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
                                    val vm: HomeViewModel = viewModel()
                                    homeViewModel = vm

                                    val homeState by vm.state.collectAsState()
                                    var showPicker by remember { mutableStateOf(false) }

                                    LaunchedEffect(homeState.needsOverlayPermission) {
                                        if (homeState.needsOverlayPermission) {
                                            if (!Settings.canDrawOverlays(context)) {
                                                overlayPermissionLauncher.launch(
                                                    Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}"),
                                                    )
                                                )
                                            } else {
                                                vm.onOverlayPermissionResult(true)
                                            }
                                        }
                                    }

                                    LaunchedEffect(homeState.needsVpnPermission) {
                                        if (homeState.needsVpnPermission) {
                                            val permIntent = VpnService.prepare(context)
                                            if (permIntent != null) {
                                                vpnPermissionLauncher.launch(permIntent)
                                            } else {
                                                vm.onVpnPermissionGranted()
                                            }
                                        }
                                    }

                                    VeilTheme {
                                        HomeRoute(
                                            viewModel     = vm,
                                            onServerClick = { showPicker = true },
                                            onTabSelected = { tab ->
                                                when (tab) {
                                                    HomeTab.Servers -> showPicker = true
                                                    else -> Unit
                                                }
                                            },
                                        )
                                    }

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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
