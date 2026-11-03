package com.muwan.muwanchat.screens

// GUEST_P2A
// Guest ke liye abhi backend se na jude screens: guest ho to Coming Soon popup (dismiss par wapas),
// warna asli screen. Auth users par koi asar nahi.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.data.GuestStore

@Composable
fun GuestGate(navController: NavController, feature: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isGuest = remember { GuestStore.isGuestActive(context) }
    if (isGuest) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            ComingSoonDialog(feature = feature, onDismiss = { navController.popBackStack() })
        }
    } else {
        content()
    }
}
