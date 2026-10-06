#!/usr/bin/env python3
"""
STATUS STEP 3d (app) -- New status screen ab WhatsApp jaisi
  Bug: colour poori window par phailta tha, isliye neeche ki navigation bar (3 buttons) bhi
       colour mein rang jaati thi aur keyboard ke saath transparent si dikhti thi.
  Fix: colour sirf content wale hisse mein; neeche navigation bar solid kali.
       Colour dots + Post button ek thodi gehri shade wali bottom bar mein
       (WhatsApp ke Photo/Text/Voice wali bar ki jagah -- aage yahin media tabs aayenge).

Step 3 pehle chal chuka hona chahiye (step 3b/3c ke saath kisi bhi order mein theek).
MuwanChat--main repo root se:
    python3 patch_app_status_step3d.py --dry-run
    python3 patch_app_status_step3d.py
"""
import os
import sys

DRY = "--dry-run" in sys.argv
BASE = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat")
SCREENS = os.path.join(BASE, "screens")
MARK = "STATUS_V4"
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

// STATUS_V2 / STATUS_V4 -- naya text status (colour background ke saath).
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

    val bg = parseStatusColor(StatusBgColors[colorIdx])
    val barBg = Color(bg.red * 0.55f, bg.green * 0.55f, bg.blue * 0.55f, 1f)

    // WhatsApp jaisa: colour sirf content mein, neeche ki navigation bar solid kali rehti hai.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(bg)
                .statusBarsPadding()
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

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBg)
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
'''


def main():
    if not os.path.isdir(SCREENS):
        print("ERROR: repo root se chalao (jahan app/ folder hai).")
        sys.exit(1)
    print("== screens ==")
    put_file("NewStatusScreen.kt", NEWSTATUSSCREEN_KT)
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
