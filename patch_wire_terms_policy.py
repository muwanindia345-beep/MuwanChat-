def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

NAVGRAPH = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
SPLASH = "app/src/main/java/com/muwan/muwanchat/screens/SplashScreen.kt"
REGISTER = "app/src/main/java/com/muwan/muwanchat/screens/RegisterScreen.kt"
SETTINGS = "app/src/main/java/com/muwan/muwanchat/screens/SettingsScreen.kt"

# ---------------------------------------------------------------------------
# 1) NavGraph.kt: add 3 new routes
# ---------------------------------------------------------------------------
apply(
    NAVGRAPH,
    '''    object Login           : Screen("login")
    object Register        : Screen("register")''',
    '''    object Login           : Screen("login")
    object Register        : Screen("register")
    object TermsPolicy     : Screen("terms_policy")
    object TermsPrivacy    : Screen("terms_privacy")
    object ApplicationRules : Screen("application_rules")''',
    "NavGraph: add TermsPolicy/TermsPrivacy/ApplicationRules routes",
)

# ---------------------------------------------------------------------------
# 2) NavGraph.kt: register the 3 new composables
# ---------------------------------------------------------------------------
apply(
    NAVGRAPH,
    '''        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }''',
    '''        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.TermsPolicy.route) { TermsPolicyScreen(navController) }
        composable(Screen.TermsPrivacy.route) { TermsPrivacyScreen(navController) }
        composable(Screen.ApplicationRules.route) { ApplicationRulesScreen(navController) }''',
    "NavGraph: register 3 new composables",
)

# ---------------------------------------------------------------------------
# 3) SplashScreen.kt: brand-new (no session) users -> Register instead of Login
# ---------------------------------------------------------------------------
apply(
    SPLASH,
    '''        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }''',
    '''        } else {
            navController.navigate(Screen.Register.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }''',
    "SplashScreen: cold-start default -> Register",
)

# ---------------------------------------------------------------------------
# 4) RegisterScreen.kt: after successful register -> TermsPolicy (not Profile)
# ---------------------------------------------------------------------------
apply(
    REGISTER,
    '''                    navController.navigate(Screen.Profile.createRoute("onboarding")) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                } else {''',
    '''                    navController.navigate(Screen.TermsPolicy.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                } else {''',
    "RegisterScreen: route to TermsPolicy after signup",
)

# ---------------------------------------------------------------------------
# 5) SettingsScreen.kt: add 2 new rows before Logout
# ---------------------------------------------------------------------------
apply(
    SETTINGS,
    '''        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout''',
    '''        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.6 Terms & Privacy Policy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.TermsPrivacy.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Policy, contentDescription = "Terms & Privacy Policy", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Terms & Privacy Policy", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        // 3.7 Application Laws & Rules
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.ApplicationRules.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Gavel, contentDescription = "Application Laws & Rules", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Application Laws & Rules", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout''',
    "SettingsScreen: add Terms & Privacy / Application Rules rows",
)

print("\\nAll patches applied successfully.")
