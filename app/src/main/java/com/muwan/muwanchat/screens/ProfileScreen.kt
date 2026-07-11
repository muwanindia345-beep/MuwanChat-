package com.muwan.muwanchat.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.MyProfileEntity
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ProfileUpdateBody
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController, mode: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOnboarding = mode == "onboarding"
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    var name by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var customGender by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }
    var hasFetchedProfile by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(!isOnboarding && !hasFetchedProfile) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun applyProfileFields(name_: String?, bio_: String?, city_: String?, country_: String?, gender_: String?, avatar_: String?) {
        name = name_ ?: ""
        bio = bio_ ?: ""
        city = city_ ?: ""
        country = country_ ?: ""
        avatarBase64 = avatar_
        val g = gender_ ?: ""
        if (g == "Male" || g == "Female") {
            gender = g
        } else if (g.isNotEmpty()) {
            gender = "Custom"
            customGender = g
        }
    }

    LaunchedEffect(Unit) {
        if (!isOnboarding && !hasFetchedProfile) {
            // 1) Room cache se turant fields bhar do — offline pe bhi khaali screen na dikhe
            db.myProfileDao().get()?.let { cached ->
                applyProfileFields(cached.name, cached.bio, cached.city, cached.country, cached.gender, cached.avatar)
                isLoading = false
            }

            // 2) Background me network se fresh data lao, UI + cache dono update
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
                val res = RetrofitClient.authApi.me("Bearer $token")
                if (res.isSuccessful) {
                    val user = res.body()?.user
                    applyProfileFields(user?.name, user?.bio, user?.city, user?.country, user?.gender, user?.avatar)
                    db.myProfileDao().upsert(
                        MyProfileEntity(
                            name = user?.name, bio = user?.bio, city = user?.city,
                            country = user?.country, gender = user?.gender, avatar = user?.avatar
                        )
                    )
                }
                hasFetchedProfile = true
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    // Receives the cropped avatar coming back from AvatarCropScreen
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

    fun save() {
        if (name.isBlank()) {
            errorMsg = "Name daalna zaroori hai"
            return
        }
        val finalGender = if (gender == "Custom") customGender.trim() else gender
        scope.launch {
            isSaving = true
            errorMsg = ""
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.usersApi.updateProfile(
                    "Bearer $token",
                    ProfileUpdateBody(
                        name = name.trim(),
                        bio = bio.trim().ifBlank { null },
                        city = city.trim().ifBlank { null },
                        country = country.trim().ifBlank { null },
                        gender = finalGender.ifBlank { null },
                        avatar = avatarBase64
                    )
                )
                if (res.isSuccessful) {
                    db.myProfileDao().upsert(
                        MyProfileEntity(
                            name = name.trim(), bio = bio.trim().ifBlank { null },
                            city = city.trim().ifBlank { null }, country = country.trim().ifBlank { null },
                            gender = finalGender.ifBlank { null }, avatar = avatarBase64
                        )
                    )
                    if (isOnboarding) {
                        navController.navigate(Screen.ConversationList.route) {
                            popUpTo(Screen.Profile.route) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                } else {
                    errorMsg = "Save failed, try again"
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Network error"
            }
            isSaving = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        if (isLoading) {
            CircularProgressIndicator(color = DarkAccent, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkHeader)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isOnboarding) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    Text(
                        if (isOnboarding) "Set up your profile" else "Edit Profile",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(DarkHeader)
                            .clickable { photoPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = remember(avatarBase64) {
                            avatarBase64?.let {
                                try {
                                    val bytes = Base64.decode(it, Base64.NO_WRAP)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) { null }
                            }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = "Add photo",
                                tint = DarkAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        TextButton(onClick = { photoPicker.launch("image/*") }) {
                            Text(if (avatarBase64 != null) "Change photo" else "Add photo", color = DarkAccent, fontSize = 13.sp)
                        }
                        if (avatarBase64 != null) {
                            TextButton(onClick = { avatarBase64 = null }) {
                                Text("Remove", color = Color(0xFF888888), fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    ProfileField("Name", name) { name = it }
                    Spacer(modifier = Modifier.height(14.dp))
                    ProfileField("Description", bio, singleLine = false) { bio = it }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileField("City", city) { city = it }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileField("Country", country) { country = it }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Gender", color = Color(0xFF888888), fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Male", "Female", "Custom").forEach { option ->
                            val selected = gender == option
                            Text(
                                option,
                                color = if (selected) Color.White else Color(0xFF888888),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) DarkAccent else DarkHeader)
                                    .clickable { gender = option }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (gender == "Custom") {
                        Spacer(modifier = Modifier.height(10.dp))
                        ProfileField("Your gender", customGender) { customGender = it }
                    }

                    if (errorMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(if (isOnboarding) "Continue" else "Save", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Color(0xFF888888)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DarkAccent,
            unfocusedBorderColor = Color(0xFF444444),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = DarkAccent
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
