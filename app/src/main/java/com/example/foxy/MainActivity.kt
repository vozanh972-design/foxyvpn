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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.data.KeyStore
import com.example.foxy.ui.home.CountryFlag
import com.example.foxy.ui.home.GameOption
import com.example.foxy.ui.home.HomeRoute
import com.example.foxy.ui.home.HomeTab
import com.example.foxy.ui.home.HomeViewModel
import com.example.foxy.ui.key.KeyScreen
import com.example.foxy.ui.splash.SplashScreen
import com.example.foxy.ui.theme.FoxyTheme
import com.example.foxy.ui.theme.ResponsiveContainer
import com.example.foxy.ui.theme.VeilColors
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

                                    if (showPicker) {
                                        GamePickerSheet(
                                            selected = homeState.selectedGame,
                                            onClose = { showPicker = false },
                                            onSelect = {
                                                vm.selectGame(it)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamePickerSheet(
    selected: GameOption,
    onClose: () -> Unit,
    onSelect: (GameOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Chọn phiên bản game",
                    fontWeight = FontWeight.Bold,
                    color = VeilColors.Ink,
                    fontSize = 18.sp,
                )
                TextButton(onClick = onClose) {
                    Text("Đóng", color = VeilColors.AccentText, fontWeight = FontWeight.SemiBold)
                }
            }
            GameOption.entries.forEach { game ->
                val on = game == selected
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (on) VeilColors.AccentTint else VeilColors.Background)
                        .border(1.5.dp, if (on) VeilColors.Accent else VeilColors.Border, shape)
                        .clickable { onSelect(game) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CountryFlag(game.code, 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(game.displayName, fontWeight = FontWeight.Bold, color = VeilColors.Ink, fontSize = 15.sp)
                        Text("Tự động", color = VeilColors.Muted, fontSize = 12.sp)
                    }
                    if (on) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = VeilColors.AccentText)
                }
            }
        }
    }
}
