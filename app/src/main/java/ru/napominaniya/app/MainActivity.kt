package ru.napominaniya.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import ru.napominaniya.app.core.App
import ru.napominaniya.app.core.Notifier
import ru.napominaniya.app.data.ThemeMode
import ru.napominaniya.app.ui.*

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_ROUTE = "route" }
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                val route by pendingRoute.collectAsState()
                LaunchedEffect(route) {
                    route?.takeIf { it.isNotBlank() }?.let { r ->
                        runCatching { nav.navigate(r) }
                        pendingRoute.value = null
                    }
                }
                FirstRun(nav)
                AppNav(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }
}

@Composable
fun AppTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val s = App.instance.settings
    val v by s.version.collectAsState()
    val ctx = LocalContext.current
    val dark = forceDark ?: when (s.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val scheme = remember(v, dark) {
        if (s.dynamicColor && Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) }
        else if (dark) darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFFFFCC80), tertiary = Color(0xFFA5D6A7))
        else lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFFEF6C00), tertiary = Color(0xFF2E7D32))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun FirstRun(nav: NavHostController) {
    val s = App.instance.settings
    var show by remember { mutableStateOf(!s.onboarded) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(App.instance)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false; s.onboarded = true },
        title = { Text("Добро пожаловать!") },
        text = { Text("Чтобы будильники и напоминания срабатывали точно и вовремя (даже в режиме энергосбережения и после перезагрузки), выдайте приложению несколько разрешений: уведомления, точные будильники, полноэкранные уведомления и работу без ограничений батареи.") },
        confirmButton = { TextButton({ show = false; s.onboarded = true; nav.navigate("permissions") }) { Text("Настроить") } },
        dismissButton = { TextButton({ show = false; s.onboarded = true }) { Text("Позже") } }
    )
}

private data class Tab(val route: String, val title: String, val icon: ImageVector, val iconSel: ImageVector)
private val tabs = listOf(
    Tab("alarms", "Будильники", Icons.Outlined.Alarm, Icons.Filled.Alarm),
    Tab("reminders", "Напоминания", Icons.Outlined.NotificationsActive, Icons.Filled.NotificationsActive),
    Tab("notes", "Заметки", Icons.Outlined.StickyNote2, Icons.Filled.StickyNote2),
    Tab("history", "История", Icons.Outlined.History, Icons.Filled.History),
    Tab("settings", "Настройки", Icons.Outlined.Settings, Icons.Filled.Settings)
)

@Composable
fun AppNav(nav: NavHostController) {
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route
    val showBar = tabs.any { it.route == current }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("alarms") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(if (current == t.route) t.iconSel else t.icon, t.title) },
                        label = { Text(t.title, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "alarms", modifier = Modifier.padding(pad)) {
            composable("alarms") { AlarmsScreen(nav) }
            composable("reminders") { RemindersScreen(nav) }
            composable("notes") { NotesScreen(nav) }
            composable("history") { HistoryScreen() }
            composable("settings") { SettingsScreen(nav) }
            composable("alarm/{id}", listOf(navArgument("id") { type = NavType.LongType })) { AlarmEditScreen(nav, it.arguments!!.getLong("id")) }
            composable("reminder/{id}", listOf(navArgument("id") { type = NavType.LongType })) { ReminderEditScreen(nav, it.arguments!!.getLong("id")) }
            composable("note/{id}", listOf(navArgument("id") { type = NavType.LongType })) { NoteEditScreen(nav, it.arguments!!.getLong("id")) }
            composable("scenario_edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { ScenarioEditScreen(nav, it.arguments!!.getLong("id")) }
            composable("scenario_run/{id}", listOf(navArgument("id") { type = NavType.LongType })) { ScenarioRunScreen(nav, it.arguments!!.getLong("id")) }
            composable("medcourse") { MedCourseScreen(nav) }
            composable("profiles") { ProfilesScreen(nav) }
            composable("profile/{id}", listOf(navArgument("id") { type = NavType.LongType })) { ProfileEditScreen(nav, it.arguments!!.getLong("id")) }
            composable("categories") { CategoriesScreen(nav) }
            composable("quiet") { QuietHoursScreen(nav) }
            composable("holidays") { HolidaysScreen(nav) }
            composable("permissions") { PermissionsScreen(nav) }
            composable("channels") { ChannelsScreen(nav) }
        }
    }
}
