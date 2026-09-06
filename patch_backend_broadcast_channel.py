# -*- coding: utf-8 -*-
# Broadcast Channel backend foundation.
#
# Design: channel = group hi hai (isChannel:true, onlyAdminsCanSend:true),
# bas creation flow alag hai (no required members, no friendship check —
# channel banate waqt sirf owner member hota hai; log baad me invite link
# se join karte hain). Message send/receive, media, seen-status, invite-link
# join flow — sab already working group infra se free mein milta hai:
#   - socket/chat.js already onlyAdminsCanSend enforce karta hai
#   - routes/groups.js ka /join/:code route already invite-code based hai,
#     koi areFriends() check nahi karta -- isliye "koi bhi link se join kare"
#     wahi flow channel ke liye bhi seedha kaam karega
#
# Is patch mein sirf do cheezein:
#   1. getGroup() ke default-merge me isChannel:false add (purane groups
#      self-heal ho jaayenge, jaise baaki fields already karte hain)
#   2. Naya route POST /groups/create-channel -- name/avatar/description
#      leta hai, memberUids nahi maangta, owner ko hi member/admin bana ke
#      isChannel:true + onlyAdminsCanSend:true set karta hai

path = "routes/groups.js"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_default = '''  return {
    inviteCode: null,
    joinApprovalRequired: false,
    membersCanAdd: false,
    onlyAdminsCanSend: false,
    readReceiptsEnabled: true,
    pendingRequests: [],
    ...group
  };
}'''

new_default = '''  return {
    inviteCode: null,
    joinApprovalRequired: false,
    membersCanAdd: false,
    onlyAdminsCanSend: false,
    readReceiptsEnabled: true,
    pendingRequests: [],
    isChannel: false,
    ...group
  };
}'''

n = src.count(old_default)
if n != 1:
    raise SystemExit(f"[FAIL] getGroup default merge: found {n} matches (expected 1)")
src = src.replace(old_default, new_default, 1)

old_anchor = '''// ---- Invite link: preview + join (approval-aware) ----
// NOTE: ye do routes "/:roomId" wale generic route se PEHLE define hain,
// warna Express "/join" ko roomId maan ke wahi route match kar leta.'''

new_block = '''// Broadcast channel create: sirf owner member/admin banta hai, koi
// memberUids/friendship zaroorat nahi -- log baad me invite link se
// khud join karte hain. onlyAdminsCanSend hamesha true, kyunki channel
// ka poora point hi one-way broadcast hai.
router.post('/create-channel', authMiddleware, async (req, res) => {
  try {
    const { name, avatar, description } = req.body;
    if (!name || !name.trim())
      return res.status(400).json({ error: 'name required' });

    const owner = req.user.uid;
    const id = `group_${uuidv4()}`;
    const createdAt = new Date().toISOString();
    const inviteCode = generateInviteCode();

    const group = {
      id,
      name: name.trim(),
      avatar: avatar || null,
      description: (description || '').trim(),
      owner,
      admins: [owner],
      members: [owner],
      createdAt,
      inviteCode,
      joinApprovalRequired: false,
      membersCanAdd: false,
      onlyAdminsCanSend: true,
      readReceiptsEnabled: true,
      pendingRequests: [],
      isChannel: true
    };
    await saveGroup(group);
    await set(inviteKey(inviteCode), id);
    await set(`convmeta:${id}`, { room_id: id, lastMessage: null, unread: {} });

    return res.json({ success: true, group });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

// ---- Invite link: preview + join (approval-aware) ----
// NOTE: ye do routes "/:roomId" wale generic route se PEHLE define hain,
// warna Express "/join" ko roomId maan ke wahi route match kar leta.'''

n2 = src.count(old_anchor)
if n2 != 1:
    raise SystemExit(f"[FAIL] create-channel route insertion: found {n2} matches (expected 1)")
src = src.replace(old_anchor, new_block, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] isChannel default added + POST /groups/create-channel route added")
