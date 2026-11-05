package com.example.foxy.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foxy.data.Server
import com.example.foxy.data.ServerRepository
import com.example.foxy.ui.theme.FoxyColors

/** Hộp thoại chọn vị trí đơn giản (tạm thời). Có thể thay bằng một màn hình riêng. */
@Composable
fun ServerPickerDialog(onDismiss: () -> Unit, onPick: (Server?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn vị trí") },
        text = {
            LazyColumn {
                item { PickerRow("🌐", "Ngẫu nhiên", "Tự động chọn máy chủ xa", "Auto") { onPick(null) } }
                items(ServerRepository.all) { s ->
                    PickerRow(s.flag, s.country, s.city, "${s.pingMs} ms") { onPick(s) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun PickerRow(flag: String, title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flag, fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = FoxyColors.Ink)
            Text(subtitle, fontSize = 12.sp, color = FoxyColors.Muted)
        }
        Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FoxyColors.Violet2)
    }
}
