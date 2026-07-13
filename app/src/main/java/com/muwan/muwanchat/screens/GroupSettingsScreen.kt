package com.muwan.muwanchat.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.GroupSettingsRequest
import com.muwan.muwanchat.network.MuteRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// muwanchat:// custom scheme -- deep link ko actually app me khulne aur
// join-screen tak le jaane ka wiring (AndroidManifest intent-filter +
// NavGraph deep link) abhi is turn me nahi kiya hai, wo agla follow-up hai.
// Abhi ye link share/copy ke liye ek stable format hai.
private const val INVITE_LINK_PREFIX = "muwanchat://join/"

@Composable
fun GroupSettingsScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard: ClipboardManager = LocalClipboardManager.current

    var myUid by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    val isAdmin = group?.admins?.contains(myUid) == true

    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) group = res.body()?.group
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        isLoading = true
        try {
            refreshGroup()
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val muteRes = RetrofitClient.chatApi.getMuteStatus("Bearer $token", groupId)
                if (muteRes.isSuccessful) muted = muteRes.body()?.muted ?: false
            }
        } catch (_: Exception) {
        }
        isLoading = false
    }

    fun updateSetting(request: GroupSettingsRequest) {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.updateGroupSettings("Bearer $token", groupId, request)
                if (res.isSuccessful) {
                    group = res.body()?.group ?: group
                } else {
                    Toast.makeText(context, "Setting update nahi hui", Toast.LENGTH_SHORT).show()
                    refreshGroup()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun toggleMute(newValue: Boolean) {
        muted = newValue
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.muteRoom("Bearer $token", groupId, MuteRequest(muted = newValue))
                if (!res.isSuccessful) {
                    muted = !newValue
                    Toast.makeText(context, "Mute update nahi hui", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                muted = !newValue
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun regenerateInvite() {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.regenerateInvite("Bearer $token", groupId)
                if (res.isSuccessful && res.body()?.success == true) {
                    refreshGroup()
                    Toast.makeText(context, "Naya invite link ban gaya, purana kaam nahi karega", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invite link regenerate nahi hua", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Group Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else {
            val g = group
            Column(modifier = Modifier.fillMaxSize()) {

                SettingsSectionLabel("PERMISSIONS")

                SettingsToggleRow(
                    icon = Icons.Filled.HowToReg,
                    label = "Join Approval",
                    subtitle = "New members via link or invite need admin approval",
                    checked = g?.joinApprovalRequired == true,
                    enabled = isAdmin && !isBusy,
                    onCheckedChange = { updateSetting(GroupSettingsRequest(joinApprovalRequired = it)) }
                )
                Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

                SettingsToggleRow(
                    icon = Icons.Filled.GroupAdd,
                    label = "Allow members to add members",
                    subtitle = "Off = only admins can add new members",
                    checked = g?.membersCanAdd == true,
                    enabled = isAdmin && !isBusy,
                    onCheckedChange = { updateSetting(GroupSettingsRequest(membersCanAdd = it)) }
                )
                Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

                SettingsToggleRow(
                    icon = Icons.Filled.Campaign,
                    label = "Only admins can send messages",
                    subtitle = "Turns this into an announcement-style group",
                    checked = g?.onlyAdminsCanSend == true,
                    enabled = isAdmin && !isBusy,
                    onCheckedChange = { updateSetting(GroupSettingsRequest(onlyAdminsCanSend = it)) }
                )
                Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

                SettingsToggleRow(
                    icon = Icons.Filled.DoneAll,
                    label = "Read Receipts",
                    subtitle = "Off = no one sees who has seen a message",
                    checked = g?.readReceiptsEnabled != false,
                    enabled = isAdmin && !isBusy,
                    onCheckedChange = { updateSetting(GroupSettingsRequest(readReceiptsEnabled = it)) }
                )

                Spacer(Modifier.height(12.dp))
                SettingsSectionLabel("INVITE LINK")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = DarkAccent)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            g?.inviteCode?.let { "$INVITE_LINK_PREFIX$it" } ?: "No invite link",
                            color = Color.White, fontSize = 14.sp
                        )
                        Text("Tap share to send, or copy to clipboard", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val code = g?.inviteCode ?: return@OutlinedButton
                            val shareText = "Join \"${g.name}\" on MuwanChat: $INVITE_LINK_PREFIX$code"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share invite link"))
                        },
                        enabled = g?.inviteCode != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", color = Color.White, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val code = g?.inviteCode ?: return@OutlinedButton
                            clipboard.setText(AnnotatedString("$INVITE_LINK_PREFIX$code"))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        },
                        enabled = g?.inviteCode != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy", color = Color.White, fontSize = 13.sp)
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = { regenerateInvite() },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset", color = Color(0xFFFF3B30), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingsSectionLabel("NOTIFICATIONS")

                SettingsToggleRow(
                    icon = Icons.Filled.NotificationsOff,
                    label = "Mute this group",
                    subtitle = "You won't get push notifications for new messages",
                    checked = muted,
                    enabled = true,
                    onCheckedChange = { toggleMute(it) }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF888888),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DarkAccent)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Color(0xFF888888), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DarkAccent,
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2a2a4e)
            )
        )
    }
}
