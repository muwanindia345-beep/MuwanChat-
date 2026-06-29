const fs = require('fs');
const path = 'app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt';
let c = fs.readFileSync(path, 'utf8');
const add = 'private const val BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app"\n\n';
c = c.replace('private fun formatConvTime', add + 'private fun formatConvTime');
fs.writeFileSync(path, c);
console.log('Done');
