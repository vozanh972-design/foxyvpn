package com.example.foxy

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.ui.home.HomeScreen
import com.example.foxy.ui.home.HomeViewModel
import com.example.foxy.ui.home.ServerPickerDialog
import com.example.foxy.ui.theme.FoxyTheme

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
                val viewModel: HomeViewModel = viewModel()
                var showPicker by remember { mutableStateOf(false) }

                HomeScreen(
                    viewModel = viewModel,
                    onMenuClick = { /* TODO: mở menu 3 gạch (Drawer) */ },
                    onLocationClick = { showPicker = true },
                )

                if (showPicker) {
                    ServerPickerDialog(
                        onDismiss = { showPicker = false },
                        onPick = {
                            viewModel.selectServer(it)
                            showPicker = false
                        },
                    )
                }
            }
        }
    }
}
