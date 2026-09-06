package com.muwan.muwanchat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Media uploads (photo/video/document/music/voice) ke liye app-lifetime scope.
// rememberCoroutineScope() (Compose) ke bajaye isko use karo taaki user chat
// screen se navigate away ho jaaye (ya app background chala jaaye) to bhi
// upload background mein chalta rahe aur poora complete ho — status update
// (SENT/FAILED) bhi ho jaaye. Process ke jeete rehte tak yeh zinda rehta hai.
object UploadScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
