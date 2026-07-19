package com.muwan.muwanchat.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
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
    val isOwner = group?.owner == myUid
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

    fun deleteGroupPermanently() {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.deleteGroup("Bearer $token", groupId)
                if (res.isSuccessful) {
                    val db = MuwanChatDb.get(context, myUid)
                    ChatRepository.deleteChatsLocally(db, setOf(groupId))
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.ConversationList.route) { inclusive = true }
                    }
                } else {
                    Toast.makeText(context, "Group delete nahi hua", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
            showDeleteConfirm = false
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    InviteCircleButton(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        tint = DarkAccent,
                        enabled = g?.inviteCode != null,
                        onClick = {
                            val code = g?.inviteCode ?: return@InviteCircleButton
                            val shareText = "Join \"${g.name}\" on MuwanChat: $INVITE_LINK_PREFIX$code"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share invite link"))
                        }
                    )
                    InviteCircleButton(
                        icon = Icons.Filled.ContentCopy,
                        label = "Copy",
                        tint = DarkAccent,
                        enabled = g?.inviteCode != null,
                        onClick = {
                            val code = g?.inviteCode ?: return@InviteCircleButton
                            clipboard.setText(AnnotatedString("$INVITE_LINK_PREFIX$code"))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    if (isAdmin) {
                        InviteCircleButton(
                            icon = Icons.Filled.Refresh,
                            label = "Reset",
                            tint = Color(0xFFFF3B30),
                            enabled = !isBusy,
                            onClick = { regenerateInvite() }
                        )
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

                if (isOwner) {
                    Spacer(Modifier.height(12.dp))
                    SettingsSectionLabel("DANGER ZONE")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) { showDeleteConfirm = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFFF3B30))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Delete Group", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Permanently deletes this group and all messages for everyone",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteConfirm = false },
            title = { Text("Delete Group?") },
            text = { Text("This permanently deletes \"${group?.name}\" and all its messages for every member. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { deleteGroupPermanently() }, enabled = !isBusy) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !isBusy) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InviteCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val effectiveTint = if (enabled) tint else Color(0xFF555577)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(effectiveTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = effectiveTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = effectiveTint, fontSize = 12.sp)
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
