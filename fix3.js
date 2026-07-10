const fs = require('fs');
const path = 'app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt';
let c = fs.readFileSync(path, 'utf8');

const oldDelete = 'RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", id)';
const newDelete = 'RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)';
if (!c.includes(oldDelete)) { console.log('deleteMsgById pattern not found!'); process.exit(1); }
c = c.replace(oldDelete, newDelete);

const oldEdit = 'RetrofitClient.chatApi.editMessage("Bearer $myToken", msg.id, EditMessageRequest(newText))';
const newEdit = 'RetrofitClient.chatApi.editMessage("Bearer $myToken", roomId, msg.id, EditMessageRequest(newText))';
if (!c.includes(oldEdit)) { console.log('editMessage pattern not found!'); process.exit(1); }
c = c.replace(oldEdit, newEdit);

fs.writeFileSync(path, c);
console.log('Done');
