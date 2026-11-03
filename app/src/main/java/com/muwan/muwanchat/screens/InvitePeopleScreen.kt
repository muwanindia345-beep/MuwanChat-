package com.muwan.muwanchat.screens

// GUEST_P2B1
// Guest "Invite People": QR code + link + Copy + Share. Header Settings/GuestInfo jaisa (consistency).
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet
import com.google.android.gms.codescanner.GmsBarcodeScannerOptions  // GUEST_P2B2
import com.google.android.gms.codescanner.GmsBarcodeScanning  // GUEST_P2B2
import com.google.mlkit.vision.barcode.common.Barcode  // GUEST_P2B2
import com.muwan.muwanchat.data.GuestInvite
import com.muwan.muwanchat.data.GuestInviteInbox  // GUEST_P2B2
import kotlinx.coroutines.launch

private fun makeQr(text: String, size: Int): Bitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val w = m.width
    val h = m.height
    val px = IntArray(w * h)
    for (y in 0 until h) {
        val off = y * w
        for (x in 0 until w) {
            px[off + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}

@Composable
fun InvitePeopleScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanSoon by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            when (val r = GuestInvite.ensure(context)) {
                is GuestInvite.Result.Ok -> {
                    link = r.info.link
                    expiresAt = r.info.expiresAtMs
                }
                is GuestInvite.Result.Fail -> {
                    error = r.reason
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (scanSoon) {
        ComingSoonDialog(feature = "📷 Scan QR Code", onDismiss = { scanSoon = false })
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
            Text("Invite People", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentLink = link

            if (loading) {
                Spacer(Modifier.height(80.dp))
                CircularProgressIndicator(color = DarkAccent)
            } else if (currentLink != null) {
                Text(
                    "Ask your friend to scan this QR code or open your link to connect with you.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                val qr = remember(currentLink) { makeQr(currentLink, 640) }
                if (qr != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp)
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Invite QR code",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text("Couldn't draw the QR code. Use the link below.", color = Color(0xFFFF5555), fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSheet)
                        .padding(14.dp)
                ) {
                    Text(
                        currentLink,
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Invite link", currentLink))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSheet),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Copy link", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Chat with me on TalkWave (guest mode): " + currentLink)
                            }
                            val chooser = Intent.createChooser(send, "Share invite")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Share", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                val days = ((expiresAt - System.currentTimeMillis() + 86_399_999L) / 86_400_000L).coerceAtLeast(1L)
                Text(
                    "This invite works for about " + days + " day(s) and up to 10 people.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Anyone who has your QR code or link can send you a connection request, so share it carefully.",
                    color = Color(0xFFFF5555),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {  // GUEST_P2B2: asli QR scan
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        GmsBarcodeScanning.getClient(context, options).startScan()
                            .addOnSuccessListener { barcode ->
                                val value = barcode.rawValue
                                if (value != null) {
                                    GuestInviteInbox.offer(value)
                                    navController.popBackStack()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Couldn't open the scanner. Check your internet and try again.", Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Scan a QR code", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(60.dp))
                Text(
                    "Couldn't create your invite. Check your internet and try again.",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", color = Color(0xFF888888), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { load() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
