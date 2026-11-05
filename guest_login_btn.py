#!/usr/bin/env python3
# GUEST_LOGIN_BTN -- LoginScreen: purple "Continue Existing Guest" button + Coming Soon popup
import io, sys, os
P = "app/src/main/java/com/muwan/muwanchat/screens/LoginScreen.kt"
MARK = "GUEST_LOGIN_BTN"
DRY = "--dry-run" in sys.argv

with io.open(P, "r", encoding="utf-8", newline="") as f:
    s = f.read()
if MARK in s:
    print("already applied"); sys.exit(0)

A1 = '    var errorMsg by remember { mutableStateOf("") }\n'
A2 = '            } else {\n                PhoneLoginSection(navController = navController)\n            }\n'
if s.count(A2) != 1 or A1 not in s:
    print("anchor nahi mila, kuch nahi likha"); sys.exit(1)

STATE = A1 + '    var showGuestExistingSoon by remember { mutableStateOf(false) }  // GUEST_LOGIN_BTN\n'
BTN = A2 + '''
            // GUEST_LOGIN_BTN -- purple Continue Existing Guest (abhi sirf Coming Soon popup)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showGuestExistingSoon = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E44FF)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Text("Continue Existing Guest", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (showGuestExistingSoon) {
                ComingSoonDialog(
                    feature = "👤 Continue Existing Guest",
                    onDismiss = { showGuestExistingSoon = False }
                )
            }
'''.replace("False", "false")

s = s.replace(A1, STATE, 1).replace(A2, BTN, 1)
if DRY:
    print("dry-run OK, anchors mil gaye"); sys.exit(0)
with io.open(P, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("applied:", P)
