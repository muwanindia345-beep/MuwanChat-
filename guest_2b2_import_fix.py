import sys, os
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
p = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens/InvitePeopleScreen.kt")
s = open(p, encoding="utf-8").read()
if "com.google.mlkit.vision.codescanner.GmsBarcodeScanning" in s:
    print("Already fixed."); sys.exit(0)
old1 = "import com.google.android.gms.codescanner.GmsBarcodeScannerOptions"
old2 = "import com.google.android.gms.codescanner.GmsBarcodeScanning"
if s.count(old1) != 1 or s.count(old2) != 1:
    print("FAIL: import lines nahi mili. Kuch nahi likha."); sys.exit(1)
s = s.replace(old1, "import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions")
s = s.replace(old2, "import com.google.mlkit.vision.codescanner.GmsBarcodeScanning")
open(p, "w", encoding="utf-8").write(s)
print("OK imports fixed")
