package com.muwan.muwanchat.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ChangeEmailRequest
import com.muwan.muwanchat.network.ChangePasswordRequest
import com.muwan.muwanchat.network.ChangeUsernameRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Same rules as RegisterScreen — username/email changes go through the same
// validation as registration, per product decision.
private val USERNAME_REGEX = Regex("^[a-z0-9_.]{3,20}$")
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")

private fun usernameError(u: String): String? {
    if (u.isBlank()) return null
    if (u != u.trim() || u.contains(" ")) return "Spaces not allowed"
    if (!USERNAME_REGEX.matches(u)) return "3-20 chars: lowercase letters, numbers, _ . only"
    return null
}

private fun emailError(e: String): String? {
    if (e.isBlank()) return null
    if (EMAIL_REGEX.matchEntire(e.trim()) == null) return "Invalid email format"
    return null
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DarkAccent,
    unfocusedBorderColor = Color(0xFF444444),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = DarkAccent,
    errorBorderColor = Color(0xFFFF5555),
    errorTextColor = Color.White,
    errorLabelColor = Color(0xFFFF5555),
    errorCursorColor = DarkAccent,
    errorLeadingIconColor = DarkAccent,
    errorTrailingIconColor = Color(0xFF888888)
)

@Composable
fun AccountSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUsername by remember { mutableStateOf("") }
    var currentEmail by remember { mutableStateOf("") }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentUsername = AuthDataStore.getUsername(context).first() ?: ""
        currentEmail = AuthDataStore.getEmail(context).first() ?: ""
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
            Text("Account Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // Username
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUsernameDialog = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Username", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Username", color = Color.White, fontSize = 16.sp)
                Text(currentUsername.ifBlank { "Not set" }, color = Color(0xFF888888), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        // Email
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEmailDialog = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Email, contentDescription = "Email", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Email", color = Color.White, fontSize = 16.sp)
                Text(currentEmail.ifBlank { "Not set" }, color = Color(0xFF888888), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        // Password
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPasswordDialog = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Password", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Password", color = Color.White, fontSize = 16.sp)
                Text("••••••••", color = Color(0xFF888888), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))
// Delete Account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeleteDialog = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.DeleteForever, contentDescription = "Delete Account", tint = Color(0xFFFF3B30))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Delete Account", color = Color(0xFFFF3B30), fontSize = 16.sp)
                Text(
                    "Permanently delete your account and all data",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showUsernameDialog) {
        UsernameChangeDialog(
            currentUsername = currentUsername,
            onDismiss = { showUsernameDialog = false },
            onSuccess = { newUsername ->
                currentUsername = newUsername
                scope.launch { AuthDataStore.setUsername(context, newUsername) }
                showUsernameDialog = false
            }
        )
    }

    if (showEmailDialog) {
        EmailChangeDialog(
            currentEmail = currentEmail,
            onDismiss = { showEmailDialog = false },
            onSuccess = { newEmail ->
                currentEmail = newEmail
                scope.launch { AuthDataStore.setEmail(context, newEmail) }
                showEmailDialog = false
            }
        )
    }

    if (showPasswordDialog) {
        PasswordChangeDialog(onDismiss = { showPasswordDialog = false })
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                scope.launch {
                    AppSocketManager.disconnect()
                    AuthDataStore.clearAuth(context)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ConversationList.route) { inclusive = true }
                    }
                }
            }
        )
    }
}

@Composable
private fun UsernameChangeDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(currentUsername) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val usernameErr = usernameError(username)

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = DarkHeader,
        title = { Text("Change Username", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase(); errorMsg = "" },
                    label = { Text("Username", color = Color(0xFF888888)) },
                    isError = usernameErr != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = accountFieldColors()
                )
                AnimatedVisibility(visible = usernameErr != null) {
                    Text(usernameErr ?: "", color = Color(0xFFFF5555), fontSize = 12.sp)
                }
                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = Color(0xFFFF5555), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && username.isNotBlank() && usernameErr == null && username != currentUsername,
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            val token = "Bearer ${AuthDataStore.getToken(context).first()}"
                            val res = RetrofitClient.authApi.changeUsername(token, ChangeUsernameRequest(username))
                            if (res.isSuccessful && res.body()?.success == true) {
                                onSuccess(username)
                            } else {
                                errorMsg = res.body()?.error ?: res.errorBody()?.string() ?: "Failed to update username"
                            }
                        } catch (e: Exception) {
                            errorMsg = "Network error — try again"
                        }
                        isLoading = false
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkAccent, strokeWidth = 2.dp)
                } else {
                    Text("Save", color = DarkAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}

@Composable
private fun EmailChangeDialog(
    currentEmail: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newEmail by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val emailErr = emailError(newEmail)

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = DarkHeader,
        title = { Text("Change Email", color = Color.White) },
        text = {
            Column {
                Text("Current: $currentEmail", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it; errorMsg = "" },
                    label = { Text("New Email", color = Color(0xFF888888)) },
                    isError = emailErr != null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = accountFieldColors()
                )
                AnimatedVisibility(visible = emailErr != null) {
                    Text(emailErr ?: "", color = Color(0xFFFF5555), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; errorMsg = "" },
                    label = { Text("Current Password", color = Color(0xFF888888)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF888888)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = accountFieldColors()
                )
                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = Color(0xFFFF5555), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && newEmail.isNotBlank() && emailErr == null && currentPassword.isNotBlank(),
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            val token = "Bearer ${AuthDataStore.getToken(context).first()}"
                            val res = RetrofitClient.authApi.changeEmail(
                                token,
                                ChangeEmailRequest(newEmail.trim(), currentPassword)
                            )
                            if (res.isSuccessful && res.body()?.success == true) {
                                onSuccess(newEmail.trim())
                            } else {
                                errorMsg = res.body()?.error ?: res.errorBody()?.string() ?: "Failed to update email"
                            }
                        } catch (e: Exception) {
                            errorMsg = "Network error — try again"
                        }
                        isLoading = false
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkAccent, strokeWidth = 2.dp)
                } else {
                    Text("Save", color = DarkAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}
@Composable
private fun PasswordChangeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }

    val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword
    val tooShort = newPassword.isNotEmpty() && newPassword.length < 6

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = DarkHeader,
        title = { Text("Change Password", color = Color.White) },
        text = {
            if (successMsg.isNotEmpty()) {
                Text(successMsg, color = Color(0xFF4CAF50), fontSize = 14.sp)
            } else {
                Column {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; errorMsg = "" },
                        label = { Text("Current Password", color = Color(0xFF888888)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = accountFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMsg = "" },
                        label = { Text("New Password", color = Color(0xFF888888)) },
                        isError = tooShort,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF888888)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = accountFieldColors()
                    )
                    AnimatedVisibility(visible = tooShort) {
                        Text("Password min 6 characters", color = Color(0xFFFF5555), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = "" },
                        label = { Text("Confirm New Password", color = Color(0xFF888888)) },
                        isError = mismatch,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = accountFieldColors()
                    )
                    AnimatedVisibility(visible = mismatch) {
                        Text("Passwords do not match", color = Color(0xFFFF5555), fontSize = 12.sp)
                    }
                    if (errorMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg, color = Color(0xFFFF5555), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (successMsg.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = DarkAccent, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    enabled = !isLoading && currentPassword.isNotBlank() &&
                        newPassword.length >= 6 && confirmPassword == newPassword,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val token = "Bearer ${AuthDataStore.getToken(context).first()}"
                                val res = RetrofitClient.authApi.changePassword(
                                    token,
                                    ChangePasswordRequest(currentPassword, newPassword)
                                )
                                if (res.isSuccessful && res.body()?.success == true) {
                                    successMsg = "Password updated successfully"
                                } else {
                                    errorMsg = res.body()?.error ?: res.errorBody()?.string() ?: "Failed to update password"
                                }
                            } catch (e: Exception) {
                                errorMsg = "Network error — try again"
                            }
                            isLoading = false
                        }
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkAccent, strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = DarkAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (successMsg.isEmpty()) {
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var confirmChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = DarkHeader,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF3B30))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account", color = Color.White)
            }
        },
        text = {
            Column {
                Text(
                    "This will permanently delete your account and all data, including messages, " +
                        "conversations, and group memberships. This action cannot be undone.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmChecked = !confirmChecked }
                ) {
                    Checkbox(
                        checked = confirmChecked,
                        onCheckedChange = { confirmChecked = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF3B30))
                    )
                    Text(
                        "I understand this cannot be undone",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = Color(0xFFFF5555), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && confirmChecked,
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            val token = "Bearer ${AuthDataStore.getToken(context).first()}"
                            val res = RetrofitClient.authApi.deleteAccount(token)
                            if (res.isSuccessful && res.body()?.success == true) {
                                onDeleted()
                            } else {
                                errorMsg = res.body()?.error ?: res.errorBody()?.string() ?: "Failed to delete account"
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            errorMsg = "Network error — try again"
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFFFF3B30), strokeWidth = 2.dp)
                } else {
                    Text("Delete Permanently", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}
