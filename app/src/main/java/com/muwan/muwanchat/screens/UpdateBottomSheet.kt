package com.muwan.muwanchat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.network.AppVersionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    info: AppVersionInfo,
    onUpdate: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState, containerColor = DarkHeader) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Version ${info.versionName} is available", color = Color(0xFF888888), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(info.changelog, color = Color(0xFFCCCCCC), fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Update \u2705", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = Color(0xFF888888), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
