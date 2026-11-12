package com.example.foxy.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.R
import com.example.foxy.ui.theme.VeilColors

private val CardBg = Color.White.copy(alpha = 0.07f)
private val Soft = Color(0xFF9FB3B6)

enum class AimTarget(val label: String) {
    Neck("Cổ"),
    Body("Thân"),
    Foot("Chân"),
}

data class BoostState(
    val aimTarget: AimTarget = AimTarget.Neck,
)

/** Nội dung tab "Tăng tốc" (tia sét) trong bảng trong game: khung ảnh nhân vật bên trái + vị trí mục tiêu bên phải. */
@Composable
internal fun BoostContent(
    boost: BoostState,
    onChange: (BoostState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.character_aim),
                contentDescription = "Nhân vật",
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AimTargetCard(
            selected = boost.aimTarget,
            onSelect = { onChange(boost.copy(aimTarget = it)) },
            modifier = Modifier.width(236.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun AimTargetCard(selected: AimTarget, onSelect: (AimTarget) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VỊ TRÍ MỤC TIÊU", fontSize = 11.sp, letterSpacing = 0.8.sp, color = Soft)
        AimTarget.entries.forEach { target ->
            val on = target == selected
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(shape)
                    .background(if (on) VeilColors.Accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                    .border(1.5.dp, if (on) VeilColors.Accent.copy(alpha = 0.75f) else Color.Transparent, shape)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(target) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(target.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(22.dp).clip(CircleShape).then(
                        if (on) Modifier.background(VeilColors.Accent)
                        else Modifier.border(1.5.dp, Color(0xFF5B6E72), CircleShape)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = VeilColors.Ink)
                }
            }
        }
    }
}
