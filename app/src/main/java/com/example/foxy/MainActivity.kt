package com.example.foxy

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.data.KeyStore
import com.example.foxy.ui.home.HomeScreen
import com.example.foxy.ui.home.HomeViewModel
import com.example.foxy.ui.home.ServerPickerDialog
import com.example.foxy.ui.key.KeyScreen
import com.example.foxy.ui.splash.SplashScreen
import com.example.foxy.ui.theme.FoxyTheme
import com.example.foxy.ui.theme.ResponsiveContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // Thanh trạng thái nằm trên nền tím => icon sáng; thanh điều hướng nằm trên nền trắng => icon tối
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            FoxyTheme {
                ResponsiveContainer {
                    val context = LocalContext.current
                    val keyStore = remember { KeyStore(context) }
                    var showSplash by remember { mutableStateOf(true) }
                    var isActivated by remember { mutableStateOf(keyStore.hasKey()) }

                    Crossfade(targetState = showSplash, label = "SplashTransition") { inSplash ->
                        if (inSplash) {
                            SplashScreen(
                                onFinish = {
                                    showSplash = false
                                },
                            )
                        } else {
                            Crossfade(targetState = isActivated, label = "ScreenTransition") { activated ->
                                if (!activated) {
                                    KeyScreen(
                                        onActivated = {
                                            isActivated = true
                                        },
                                        onBuyClick = {
                                            // Mở trang web mua gói hoặc thông báo
                                        },
                                    )
                                } else {
                                    val homeViewModel: HomeViewModel = viewModel()
                                    var showPicker by remember { mutableStateOf(false) }

                                    HomeScreen(
                                        viewModel = homeViewModel,
                                        onMenuClick = { /* TODO: mở menu 3 gạch (Drawer) */ },
                                        onLocationClick = { showPicker = true },
                                    )

                                    if (showPicker) {
                                        ServerPickerDialog(
                                            onDismiss = { showPicker = false },
                                            onPick = {
                                                homeViewModel.selectServer(it)
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
}
