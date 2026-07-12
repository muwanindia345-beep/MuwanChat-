package com.muwan.muwanchat.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen

@Composable
fun CreateGroupScreen(navController: NavController) {

    var groupName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }

    // Placeholder counters — actual picker/search screens abhi wired nahi hain,
    // isliye Confirm + dono add-member boxes filhaal Coming Soon dikhate hain.
    var comingSoonFeature by remember { mutableStateOf<String?>(null) }

    // AvatarCropScreen ka wahi handoff pattern jo ProfileScreen use karta hai —
    // cropped avatar wapas savedStateHandle se aata hai.
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value
    LaunchedEffect(croppedAvatar) {
        if (croppedAvatar != null) {
            avatarBase64 = croppedAvatar
            savedStateHandle?.remove<String>("cropped_avatar")
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            AvatarTransfer.pickedUri = it
            navController.navigate(Screen.AvatarCrop.route)
        }
    }

    val previewName = groupName.ifBlank { "New Group" }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            // ─── Header ───
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
                Text(
                    "New Group",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            // ─── Scrollable body — kaafi fields hain isliye scroll zaroori hai ───
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ─── Avatar picker ───
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        avatarBase64 = avatarBase64,
                        fallbackText = previewName,
                        size = 110.dp,
                        fontSize = 36.sp
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Set group photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Tap to add group photo",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Name ───
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name", color = Color(0xFF888888)) },
                    placeholder = { Text("New Group", color = Color(0xFF555555)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = groupFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Description ───
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = Color(0xFF888888)) },
                    placeholder = {
                        Text(
                            "Purpose, rules, kuch bhi likh sakte ho",
                            color = Color(0xFF555555)
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = groupFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    "Add members",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ─── Add from existing contacts ───
                AddMemberRow(
                    icon = Icons.Filled.Person,
                    title = "Add from contacts",
                    subtitle = "Apne accepted connections se select karo",
                    onClick = { comingSoonFeature = "👤 Add Members" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ─── Search / random add ───
                AddMemberRow(
                    icon = Icons.Filled.Search,
                    title = "Search members",
                    subtitle = "Kisi ko bhi search karke add karo",
                    onClick = { comingSoonFeature = "🔍 Search Members" }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ─── Confirm ───
                Button(
                    onClick = { comingSoonFeature = "👥 Group Chat" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AddMemberRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkHeader)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarkAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF888888), fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
    }
}

@Composable
private fun groupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DarkAccent,
    unfocusedBorderColor = Color(0xFF444444),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = DarkAccent
)
