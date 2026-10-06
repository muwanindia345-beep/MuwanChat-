#!/usr/bin/env python3
"""
STATUS STEP 3c (app) -- "My status" par apna profile pic
  Bug: My status par sirf initial ("T") dikhta tha, kyunki apna avatar Room ke my_profile cache se
       padha ja raha tha aur woh cache sirf tab bharta hai jab Profile screen khule.
  Fix: avatar pehle cache se (turant), cache mein na ho to ek baar server (/auth/me) se laake
       wahi my_profile cache bhar dete hain. Memory mein bhi rakha -> tab dobara kholne par turant.
  Viewer ke header mein bhi apna avatar ab dikhta hai.

Step 3 pehle chal chuka hona chahiye (step 3b ke pehle ya baad -- dono order theek hain).
MuwanChat--main repo root se:
    python3 patch_app_status_step3c.py --dry-run
    python3 patch_app_status_step3c.py
"""
import os
import sys

DRY = "--dry-run" in sys.argv
BASE = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat")
SCREENS = os.path.join(BASE, "screens")
MARK = "STATUS_V3"
errors = []


def read(path):
    with open(path, encoding="utf-8", newline="") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def eol_of(text):
    return "\r\n" if "\r\n" in text else "\n"


def put_file(name, content):
    path = os.path.join(SCREENS, name)
    if not os.path.exists(path):
        errors.append(name + " nahi mili -- pehle step 3 chalao")
        print("  [FAIL] " + name)
        return
    if MARK in read(path):
        print("  [skip] pehle se patched : " + name)
        return
    print("  [ ok ] update           : " + name)
    if not DRY:
        write(path, content)


def edit(path, label, old, new):
    text = read(path)
    if new.strip() in text:
        print("  [skip] " + label)
        return
    nl = eol_of(text)
    o = old.replace("\n", nl)
    n = new.replace("\n", nl)
    found = text.count(o)
    if found != 1:
        errors.append(path + " :: " + label + " -- anchor " + str(found) + " baar mili (chahiye 1)")
        print("  [FAIL] " + label)
        return
    print("  [ ok ] " + label)
    if not DRY:
        write(path, text.replace(o, n, 1))



STATUSCOMMON_KT = r'''package com.muwan.muwanchat.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.MyProfileEntity
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first

// STATUS_V2 / STATUS_V3 -- Status list, viewer, new-status aur chat list ke beech shared helpers.

val StatusBgColors = listOf("#D85A30", "#534AB7", "#0F6E56", "#185FA5", "#993556", "#854F0B")

const val STATUS_MAX_TEXT = 700

fun parseStatusColor(hex: String?): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex ?: "#534AB7"))
    } catch (_: Exception) {
        Color(0xFF534AB7)
    }
}

// Process-level memory cache: Status tab / chat list dobara khulte hi purana feed TURANT
// dikhta hai (blank ya loading nahi). Phir cache DB aur network chup-chaap update karte hain.
object StatusMemory {
    @Volatile
    var feed: StatusFeed? = null

    @Volatile
    var lastRefreshMs: Long = 0L

    const val REFRESH_EVERY_MS = 60_000L

    // Apna profile pic (base64). Memory mein rakha taaki "My status" par turant dikhe.
    @Volatile
    var myAvatar: String? = null

    @Volatile
    var profileFetched: Boolean = false
}

// Apna avatar: 1) Room cache (my_profile) turant  2) cache mein avatar na ho (Profile screen kabhi
// khuli hi nahi) to ek baar server se laake wahi cache bhar do -- ProfileScreen jaisa hi.
suspend fun loadMyAvatar(context: Context, onAvatar: (String?) -> Unit) {
    val db = MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context))
    val cached = try {
        db.myProfileDao().get()
    } catch (_: Exception) {
        null
    }
    if (cached?.avatar != null) {
        StatusMemory.myAvatar = cached.avatar
        onAvatar(cached.avatar)
        return
    }
    if (StatusMemory.profileFetched) return
    try {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.authApi.me("Bearer $token")
        val user = res.body()?.user
        if (res.isSuccessful && user != null) {
            StatusMemory.profileFetched = true
            db.myProfileDao().upsert(
                MyProfileEntity(
                    name = user.name,
                    bio = user.bio,
                    city = user.city,
                    country = user.country,
                    gender = user.gender,
                    avatar = user.avatar
                )
            )
            StatusMemory.myAvatar = user.avatar
            onAvatar(user.avatar)
        }
    } catch (_: Exception) {
        // offline: initial letter dikhta rahega, agli baar dobara try
    }
}

// 1) Room cache turant  2) network sirf tab jab pichla refresh 60 second se purana ho.
// Offline ho to network step bina kuch kiye khatam (repo.refresh() offline par false deta hai).
suspend fun StatusRepository.loadWithMemory(onUpdate: (StatusFeed) -> Unit) {
    cachedFeed()?.let {
        StatusMemory.feed = it
        onUpdate(it)
    }
    val now = System.currentTimeMillis()
    if (now - StatusMemory.lastRefreshMs >= StatusMemory.REFRESH_EVERY_MS && refresh()) {
        StatusMemory.lastRefreshMs = System.currentTimeMillis()
        cachedFeed()?.let {
            StatusMemory.feed = it
            onUpdate(it)
        }
    }
}

// uid -> har status ka "dekha ya nahi" (chat list ki avatar ring ke liye)
fun StatusFeed.ringMap(): Map<String, List<Boolean>> {
    return users.associate { u -> u.uid to u.items.map { it.seen } }
}

// Avatar ke chaaron taraf tukdon wali ring: har status ka ek hissa.
// seen[i] == true -> grey (dekha hua), false -> orange (naya).
// NOTE: parameter ka naam `diameter` hai (size nahi) kyunki Canvas ke andar
// DrawScope.size se takra jaata.
@Composable
fun StatusRing(
    seen: List<Boolean>,
    diameter: Dp,
    strokeWidth: Dp = 2.5.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = seen.size
            if (n > 0) {
                val sw = strokeWidth.toPx()
                val arcSize = Size(this.size.width - sw, this.size.height - sw)
                val step = 360f / n
                val gap = if (n > 1) 8f else 0f
                for (i in 0 until n) {
                    drawArc(
                        color = if (seen[i]) Color(0xFF555555) else DarkAccent,
                        startAngle = -90f + i * step + gap / 2f,
                        sweepAngle = step - gap,
                        useCenter = false,
                        topLeft = Offset(sw / 2f, sw / 2f),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt)
                    )
                }
            }
        }
        content()
    }
}
'''

STATUSSCREEN_KT = r'''package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusItem
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.data.StatusTime
import com.muwan.muwanchat.data.StatusUser
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STATUS_V2 / STATUS_V3 -- offline-first: memory se turant, phir cache, phir (zaroorat par) server se refresh.
@Composable
fun StatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StatusRepository(context) }

    var feed by remember { mutableStateOf<StatusFeed?>(StatusMemory.feed) }
    var loaded by remember { mutableStateOf(StatusMemory.feed != null) }
    var myAvatar by remember { mutableStateOf<String?>(StatusMemory.myAvatar) }
    var myName by remember { mutableStateOf("Me") }

    LaunchedEffect(Unit) {
        try {
            loadMyAvatar(context) { myAvatar = it }
        } catch (_: Exception) {
        }
        try {
            AuthDataStore.getUsername(context).first()?.let { myName = it }
        } catch (_: Exception) {
        }
    }

    // Screen jab bhi saamne aaye (viewer / new-status se lautne par bhi): purana feed turant, phir chup-chaap update
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    try {
                        repo.loadWithMemory { feed = it }
                    } finally {
                        loaded = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mine = feed?.mine ?: emptyList()
    val recent = feed?.recent ?: emptyList()
    val viewed = feed?.viewed ?: emptyList()

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }

            // Bottom padding: floating nav bar list ko dhak na le
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                item {
                    MyStatusRow(
                        name = myName,
                        avatar = myAvatar,
                        mine = mine,
                        onOpen = {
                            if (mine.isEmpty()) navController.navigate(Screen.NewStatus.route)
                            else navController.navigate(Screen.StatusViewer.createRoute("me"))
                        },
                        onAdd = { navController.navigate(Screen.NewStatus.route) }
                    )
                }

                // Kisi friend ne kuch dala hi nahi -> sections dikhte hi nahi
                if (loaded && recent.isEmpty() && viewed.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No status updates yet", color = Color(0xFF888888), fontSize = 14.sp)
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    item { StatusSectionLabel("Recent updates") }
                    items(recent, key = { "r_" + it.uid }) { u ->
                        FriendStatusRow(u) {
                            navController.navigate(Screen.StatusViewer.createRoute(u.uid))
                        }
                    }
                }

                if (viewed.isNotEmpty()) {
                    item { StatusSectionLabel("Viewed updates") }
                    items(viewed, key = { "v_" + it.uid }) { u ->
                        FriendStatusRow(u) {
                            navController.navigate(Screen.StatusViewer.createRoute(u.uid))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF888888),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun MyStatusRow(
    name: String,
    avatar: String?,
    mine: List<StatusItem>,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            if (mine.isEmpty()) {
                AvatarView(avatarBase64 = avatar, fallbackText = name, size = 56.dp, fontSize = 22.sp)
            } else {
                StatusRing(seen = mine.map { false }, diameter = 56.dp) {
                    AvatarView(avatarBase64 = avatar, fallbackText = name, size = 47.dp, fontSize = 20.sp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(DarkAccent)
                    .border(2.dp, DarkBg, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add status",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text("My status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (mine.isEmpty()) "Tap to add status"
                else "${mine.size} update${if (mine.size > 1) "s" else ""} \u00B7 ${StatusTime.label(mine.last().createdAtMs)}",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FriendStatusRow(user: StatusUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusRing(seen = user.items.map { it.seen }, diameter = 56.dp) {
            AvatarView(avatarBase64 = user.avatar, fallbackText = user.username, size = 47.dp, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(StatusTime.label(user.latestAtMs), color = Color(0xFF888888), fontSize = 13.sp)
        }
    }
}
'''

STATUSVIEWERSCREEN_KT = r'''package com.muwan.muwanchat.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.StatusItem
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.data.StatusTime
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STATUS_V3 -- full-screen viewer. uid == "me" -> apne status, warna friend ka uid.
private const val STATUS_DURATION_MS = 5000f

@Composable
fun StatusViewerScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val repo = remember { StatusRepository(context) }
    val isMine = uid == "me"

    var loaded by remember { mutableStateOf(false) }
    var statuses by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(if (isMine) "My status" else "") }
    var avatar by remember { mutableStateOf<String?>(null) }

    var progress by remember { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var replyFocused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val marked = remember { mutableSetOf<String>() }

    val close: () -> Unit = { navController.popBackStack() }
    BackHandler { close() }

    // Cache se status load. Friend ke case mein pehle na-dekhe status se shuru.
    LaunchedEffect(uid) {
        val feed = repo.cachedFeed()
        if (isMine) {
            statuses = feed?.mine ?: emptyList()
            avatar = StatusMemory.myAvatar
            // alag coroutine: avatar ke network call se viewer start na ruke
            scope.launch {
                try {
                    loadMyAvatar(context) { avatar = it }
                } catch (_: Exception) {
                }
            }
        } else {
            val u = feed?.users?.firstOrNull { it.uid == uid }
            statuses = u?.items ?: emptyList()
            name = u?.username ?: ""
            avatar = u?.avatar
            val firstUnseen = statuses.indexOfFirst { !it.seen }
            idx = if (firstUnseen < 0) 0 else firstUnseen
        }
        loaded = true
        if (statuses.isEmpty()) close()
    }

    // 5 second ka timer. Pause: ungli dabi ho / reply box focus mein ho / dialog khula ho.
    LaunchedEffect(idx, statuses.size, loaded) {
        if (!loaded || statuses.isEmpty()) return@LaunchedEffect
        progress = 0f
        while (progress < 1f) {
            delay(50)
            if (!(paused || replyFocused || confirmDelete)) {
                progress += 50f / STATUS_DURATION_MS
            }
        }
        if (idx + 1 < statuses.size) { idx += 1 } else { close() }
    }

    // Dekha mark: turant local (ring grey), server ko repository baad mein bata deti hai
    LaunchedEffect(idx, statuses.size) {
        val cur = statuses.getOrNull(idx) ?: return@LaunchedEffect
        if (!isMine && cur.id !in marked) {
            marked.add(cur.id)
            if (!cur.seen) repo.markSeen(cur.id)
        }
    }

    val current: StatusItem? = statuses.getOrNull(idx)
    if (!loaded || current == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    } else {
        val sendReply: () -> Unit = {
            val text = replyText.trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "Write a reply first", Toast.LENGTH_SHORT).show()
            } else {
                val preview = current.text.take(40) + if (current.text.length > 40) "..." else ""
                val content = if (preview.isNotBlank()) "Status: \"$preview\"\n$text" else text
                val msgId = java.util.UUID.randomUUID().toString()
                replyText = ""
                focusManager.clearFocus()
                val onResult: (Boolean) -> Unit = { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Reply sent" else "Couldn't send reply. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(msgId, uid, content) { ok ->
                        scope.launch { onResult(ok) }
                    }
                } else {
                    scope.launch {
                        var ok = false
                        try {
                            val token = AuthDataStore.getToken(context).first()
                            if (token != null) {
                                val res = RetrofitClient.chatApi.sendMessage(
                                    "Bearer $token",
                                    SendMessageRequest(uid, content)
                                )
                                ok = res.isSuccessful
                            }
                        } catch (_: Exception) {
                        }
                        onResult(ok)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseStatusColor(current.bgColor))
        ) {
            // Beech ka hissa: tap (left = pichla, right = agla), daba ke rakho = pause, neeche swipe = band
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(statuses.size) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.35f) {
                                    if (idx > 0) { idx -= 1 } else { progress = 0f }
                                } else {
                                    if (idx + 1 < statuses.size) { idx += 1 } else { close() }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 150f) close() },
                            onDragCancel = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                StatusContent(current)
            }

            // Upar: progress bars + header
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    statuses.forEachIndexed { i, _ ->
                        val frac = when {
                            i < idx -> 1f
                            i == idx -> progress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(frac)
                                    .background(Color.White)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarBase64 = avatar, fallbackText = name, size = 38.dp, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            StatusTime.label(current.createdAtMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    if (isMine) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete status", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { close() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Neeche: friend ke status par reply bar, apne status par "Viewed by N"
            if (isMine) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Viewed by ${current.viewCount}", color = Color.White, fontSize = 14.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendReply() }),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { replyFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (replyText.isEmpty()) {
                                    Text(
                                        "Reply to status",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 15.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    IconButton(onClick = { sendReply() }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send reply", tint = Color.White)
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = DarkSheet,
                title = { Text("Delete status?", color = Color.White) },
                text = { Text("This status will be removed for everyone.", color = Color(0xFFAAAAAA)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        val target = current
                        scope.launch {
                            if (repo.delete(target.id)) {
                                statuses = statuses.filter { it.id != target.id }
                                if (idx >= statuses.size) idx = (statuses.size - 1).coerceAtLeast(0)
                                if (statuses.isEmpty()) close()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Couldn't delete status. Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) { Text("Delete", color = Color(0xFFFF5252)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun StatusContent(s: StatusItem) {
    val body = when (s.type) {
        "link" -> listOf(s.text, s.linkUrl ?: "").filter { it.isNotBlank() }.joinToString("\n\n")
        else -> s.text
    }
    val fontSize = when {
        s.type != "text" -> 18.sp
        body.length > 220 -> 18.sp
        body.length > 100 -> 22.sp
        else -> 28.sp
    }
    Text(
        text = body.ifBlank { "This status type isn't supported in your app version yet." },
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}
'''


def main():
    if not os.path.isdir(SCREENS):
        print("ERROR: repo root se chalao (jahan app/ folder hai).")
        sys.exit(1)
    print("== screens ==")
    put_file("StatusCommon.kt", STATUSCOMMON_KT)
    put_file("StatusScreen.kt", STATUSSCREEN_KT)
    put_file("StatusViewerScreen.kt", STATUSVIEWERSCREEN_KT)
    print("")
    if errors:
        print("ERRORS:")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    if DRY:
        print("DRY RUN -- kuch likha nahi gaya. Sab theek. Ab bina --dry-run ke chalao.")
    else:
        print("Ho gaya. Ab: git diff -> commit -> push.")


main()
