package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SearchMembersForGroupScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var myUid by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    fun loadSuggestions() {
        isLoading = true
        errorMsg = ""
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.usersApi.getSuggestions("Bearer $token")
                if (res.isSuccessful) {
                    users = (res.body()?.users ?: emptyList()).filter { it.uid != myUid }
                } else errorMsg = "Couldn't load users"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Error"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        try {
            val res = RetrofitClient.authApi.me("Bearer $token")
            if (res.isSuccessful) myUid = res.body()?.user?.uid ?: ""
        } catch (_: Exception) {}
        loadSuggestions()
    }

    fun search() {
        if (query.isBlank()) {
            loadSuggestions()
            return
        }
        isLoading = true
        errorMsg = ""
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.usersApi.searchUsers("Bearer $token", query.trim())
                if (res.isSuccessful) {
                    users = (res.body()?.users ?: emptyList()).filter { it.uid != myUid }
                } else errorMsg = "Search failed"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Error"
            }
            isLoading = false
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
            Text("Search members", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Username search karo...", color = Color(0xFF888888)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF333355),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Button(
                onClick = { search() },
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White)
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        }

        LazyColumn {
            items(users, key = { it.uid }) { user ->
                val isAdded = GroupMemberSelection.isSelected(user.uid)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        AvatarView(
                            avatarBase64 = user.avatar,
                            fallbackText = user.username,
                            size = 46.dp,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(user.name ?: "", color = Color(0xFF888888), fontSize = 12.sp)
                        }
                    }

                    if (isAdded) {
                        Button(
                            onClick = { GroupMemberSelection.toggle(user) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333355)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Added", color = Color.White, fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { GroupMemberSelection.toggle(user) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Add", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
            }
        }
    }
}
