#!/data/data/com.termux/files/usr/bin/bash
set -e

LOGIN="app/src/main/java/com/muwan/muwanchat/screens/LoginScreen.kt"
REGISTER="app/src/main/java/com/muwan/muwanchat/screens/RegisterScreen.kt"

if [ ! -f "$LOGIN" ] || [ ! -f "$REGISTER" ]; then
  echo "ERROR: run this from project root (where app/ folder is)"
  exit 1
fi

sed -i 's/Text("M", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = DarkAccent)/Text("T", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = DarkAccent)/' "$LOGIN"
sed -i 's/Text("MuwanChat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)/Text("TalkWave", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)/' "$LOGIN"

sed -i 's/Text("M", color = DarkAccent, fontSize = 60.sp, fontWeight = FontWeight.Bold)/Text("T", color = DarkAccent, fontSize = 60.sp, fontWeight = FontWeight.Bold)/' "$REGISTER"
sed -i 's/Text("MuwanChat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)/Text("TalkWave", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)/' "$REGISTER"

echo "Done: M -> T and MuwanChat -> TalkWave updated in Login & Register screens."
