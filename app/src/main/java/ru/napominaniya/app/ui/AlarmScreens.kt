@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.napominaniya.app.core.App
import ru.napominaniya.app.core.Scheduler
import ru.napominaniya.app.core.logHistory
import ru.napominaniya.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(nav: NavHostController) {
    val app = App.instance
    val alarms by app.db.alarms().all().collectAsState(initial = emptyList())
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = app
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); tick = System.currentTimeMillis() } }
    var confirmDelete by remember { mutableStateOf<Alarm?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Будильники") }) },
        floatingActionButton = { FloatingActionButton({ nav.navigate("alarm/0") }) { Icon(Icons.Default.Add, "Новый будильник") } }
    ) { pad ->
        val next = alarms.filter { it.enabled }.map { if (it.snoozedUntil > tick) it.snoozedUntil else it.nextTriggerAt }.filter { it > 0 }.minOrNull()
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (next != null) "Ближайший будильник" else "Нет активных будильников", style = MaterialTheme.typography.labelLarge)
                        if (next != null) {
                            Text(TimeUtil.full(next), style = MaterialTheme.typography.titleLarge)
                            Text(TimeUtil.relative(next, tick), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (alarms.isEmpty()) item { EmptyState(Icons.Default.AlarmAdd, "Будильников пока нет", "Нажмите «+», чтобы создать первый") }
            items(alarms, key = { it.id }) { a ->
                val cat = cats.firstOrNull { it.id == a.categoryId }
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { nav.navigate("alarm/${a.id}") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cat != null) Box(Modifier.width(4.dp).height(56.dp).background(Color(cat.color)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(TimeUtil.hm(a.hour, a.minute), fontSize = 40.sp, fontWeight = FontWeight.Light,
                                color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            Text(listOfNotNull(a.label.ifBlank { null }, Days.describe(a.days)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                            FlowRow {
                                if (a.weekendTimeEnabled) Tag("Вых.: ${TimeUtil.hm(a.weekendHour, a.weekendMinute)}")
                                if (a.skipHolidays) Tag("Кроме праздников")
                                if (a.alert.challenge != Challenge.NONE) Tag(a.alert.challenge.title)
                                if (a.alert.gradualSeconds > 0) Tag("Нарастание")
                                cat?.let { Tag(it.name, Color(it.color).copy(alpha = 0.25f)) }
                            }
                            if (a.enabled) {
                                val t = if (a.snoozedUntil > tick) a.snoozedUntil else a.nextTriggerAt
                                if (t > 0) Text((if (a.snoozedUntil > tick) "Отложен до ${TimeUtil.time(t)}" else "Сработает ${TimeUtil.relative(t, tick)}") +
                                    if (a.skipNextAt != 0L) " (одно срабатывание пропускается)" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(a.enabled, { on -> scope.launch { Scheduler.scheduleAlarm(ctx, a.copy(enabled = on, snoozedUntil = 0, skipNextAt = 0)) } })
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                                DropdownMenu(menu, { menu = false }) {
                                    if (a.enabled && a.days != 0 && a.nextTriggerAt > 0) DropdownMenuItem(
                                        text = { Text(if (a.skipNextAt != 0L) "Не пропускать" else "Пропустить ближайший") },
                                        leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                                        onClick = {
                                            menu = false
                                            scope.launch {
                                                val upd = if (a.skipNextAt != 0L) a.copy(skipNextAt = 0) else a.copy(skipNextAt = a.nextTriggerAt)
                                                Scheduler.scheduleAlarm(ctx, upd)
                                                logHistory("Будильник", a.label.ifBlank { TimeUtil.hm(a.hour, a.minute) }, if (upd.skipNextAt != 0L) "Ближайшее срабатывание пропущено" else "Пропуск отменён")
                                            }
                                        })
                                    DropdownMenuItem(text = { Text("Дублировать") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                        menu = false
                                        scope.launch { val id = app.db.alarms().upsert(a.copy(id = 0, nextTriggerAt = 0, snoozedUntil = 0)); app.db.alarms().get(id)?.let { Scheduler.scheduleAlarm(ctx, it) } }
                                    })
                                    DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; confirmDelete = a })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { a ->
        ConfirmDialog("Удалить будильник?", "${TimeUtil.hm(a.hour, a.minute)} ${a.label}", onDismiss = { confirmDelete = null }) {
            scope.launch { Scheduler.cancel(ctx, Scheduler.T_ALARM, a.id); app.db.alarms().delete(a) }
        }
    }
}

@Composable
fun AlarmEditScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val profiles by app.db.profiles().all().collectAsState(initial = emptyList())
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var a by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(id) {
        a = if (id == 0L) {
            val sleep = app.db.profiles().list().firstOrNull { it.name == "Сон" }
            Alarm(alert = AlertSettings(ringMode = true, gradualSeconds = 30, silentBehavior = SilentBehavior.FORCE_SOUND,
                ignoreQuietHours = true, snoozeMinutes = app.settings.defaultSnooze))
                .let { al -> al }
        } else app.db.alarms().get(id)
    }
    var pickTime by remember { mutableStateOf(false) }
    var pickWeekend by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val cur = a ?: return

    fun save() {
        scope.launch {
            val newId = app.db.alarms().upsert(cur.copy(enabled = true, snoozedUntil = 0, snoozeCount = 0))
            val saved = app.db.alarms().get(if (cur.id == 0L) newId else cur.id)
            if (saved != null) {
                val s = Scheduler.scheduleAlarm(app, saved)
                if (s.nextTriggerAt > 0) android.widget.Toast.makeText(app, "Будильник сработает ${TimeUtil.relative(s.nextTriggerAt)}", android.widget.Toast.LENGTH_SHORT).show()
            }
            nav.popBackStack()
        }
    }

    EditScaffold(if (id == 0L) "Новый будильник" else "Будильник", { nav.popBackStack() }, actions = {
        if (id != 0L) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, "Удалить") }
        TextButton(::save) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(TimeUtil.hm(cur.hour, cur.minute), fontSize = 64.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.fillMaxWidth().clickable { pickTime = true }.padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedTextField(cur.label, { a = cur.copy(label = it) }, label = { Text("Название") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))

            SectionHeader("Повтор")
            DaysSelector(cur.days) { a = cur.copy(days = it) }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("Однократно" to 0, "Будни" to Days.WEEKDAYS, "Выходные" to Days.WEEKEND, "Каждый день" to Days.ALL).forEach { (t, m) ->
                    AssistChip({ a = cur.copy(days = m) }, { Text(t) }, Modifier.padding(4.dp))
                }
            }
            SwitchRow("Другое время в выходные", cur.weekendTimeEnabled, if (cur.weekendTimeEnabled) "В выходные: ${TimeUtil.hm(cur.weekendHour, cur.weekendMinute)} (нажмите, чтобы изменить)" else "Например, будни 7:00, выходные 9:30", Icons.Default.Weekend) {
                a = cur.copy(weekendTimeEnabled = it)
            }
            if (cur.weekendTimeEnabled) SettingRow("Время в выходные", TimeUtil.hm(cur.weekendHour, cur.weekendMinute), Icons.Default.Schedule, onClick = { pickWeekend = true })
            SwitchRow("Не звонить в праздники", cur.skipHolidays, "Праздники настраиваются в «Настройки → Выходные и праздники»", Icons.Default.Celebration) { a = cur.copy(skipHolidays = it) }
            SkipDatesEditor("Пропустить даты", cur.skipDates) { a = cur.copy(skipDates = it) }

            SectionHeader("Профиль и категория")
            Dropdown("Применить профиль", listOf<Profile?>(null) + profiles, profiles.firstOrNull { it.id == cur.profileId },
                { it?.let { p -> "${p.emoji} ${p.name}" } ?: "— не выбран —" }) { p ->
                a = if (p == null) cur.copy(profileId = 0) else cur.copy(profileId = p.id, alert = p.alert.copy(ringMode = true), categoryId = if (p.categoryId != 0L) p.categoryId else cur.categoryId)
            }
            CategoryPicker(cats, cur.categoryId) { a = cur.copy(categoryId = it) }

            AlertSettingsEditor(cur.alert, AlertMode.ALARM) { a = cur.copy(alert = it.copy(ringMode = true)) }
        }
    }
    if (pickTime) TimePickDialog(cur.hour, cur.minute, { pickTime = false }) { h, m -> a = cur.copy(hour = h, minute = m); pickTime = false }
    if (pickWeekend) TimePickDialog(cur.weekendHour, cur.weekendMinute, { pickWeekend = false }) { h, m -> a = cur.copy(weekendHour = h, weekendMinute = m); pickWeekend = false }
    if (confirmDel) ConfirmDialog("Удалить будильник?", "Действие нельзя отменить", onDismiss = { confirmDel = false }) {
        scope.launch { Scheduler.cancel(app, Scheduler.T_ALARM, cur.id); app.db.alarms().delete(cur); nav.popBackStack() }
    }
}
