import io, sys

BASE = "app/src/main/java/com/muwan/muwanchat/"

def read(p):
    with io.open(p, "r", encoding="utf-8", newline="") as f:
        return f.read().replace("\r\n", "\n")

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

def replace_once(s, old, new, label):
    if s.count(old) != 1:
        print("ERROR [%s]: anchor %d baar mila (1 chahiye)" % (label, s.count(old)))
        sys.exit(1)
    return s.replace(old, new)

# ---------------- CallManager.kt ----------------
p = BASE + "calling/CallManager.kt"
s = read(p)

if "lastConnectionState" in s:
    print("SKIP CallManager.kt: pehle se patch ho chuka hai")
else:
    s = replace_once(
        s,
        "    private val stateLock = Any()\n",
        "    private val stateLock = Any()\n"
        "    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())\n"
        "    @Volatile private var connState: PeerConnection.PeerConnectionState? = null\n"
        "\n"
        "    companion object {\n"
        "        // Sirf diagnostics ke liye: aakhri connection state ka naam (CallScreen toast mein dikhata hai)\n"
        "        @Volatile var lastConnectionState: String = \"\"\n"
        "    }\n",
        "fields")

    old = (
        "                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {\n"
        "                        if (newState == PeerConnection.PeerConnectionState.FAILED ||\n"
        "                            newState == PeerConnection.PeerConnectionState.DISCONNECTED\n"
        "                        ) {\n"
        "                            onConnectionFailed()\n"
        "                        }\n"
        "                    }\n"
    )
    new = (
        "                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {\n"
        "                        connState = newState\n"
        "                        lastConnectionState = newState.name\n"
        "                        android.util.Log.d(\"CallManager\", \"connection state: $newState\")\n"
        "                        when (newState) {\n"
        "                            PeerConnection.PeerConnectionState.FAILED ->\n"
        "                                mainHandler.post { onConnectionFailed() }\n"
        "                            // DISCONNECTED aksar temporary hota hai (network blip) --\n"
        "                            // 8 second wait karo, tab bhi na sudhre to hi call kaato\n"
        "                            PeerConnection.PeerConnectionState.DISCONNECTED ->\n"
        "                                mainHandler.postDelayed({\n"
        "                                    if (connState == PeerConnection.PeerConnectionState.DISCONNECTED) {\n"
        "                                        onConnectionFailed()\n"
        "                                    }\n"
        "                                }, 8000)\n"
        "                            else -> {}\n"
        "                        }\n"
        "                    }\n"
    )
    s = replace_once(s, old, new, "onConnectionChange")
    write(p, s)
    print("OK CallManager.kt")

# ---------------- CallScreen.kt ----------------
p = BASE + "screens/CallScreen.kt"
s = read(p)

if "Call connection failed" in s:
    print("SKIP CallScreen.kt: pehle se patch ho chuka hai")
else:
    old = (
        "            onConnectionFailed = {\n"
        "                if (callState != CallState.ENDED) {\n"
        "                    callState = CallState.ENDED\n"
        "                    navController.popBackStack()\n"
        "                }\n"
        "            }\n"
    )
    new = (
        "            onConnectionFailed = {\n"
        "                if (callState != CallState.ENDED) {\n"
        "                    callState = CallState.ENDED\n"
        "                    Toast.makeText(\n"
        "                        context,\n"
        "                        \"Call connection failed (${CallManager.lastConnectionState})\",\n"
        "                        Toast.LENGTH_LONG\n"
        "                    ).show()\n"
        "                    // Doosre side ko bhi batao, warna wo ringing/ongoing pe atka rehta hai\n"
        "                    AppSocketManager.sendCallEnd(callId)\n"
        "                    navController.popBackStack()\n"
        "                }\n"
        "            }\n"
    )
    s = replace_once(s, old, new, "CallScreen onConnectionFailed")
    if "import com.muwan.muwanchat.calling.CallManager\n" not in s:
        s = replace_once(s, "import android.widget.Toast\n",
                         "import android.widget.Toast\nimport com.muwan.muwanchat.calling.CallManager\n", "import")
    write(p, s)
    print("OK CallScreen.kt")
