#!/usr/bin/env python3
"""
STATUS STEP 3b (app) -- update on top of step 3
  * NewStatusScreen : "Post status" button ab ek line mein (icon + "Post"), posting ke dauran spinner,
                      double-tap se dobara post nahi hota, 600+ characters par counter
  * Status tab      : offline-first -- purana feed memory se TURANT dikhta hai (blank/loading nahi),
                      phir Room cache, aur network sirf tab jab pichla refresh 60 second se purana ho
  * Chat list ring  : wahi memory cache, tab-switch par ring ghaayab/blink nahi hoti

Step 3 pehle chal chuka hona chahiye. MuwanChat--main repo root se:
    python3 patch_app_status_step3b.py --dry-run
    python3 patch_app_status_step3b.py
"""
import os
import sys

DRY = "--dry-run" in sys.argv
BASE = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat")
SCREENS = os.path.join(BASE, "screens")
MARK = "STATUS_V2"
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
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusRepository

// STATUS_V2 -- Status list, viewer, new-status aur chat list ke beech shared helpers.

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
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusItem
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.data.StatusTime
import com.muwan.muwanchat.data.StatusUser
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STATUS_V2 -- offline-first: memory se turant, phir cache, phir (zaroorat par) server se refresh.
@Composable
fun StatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StatusRepository(context) }

    var feed by remember { mutableStateOf<StatusFeed?>(StatusMemory.feed) }
    var loaded by remember { mutableStateOf(StatusMemory.feed != null) }
    var myAvatar by remember { mutableStateOf<String?>(null) }
    var myName by remember { mutableStateOf("Me") }

    LaunchedEffect(Unit) {
        try {
            val db = MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context))
            myAvatar = db.myProfileDao().get()?.avatar
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

NEWSTATUSSCREEN_KT = r'''package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.network.CreateStatusBody
import kotlinx.coroutines.launch

// STATUS_V2 -- naya text status (colour background ke saath).
@Composable
fun NewStatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StatusRepository(context) }
    val focusRequester = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var colorIdx by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    val post: () -> Unit = {
        val t = text.trim()
        if (t.isEmpty()) {
            error = "Write something first"
        } else if (!posting) {
            posting = true
            error = ""
            scope.launch {
                val result = repo.create(
                    CreateStatusBody(type = "text", text = t, bg_color = StatusBgColors[colorIdx])
                )
                posting = false
                if (result.isSuccess) {
                    navController.popBackStack()
                } else {
                    error = result.exceptionOrNull()?.message ?: "Couldn't post status"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(parseStatusColor(StatusBgColors[colorIdx]))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                IconButton(onClick = { colorIdx = (colorIdx + 1) % StatusBgColors.size }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Change colour", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= STATUS_MAX_TEXT) {
                            text = it
                            error = ""
                        }
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) {
                                Text(
                                    "Type a status",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            if (text.length >= STATUS_MAX_TEXT - 100) {
                Text(
                    "${text.length}/$STATUS_MAX_TEXT",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, bottom = 2.dp)
                )
            }

            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBgColors.forEachIndexed { i, hex ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(parseStatusColor(hex))
                                .border(2.dp, if (i == colorIdx) Color.White else Color.Transparent, CircleShape)
                                .clickable { colorIdx = i }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DarkAccent.copy(alpha = if (text.isBlank() || posting) 0.6f else 1f))
                        .clickable(enabled = !posting) { post() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (posting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (posting) "Posting" else "Post",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
'''


def main():
    if not os.path.isdir(SCREENS):
        print("ERROR: repo root se chalao (jahan app/ folder hai).")
        sys.exit(1)

    print("== screens ==")
    put_file("StatusCommon.kt", STATUSCOMMON_KT)
    put_file("StatusScreen.kt", STATUSSCREEN_KT)
    put_file("NewStatusScreen.kt", NEWSTATUSSCREEN_KT)

    conv = os.path.join(SCREENS, "ConversationListScreen.kt")
    print("== ConversationListScreen.kt ==")
    if not os.path.exists(conv):
        errors.append("ConversationListScreen.kt nahi mili")
    else:
        edit(conv, "chat list ring: memory se turant + throttled refresh",
             '    var statusRings by remember { mutableStateOf<Map<String, List<Boolean>>>(emptyMap()) }\n'
             '    LaunchedEffect(Unit) {\n'
             '        try {\n'
             '            statusRepo.cachedFeed()?.let { statusRings = it.ringMap() }\n'
             '            if (statusRepo.refresh()) statusRepo.cachedFeed()?.let { statusRings = it.ringMap() }\n'
             '        } catch (_: Exception) {\n'
             '        }\n'
             '    }\n',
             '    var statusRings by remember { mutableStateOf(StatusMemory.feed?.ringMap() ?: emptyMap<String, List<Boolean>>()) }  // ' + MARK + '\n'
             '    LaunchedEffect(Unit) {\n'
             '        try {\n'
             '            statusRepo.loadWithMemory { statusRings = it.ringMap() }\n'
             '        } catch (_: Exception) {\n'
             '        }\n'
             '    }\n')

    print("")
    if errors:
        print("ERRORS:")
        for e in errors:
            print("  - " + e)
        print("Jahan FAIL hai wahan kuch likha nahi gaya. Upar ki lines mujhe bhejo.")
        sys.exit(1)
    if DRY:
        print("DRY RUN -- kuch likha nahi gaya. Sab theek. Ab bina --dry-run ke chalao.")
    else:
        print("Ho gaya. Ab: git diff -> commit -> push.")


main()
