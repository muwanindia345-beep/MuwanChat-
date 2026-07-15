#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/src/main/java/com/muwan/muwanchat/screens/SplashScreen.kt")
IMG = pathlib.Path("app/src/main/res/drawable-nodpi/splash_bg.jpg")

MARKER = "R.drawable.splash_bg"

NEW_CONTENT = '''package com.muwan.muwanchat.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.muwan.muwanchat.R
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(2000)
        val loggedIn = AuthDataStore.isLoggedIn(context).first()
        if (loggedIn) {
            navController.navigate(Screen.ConversationList.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    Image(
        painter = painterResource(id = R.drawable.splash_bg),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
    )
}
'''

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from repo root.")
        sys.exit(1)

    existing = TARGET.read_text(encoding="utf-8")
    if MARKER in existing:
        print("Already patched, nothing to do.")
    else:
        TARGET.write_text(NEW_CONTENT, encoding="utf-8")
        print(f"Patched: {TARGET}")

    if not IMG.exists():
        print(f"\nWARNING: {IMG} not found yet.")
        print("Move the image there first, e.g.:")
        print("  mkdir -p app/src/main/res/drawable-nodpi")
        print("  mv ~/storage/downloads/splash_bg.jpg app/src/main/res/drawable-nodpi/splash_bg.jpg")
    else:
        print(f"Found image at: {IMG}")

if __name__ == "__main__":
    main()
