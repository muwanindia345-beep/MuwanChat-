const fs = require('fs');
const path = 'app/src/main/java/com/muwan/muwanchat/screens/RegisterScreen.kt';
let c = fs.readFileSync(path, 'utf8');
const oldCode = `if (res.isSuccessful && res.body()?.success == true) {\n                                navController.navigate(Screen.ConversationList.route) {\n                                    popUpTo(Screen.Register.route) { inclusive = true }\n                                }`;
const newCode = `if (res.isSuccessful && res.body()?.success == true) {\n                                val body = res.body()!!\n                                AuthDataStore.saveAuth(\n                                    context,\n                                    username  = body.user?.username ?: username.trim(),\n                                    email     = email.trim(),\n                                    token     = body.token ?: "",\n                                    anonKey   = "",\n                                    secretKey = "",\n                                    dbName    = "",\n                                    loginType = "email"\n                                )\n                                navController.navigate(Screen.ConversationList.route) {\n                                    popUpTo(Screen.Register.route) { inclusive = true }\n                                }`;
if (!c.includes('navController.navigate(Screen.ConversationList.route)')) { console.log('Pattern not found!'); process.exit(1); }
c = c.replace(oldCode, newCode);
fs.writeFileSync(path, c);
console.log('Done');
