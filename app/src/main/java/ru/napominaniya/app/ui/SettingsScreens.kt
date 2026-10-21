@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.napominaniya.app.BuildConfig
import ru.napominaniya.app.core.*
import ru.napominaniya.app.data.*
import java.time.LocalDate

data class PermStatus(val notif: Boolean, val exact: Boolean, val fullScreen: Boolean, val battery: Boolean, val dnd: Boolean) {
    val missing get() = listOf(notif, exact, fullScreen, battery).count { !it }
}

fun permStatus(ctx: android.content.Context): PermStatus {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val pm = ctx.getSystemService(PowerManager::class.java)
    return PermStatus(
        notif = Notifier.canPost(ctx) && nm.areNotificationsEnabled(),
        exact = Scheduler.canExact(ctx),
        fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
        battery = pm.isIgnoringBatteryOptimizations(ctx.packageName),
        dnd = nm.isNotificationPolicyAccessGranted
    )
}

@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = App.instance
    val s = app.settings
    val v by s.version.collectAsState()
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val perm = remember(tick) { permStatus(ctx) }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            key(v) {
                Card(Modifier.fillMaxWidth().padding(16.dp).clickable { nav.navigate("permissions") },
                    colors = CardDefaults.cardColors(containerColor = if (perm.missing == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (perm.missing == 0) Icons.Default.VerifiedUser else Icons.Default.Warning, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Разрешения и надёжность", style = MaterialTheme.typography.titleMedium)
                            Text(if (perm.missing == 0) "Всё настроено — оповещения будут приходить вовремя" else "Не выдано разрешений: ${perm.missing}. Нажмите, чтобы исправить",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }

                SectionHeader("Ситуации и правила")
                SettingRow("Тихие часы", if (s.quietEnabled) "${TimeUtil.hm(s.quietStart / 60, s.quietStart % 60)}–${TimeUtil.hm(s.quietEnd / 60, s.quietEnd % 60)} · ${s.quietMode.title.lowercase()}" else "Выключены",
                    Icons.Default.Bedtime, onClick = { nav.navigate("quiet") })
                SettingRow("Выходные и праздники", "Выходные: ${Days.describe(s.weekendDays)}; праздники РФ: ${if (s.ruHolidays) "да" else "нет"}; своих дат: ${s.customHolidays.size}",
                    Icons.Default.Celebration, onClick = { nav.navigate("holidays") })
                SettingRow("Профили (шаблоны настроек)", "Работа, Лекарства, Сон, Встреча… — готовые наборы параметров", Icons.Default.Tune, onClick = { nav.navigate("profiles") })
                SettingRow("Категории и цвета", null, Icons.Default.Label, onClick = { nav.navigate("categories") })

                SectionHeader("По умолчанию")
                Stepper("Отложить на", s.defaultSnooze, 1..60, format = { "$it мин" }) { s.defaultSnooze = it }
                Stepper("Предварительное напоминание", s.defaultPreReminder, 0..1440, step = 5, format = { if (it == 0) "нет" else "за " + TimeUtil.describeMinutes(it) }) { s.defaultPreReminder = it }

                SectionHeader("Уведомления")
                SettingRow("Каналы уведомлений", "Системные настройки звука и важности для каждого канала", Icons.Default.NotificationsActive, onClick = { nav.navigate("channels") })
                SwitchRow("Возвращать смахнутые заметки", s.repinOnSwipe, "Закреплённая заметка вернётся в шторку, если её случайно смахнуть", Icons.Default.PushPin) { s.repinOnSwipe = it }
                SwitchRow("Сообщать о пропущенных", s.missedOnBoot, "После включения телефона показать напоминания, пропущенные пока он был выключен", Icons.Default.NotificationImportant) { s.missedOnBoot = it }

                SectionHeader("Оформление")
                Dropdown("Тема", ThemeMode.entries, s.theme, { it.title }) { s.theme = it }
                if (Build.VERSION.SDK_INT >= 31) SwitchRow("Цвета обоев (Material You)", s.dynamicColor, icon = Icons.Default.Palette) { s.dynamicColor = it }

                SectionHeader("История")
                Stepper("Хранить историю", s.historyDays, 1..365, format = { "$it дн" }) { s.historyDays = it }
                SettingRow("Очистить историю", null, Icons.Default.DeleteSweep, onClick = { confirmClear = true })

                SectionHeader("О приложении")
                SettingRow("Напоминания ${BuildConfig.VERSION_NAME}", "Будильники, напоминания, сценарии, живые заметки. Все данные хранятся только на устройстве.", Icons.Default.Info)
                SettingRow("Перепланировать всё", "Если что-то не сработало после смены времени или часового пояса", Icons.Default.Refresh, onClick = {
                    scope.launch { Scheduler.rescheduleAll(app, false); android.widget.Toast.makeText(app, "Готово", android.widget.Toast.LENGTH_SHORT).show() }
                })
            }
        }
    }
    if (confirmClear) ConfirmDialog("Очистить историю?", "Все записи будут удалены", "Очистить", { confirmClear = false }) { scope.launch { app.db.history().clear() } }
}

@Composable
fun PermissionsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val st = remember(tick, refresh) { permStatus(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } }
    val pkg = Uri.parse("package:${ctx.packageName}")

    EditScaffold("Разрешения", { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Android ограничивает фоновую работу приложений. Чтобы будильники и напоминания срабатывали точно — даже в режиме Doze, при экономии заряда и на заблокированном экране — выдайте разрешения ниже.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            PermRow("Уведомления", "Без этого напоминания не будут видны (Android 13+)", st.notif) {
                if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            PermRow("Точные будильники", "«Будильники и напоминания» — срабатывание минута в минуту", st.exact) {
                if (Build.VERSION.SDK_INT >= 31) open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            }
            PermRow("Полноэкранные уведомления", "Показ будильника поверх экрана блокировки (Android 14+)", st.fullScreen) {
                if (Build.VERSION.SDK_INT >= 34) open(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            }
            PermRow("Без ограничений батареи", "Исключение из оптимизации — система не будет «усыплять» приложение", st.battery) {
                @Suppress("BatteryLife")
                open(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            }
            PermRow("Доступ к «Не беспокоить» (необязательно)", "Позволяет каналу будильника звучать в режиме «Не беспокоить»", st.dnd, optional = true) {
                open(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            SectionHeader("Производители с агрессивной экономией")
            Text("На Xiaomi (MIUI/HyperOS), Huawei/Honor, Samsung, Oppo/Realme, Vivo дополнительно включите «Автозапуск» и отключите ограничения фоновой активности для приложения. Также на Samsung уберите приложение из «Спящих приложений».",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            TextButton({ open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }, Modifier.padding(8.dp)) {
                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(6.dp)); Text("Открыть настройки приложения")
            }
            SectionHeader("Проверка")
            TextButton({
                App.instance.scope.launch {
                    val r = Reminder(id = 0, title = "Тестовое напоминание", description = "Если вы видите это уведомление — всё работает. Кнопки «Готово» и «Отложить» тоже можно проверить.",
                        startAt = System.currentTimeMillis() + 10_000)
                    val id = App.instance.db.reminders().upsert(r)
                    App.instance.db.reminders().get(id)?.let { Scheduler.scheduleReminder(App.instance, it) }
                }
                android.widget.Toast.makeText(ctx, "Уведомление придёт через 10 секунд", android.widget.Toast.LENGTH_SHORT).show()
            }, Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Default.NotificationAdd, null); Spacer(Modifier.width(6.dp)); Text("Тестовое напоминание через 10 секунд") }
            TextButton({
                App.instance.scope.launch {
                    val t = java.time.LocalDateTime.now().plusMinutes(1)
                    val a = Alarm(hour = t.hour, minute = t.minute, label = "Тестовый будильник",
                        alert = AlertSettings(ringMode = true, gradualSeconds = 10, silentBehavior = SilentBehavior.FORCE_SOUND, autoDismissMinutes = 2))
                    val id = App.instance.db.alarms().upsert(a)
                    App.instance.db.alarms().get(id)?.let { Scheduler.scheduleAlarm(App.instance, it) }
                }
                android.widget.Toast.makeText(ctx, "Будильник зазвонит в начале следующей минуты", android.widget.Toast.LENGTH_SHORT).show()
            }, Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Default.Alarm, null); Spacer(Modifier.width(6.dp)); Text("Тестовый будильник (через ~1 минуту)") }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, optional: Boolean = false, onFix: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else if (optional) Icons.Default.RadioButtonUnchecked else Icons.Default.Error, null,
                tint = if (ok) Color(0xFF43A047) else if (optional) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            if (!ok) Button(onFix) { Text("Выдать") } else TextButton(onFix) { Text("Открыть") }
        }
    }
}

@Composable
fun QuietHoursScreen(nav: NavHostController) {
    val s = App.instance.settings
    val v by s.version.collectAsState()
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    EditScaffold("Тихие часы", { nav.popBackStack() }) { pad ->
        key(v) {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                Text("В тихие часы напоминания приходят без звука или переносятся на конец периода. Будильники звонят всегда. Для отдельных напоминаний и профилей можно включить «Игнорировать тихие часы».",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                SwitchRow("Тихие часы", s.quietEnabled, icon = Icons.Default.Bedtime) { s.quietEnabled = it }
                SettingRow("Начало", TimeUtil.hm(s.quietStart / 60, s.quietStart % 60), Icons.Default.NightsStay, onClick = { pickStart = true })
                SettingRow("Конец", TimeUtil.hm(s.quietEnd / 60, s.quietEnd % 60), Icons.Default.WbSunny, onClick = { pickEnd = true })
                SectionHeader("Дни (по дню начала периода)")
                DaysSelector(s.quietDays) { s.quietDays = it }
                Dropdown("Что делать с напоминаниями", QuietMode.entries, s.quietMode, { it.title }) { s.quietMode = it }
                SwitchRow("Критический приоритет звучит всегда", s.quietAllowCritical, icon = Icons.Default.PriorityHigh) { s.quietAllowCritical = it }
            }
        }
    }
    if (pickStart) TimePickDialog(s.quietStart / 60, s.quietStart % 60, { pickStart = false }) { h, m -> s.quietStart = h * 60 + m; pickStart = false }
    if (pickEnd) TimePickDialog(s.quietEnd / 60, s.quietEnd % 60, { pickEnd = false }) { h, m -> s.quietEnd = h * 60 + m; pickEnd = false }
}

@Composable
fun HolidaysScreen(nav: NavHostController) {
    val app = App.instance
    val s = app.settings
    val v by s.version.collectAsState()
    val scope = rememberCoroutineScope()
    EditScaffold("Выходные и праздники", { nav.popBackStack() }) { pad ->
        key(v) {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                SectionHeader("Какие дни считать выходными")
                DaysSelector(s.weekendDays) { s.weekendDays = it; scope.launch { Scheduler.rescheduleAll(app, false) } }
                Text("Используется для «другого времени в выходные» у будильников и «пропускать выходные» у напоминаний.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                SwitchRow("Государственные праздники РФ", s.ruHolidays, "1–8 января, 23 февраля, 8 марта, 1 и 9 мая, 12 июня, 4 ноября (переносы выходных не учитываются — добавьте их вручную)", Icons.Default.Flag) {
                    s.ruHolidays = it; scope.launch { Scheduler.rescheduleAll(app, false) }
                }
                SectionHeader("Свои праздники и нерабочие дни")
                SkipDatesEditor("Добавить дату", s.customHolidays.toList()) { s.customHolidays = it.toSet(); scope.launch { Scheduler.rescheduleAll(app, false) } }
                Text("Будильники и напоминания с включённым «пропускать праздники» не сработают в эти дни.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ProfilesScreen(nav: NavHostController) {
    val app = App.instance
    val profiles by app.db.profiles().all().collectAsState(initial = emptyList())
    EditScaffold("Профили", { nav.popBackStack() }, actions = { IconButton({ nav.navigate("profile/0") }) { Icon(Icons.Default.Add, "Новый профиль") } }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Text("Профиль — готовый набор настроек (звук, вибрация, повторы, важность, задания…). Выберите профиль при создании будильника, напоминания или запуске сценария.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            items(profiles, key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { nav.navigate("profile/${p.id}") },
                    leadingContent = { Text(p.emoji, fontSize = 28.sp) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(p.description.ifBlank { "${p.alert.importance.title.substringBefore(" (")} · ${p.alert.vibration.title}" }) },
                    trailingContent = { ColorDot(p.color, 16) })
            }
        }
    }
}

@Composable
fun ProfileEditScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var p by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(id) { p = if (id == 0L) Profile(name = "", emoji = "⭐") else app.db.profiles().get(id) }
    var confirmDel by remember { mutableStateOf(false) }
    val cur = p ?: return
    EditScaffold(if (id == 0L) "Новый профиль" else "Профиль", { nav.popBackStack() }, actions = {
        if (id != 0L) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, "Удалить") }
        TextButton({ scope.launch { if (cur.name.isNotBlank()) { app.db.profiles().upsert(cur); nav.popBackStack() } } }) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(cur.emoji, { p = cur.copy(emoji = it.take(4)) }, label = { Text("Значок") }, singleLine = true, modifier = Modifier.width(90.dp))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(cur.name, { p = cur.copy(name = it) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(cur.description, { p = cur.copy(description = it) }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            ColorPickerRow(cur.color, allowNone = false) { p = cur.copy(color = it) }
            Dropdown("Приоритет по умолчанию", Priority.entries, cur.priority, { it.title }) { p = cur.copy(priority = it) }
            SettingRow("Предварительные напоминания", if (cur.preReminders.isEmpty()) "нет" else cur.preReminders.joinToString { "за " + TimeUtil.describeMinutes(it) })
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf(10, 15, 30, 60, 1440).forEach { m ->
                    FilterChip(m in cur.preReminders, { p = cur.copy(preReminders = if (m in cur.preReminders) cur.preReminders - m else (cur.preReminders + m).sorted()) },
                        { Text(TimeUtil.describeMinutes(m)) }, Modifier.padding(3.dp))
                }
            }
            SectionHeader("Категория по умолчанию")
            CategoryPicker(cats, cur.categoryId) { p = cur.copy(categoryId = it) }
            AlertSettingsEditor(cur.alert, AlertMode.REMINDER) { p = cur.copy(alert = it) }
        }
    }
    if (confirmDel) ConfirmDialog("Удалить профиль?", cur.name, onDismiss = { confirmDel = false }) { scope.launch { app.db.profiles().delete(cur); nav.popBackStack() } }
}

@Composable
fun CategoriesScreen(nav: NavHostController) {
    val app = App.instance
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<Category?>(null) }
    EditScaffold("Категории", { nav.popBackStack() }, actions = { IconButton({ edit = Category(name = "", color = palette[5]) }) { Icon(Icons.Default.Add, "Новая") } }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(cats, key = { it.id }) { c ->
                ListItem(modifier = Modifier.clickable { edit = c }, leadingContent = { ColorDot(c.color, 20) }, headlineContent = { Text(c.name) },
                    trailingContent = { IconButton({ scope.launch { app.db.categories().delete(c) } }) { Icon(Icons.Default.Delete, "Удалить") } })
            }
        }
    }
    edit?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var color by remember(c) { mutableIntStateOf(c.color) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(if (c.id == 0L) "Новая категория" else "Категория") },
            text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true); ColorPickerRow(color, allowNone = false) { color = it } } },
            confirmButton = { TextButton({ if (name.isNotBlank()) scope.launch { app.db.categories().upsert(c.copy(name = name, color = color)) }; edit = null }) { Text("Сохранить") } },
            dismissButton = { TextButton({ edit = null }) { Text("Отмена") } })
    }
}

@Composable
fun ChannelsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val nm = ctx.getSystemService(NotificationManager::class.java)
    var refresh by remember { mutableIntStateOf(0) }
    val tick = rememberResumeTick()
    val channels = remember(refresh, tick) { nm.notificationChannels.sortedBy { (if (it.group == Notifier.GROUP_CUSTOM) "1" else "0") + it.name } }
    fun openChannel(id: String) = runCatching {
        ctx.startActivity(Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    EditScaffold("Каналы уведомлений", { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text("Каждый канал можно открыть в системных настройках: звук, вибрация, всплывание, показ на экране блокировки, обход «Не беспокоить». Персональные каналы создаются автоматически под комбинации настроек ваших напоминаний.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                SettingRow("Все уведомления приложения", null, Icons.Default.Settings, onClick = {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                SettingRow("Удалить неиспользуемые персональные каналы", "Каналы будут созданы заново при необходимости", Icons.Default.CleaningServices, onClick = {
                    App.instance.scope.launch {
                        val db = App.instance.db
                        val used = (db.reminders().list().map { it.alert } + db.profiles().list().map { it.alert }).map { Notifier.channelFor(ctx, it) }.toSet()
                        nm.notificationChannels.filter { it.id.startsWith("c_") && it.id !in used }.forEach { nm.deleteNotificationChannel(it.id) }
                        refresh++
                    }
                })
            }
            items(channels, key = { it.id }) { ch ->
                val imp = when (ch.importance) { NotificationManager.IMPORTANCE_HIGH -> "высокая"; NotificationManager.IMPORTANCE_DEFAULT -> "обычная"; NotificationManager.IMPORTANCE_LOW -> "низкая"; NotificationManager.IMPORTANCE_NONE -> "отключён"; else -> "минимальная" }
                ListItem(modifier = Modifier.clickable { openChannel(ch.id) },
                    leadingContent = { Icon(if (ch.group == Notifier.GROUP_CUSTOM) Icons.Default.Tune else Icons.Default.Notifications, null) },
                    headlineContent = { Text(ch.name.toString()) },
                    supportingContent = { Text("Важность: $imp" + (ch.description?.let { "\n$it" } ?: "")) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) })
            }
        }
    }
}
