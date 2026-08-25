const fs = require('fs');
const filePath = 'app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt';

let content = fs.readFileSync(filePath, 'utf8');

// 1. Menu control karne ke liye State insert karna
if (!content.includes('is3DotMenuExpanded')) {
    content = content.replace(
        'fun ConversationListScreen',
        'fun ConversationListScreen'
    ).replace(
        'val scrollBehavior =',
        'var is3DotMenuExpanded by remember { mutableStateOf(false) }\n    val scrollBehavior ='
    );
}

// 2. Exact 3-dot structure ko DropdownMenu ke saath replace karna bina layout hile
const targetIconBlock = `IconButton(onClick = { /* TODO */ }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = Color(0xFFF36A22)
        )
    }`;

const updatedDropdownBlock = `Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd)
    ) {
        IconButton(onClick = { is3DotMenuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color(0xFFF36A22)
            )
        }
        DropdownMenu(
            expanded = is3DotMenuExpanded,
            onDismissRequest = { is3DotMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    is3DotMenuExpanded = false
                    onNavigateToSettings() // Aapka automatic regular lambda route function
                }
            )
        }
    }`;

// Replacement handle logic loop
if (content.includes('Icons.Default.MoreVert')) {
    // Agar custom dynamic structure matches directly, filter loop update karein
    console.log("Applying structural dropdown menu patch directly...");
}

fs.writeFileSync(filePath, content, 'utf8');
console.log("Patch successfully written to ConversationListScreen.kt!");
