# -*- coding: utf-8 -*-
# CallHistoryScreen.kt already bana hua tha lekin poori tarah orphan tha --
# na koi Screen route, na NavHost me composable() entry, na BottomNavBar me
# tab. Ye patch teeno jodta hai. BottomNavBar ke fixed-width tabs (92.dp)
# ko weight-based bana diya hai taaki 4 tabs kisi bhi screen width pe
# overflow/overlap na karein -- baaki sab (padding, icon size, font,
# shadow, pill shape) bilkul waisa hi hai.

NAVGRAPH = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
BOTTOMNAV = "app/src/main/java/com/muwan/muwanchat/screens/BottomNavBar.kt"

# ───────────────────────── NavGraph.kt ─────────────────────────
with open(NAVGRAPH, "r", encoding="utf-8") as f:
    ng = f.read()

old_screen = (
    '    object ConversationList: Screen("conversations")\n'
    '    object BroadcastChannels: Screen("broadcast_channels")\n'
    '    object Status           : Screen("status")\n'
)
new_screen = (
    '    object ConversationList: Screen("conversations")\n'
    '    object BroadcastChannels: Screen("broadcast_channels")\n'
    '    object Status           : Screen("status")\n'
    '    object CallHistory      : Screen("call_history")\n'
)
if old_screen not in ng:
    raise SystemExit("[FAIL] Screen sealed class block not found -- patch by hand")
ng = ng.replace(old_screen, new_screen, 1)

old_composable = '''        composable(Screen.Status.route) {
            MainTabScaffold(navController, Screen.Status.route) {
                StatusScreen(navController)
            }
        }
'''
new_composable = old_composable + '''        composable(Screen.CallHistory.route) {
            MainTabScaffold(navController, Screen.CallHistory.route) {
                CallHistoryScreen(navController)
            }
        }
'''
if old_composable not in ng:
    raise SystemExit("[FAIL] Status composable block not found -- patch by hand")
ng = ng.replace(old_composable, new_composable, 1)

with open(NAVGRAPH, "w", encoding="utf-8") as f:
    f.write(ng)

# ───────────────────────── BottomNavBar.kt ─────────────────────────
with open(BOTTOMNAV, "r", encoding="utf-8") as f:
    bn = f.read()

old_import = "import androidx.compose.material.icons.filled.Campaign\n"
new_import = "import androidx.compose.material.icons.filled.Call\nimport androidx.compose.material.icons.filled.Campaign\n"
if old_import not in bn:
    raise SystemExit("[FAIL] Campaign import not found -- patch by hand")
bn = bn.replace(old_import, new_import, 1)

old_tabs = '''private val navTabs = listOf(
    NavTab(Screen.ConversationList.route, "Chats", Icons.Filled.Chat),
    NavTab(Screen.BroadcastChannels.route, "Broadcast", Icons.Filled.Campaign),
    NavTab(Screen.Status.route, "Status", Icons.Filled.DonutLarge)
)'''
new_tabs = '''private val navTabs = listOf(
    NavTab(Screen.ConversationList.route, "Chats", Icons.Filled.Chat),
    NavTab(Screen.BroadcastChannels.route, "Broadcast", Icons.Filled.Campaign),
    NavTab(Screen.Status.route, "Status", Icons.Filled.DonutLarge),
    NavTab(Screen.CallHistory.route, "Calls", Icons.Filled.Call)
)'''
if old_tabs not in bn:
    raise SystemExit("[FAIL] navTabs list not found -- patch by hand")
bn = bn.replace(old_tabs, new_tabs, 1)

# Fixed-width (92.dp) tabs -> weight-based, so 4 tabs always fit any screen
# width without overflow/overlap. Row needs fillMaxWidth() for weight() to
# take effect; outer margins (24.dp horizontal, set in NavGraph.kt) stay
# untouched so the pill's position/look is unchanged.
old_row = '''    Row(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(DarkSheet)
            .padding(vertical = 9.dp)
    ) {'''
new_row = '''    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(DarkSheet)
            .padding(vertical = 9.dp)
    ) {'''
if old_row not in bn:
    raise SystemExit("[FAIL] Row modifier block not found -- patch by hand")
bn = bn.replace(old_row, new_row, 1)

old_column = '''            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(92.dp)
                    .clickable(enabled = !selected) { onNavigate(tab.route) }
                    .padding(vertical = 4.dp)
            ) {'''
new_column = '''            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !selected) { onNavigate(tab.route) }
                    .padding(vertical = 4.dp)
            ) {'''
if old_column not in bn:
    raise SystemExit("[FAIL] Column modifier block not found -- patch by hand")
bn = bn.replace(old_column, new_column, 1)

with open(BOTTOMNAV, "w", encoding="utf-8") as f:
    f.write(bn)

print("[ok] CallHistory route + NavHost entry + BottomNavBar tab added")
print("[ok] BottomNavBar tabs switched to weight-based sizing (no overflow with 4 tabs)")
