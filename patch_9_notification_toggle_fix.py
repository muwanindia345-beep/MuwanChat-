#!/usr/bin/env python3
import pathlib, sys

TARGET = pathlib.Path("app/src/main/java/com/muwan/muwanchat/MuwanFirebaseService.kt")

OLD = """    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "MuwanChat"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        showNotification(title, body)
    }"""

NEW = """    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "MuwanChat"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"

        CoroutineScope(Dispatchers.IO).launch {
            val notificationsEnabled = try {
                AuthDataStore.getNotificationsEnabled(applicationContext).first()
            } catch (_: Exception) {
                true // read fail ho jaye to fail-open (notification na khoye)
            }
            if (!notificationsEnabled) return@launch

            showNotification(title, body)
        }
    }"""

def main():
    if not TARGET.exists():
        print(f"ERROR: {TARGET} not found. Run from MuwanChat repo root.")
        sys.exit(1)
    text = TARGET.read_text(encoding="utf-8")
    if "notificationsEnabled" in text:
        print("Already patched, nothing to do.")
        return
    if OLD not in text:
        print("ERROR: expected onMessageReceived block not found — file may have changed.")
        sys.exit(1)
    text = text.replace(OLD, NEW)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched: {TARGET}")

if __name__ == "__main__":
    main()
