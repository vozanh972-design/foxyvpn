package com.example.foxy.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foxy.ui.home.BottomNav
import com.example.foxy.ui.home.HomeTab
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.path
import com.example.foxy.ui.theme.VeilColors as C
import com.example.foxy.ui.theme.VeilTheme

private val BackArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color(0xFF1C1F1D)),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 19f)
        lineTo(8f, 12f)
        lineTo(15f, 5f)
    }.build()
}

/** Điểm vào: gắn ViewModel vào màn hình Tài khoản. */
@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = viewModel(),
    onBack: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onProtocolClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    showBottomNav: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    ProfileScreen(
        state = state,
        onBack = onBack,
        onAutoConnectChange = viewModel::onAutoConnectChange,
        onUpgradeClick = onUpgradeClick,
        onProtocolClick = onProtocolClick,
        onLanguageClick = onLanguageClick,
        onHelpClick = onHelpClick,
        onLogout = onLogout,
        onTabSelected = onTabSelected,
        showBottomNav = showBottomNav,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit = {},
    onAutoConnectChange: (Boolean) -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onProtocolClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onTabSelected: (HomeTab) -> Unit = {},
    showBottomNav: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val gradientEnd = with(LocalDensity.current) { 460.dp.toPx() }
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.HeaderTint, C.Background), startY = 0f, endY = gradientEnd))
            .statusBarsPadding(),
    ) {
        // Thanh tiêu đề trên cùng với nút quay lại
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BackArrowIcon,
                    contentDescription = "Quay lại",
                    tint = C.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Tài khoản",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = C.Ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            ProfileHeader(state, Modifier.padding(top = 16.dp, bottom = 22.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                    SettingRow(Icons.Rounded.WorkspacePremium, "Gói hiện tại", Modifier.clickable(role = Role.Button, onClick = onUpgradeClick)) {
                        Text(state.planLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.AccentText)
                    }
                    RowDivider()
                    SettingRow(Icons.Rounded.VerifiedUser, "Giao thức", Modifier.clickable(role = Role.Button, onClick = onProtocolClick)) {
                        Text(state.protocol, fontSize = 13.sp, color = C.Ink)
                    }
                    RowDivider()
                    SettingRow(Icons.Rounded.Bolt, "Tự động kết nối", Modifier.toggleable(state.autoConnect, role = Role.Switch, onValueChange = onAutoConnectChange)) {
                        PillSwitch(state.autoConnect)
                    }
                    RowDivider()
                    SettingRow(Icons.Rounded.Public, "Ngôn ngữ", Modifier.clickable(role = Role.Button, onClick = onLanguageClick)) {
                        Text(state.language, fontSize = 13.sp, color = C.Ink)
                    }
                    RowDivider()
                    SettingRow(Icons.Rounded.Help, "Trợ giúp", Modifier.clickable(role = Role.Button, onClick = onHelpClick)) {
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = C.Muted)
                    }
                }
            }
            Text(
                "Đăng xuất",
                color = Color(0xFFC0392B),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp).clickable(role = Role.Button, onClick = onLogout).padding(vertical = 8.dp),
            )
            if (!showBottomNav) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(80.dp))
            }
        }
        if (showBottomNav) {
            Box(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                BottomNav(HomeTab.Account, onTabSelected)
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).background(C.AccentTint, CircleShape), contentAlignment = Alignment.Center) {
            Text(state.name.take(1).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.AccentText)
        }
        Text(state.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(top = 12.dp))
        Text(state.email, fontSize = 13.sp, color = C.Muted)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(C.AccentTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = C.AccentText)
        }
        Text(label, color = C.Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEECE7)))
}

@Composable
private fun PillSwitch(checked: Boolean) {
    Box(
        Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp))
            .background(if (checked) C.Accent else Color(0xFFDEDCD6)).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(20.dp).background(Color.White, CircleShape)) }
}

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable
private fun PreviewProfile() = VeilTheme { ProfileScreen(ProfileUiState(), {}, {}, {}, {}, {}, {}, {}) }
