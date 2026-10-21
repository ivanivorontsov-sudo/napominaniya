@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.napominaniya.app.core.*
import ru.napominaniya.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemindersScreen(nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Напоминания") }, actions = {
                    IconButton({ nav.navigate("medcourse") }) { Icon(Icons.Default.Medication, "Курс лекарств") }
                })
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Список") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Сценарии") })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 0) nav.navigate("reminder/0") else nav.navigate("scenario_edit/0") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == 0) "Напоминание" else "Свой сценарий") })
        }
    ) { pad ->
        Box(Modifier.padding(pad)) { if (tab == 0) ReminderList(nav) else ScenariosTab(nav) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderList(nav: NavHostController) {
    val app = App.instance
    val all by app.db.reminders().all().collectAsState(initial = emptyList())
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 активные, 1 ждут, 2 выполненные, 3 все
    var cat by rememberSaveable { mutableLongStateOf(-1L) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = all.filter {
        when (filter) { 0 -> !it.completed && it.enabled; 1 -> it.awaitingAck; 2 -> it.completed; else -> true } &&
            (cat == -1L || it.categoryId == cat) &&
            (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true))
    }.sortedWith(compareByDescending<Reminder> { it.awaitingAck }.thenBy { if (it.nextTriggerAt == 0L) Long.MAX_VALUE else it.nextTriggerAt })

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                listOf("Активные", "Ждут ответа", "Выполненные", "Все").forEachIndexed { i, t ->
                    FilterChip(filter == i, { filter = i }, { Text(t) }, Modifier.padding(4.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                FilterChip(cat == -1L, { cat = -1 }, { Text("Все категории") }, Modifier.padding(4.dp))
                cats.forEach { c -> FilterChip(cat == c.id, { cat = if (cat == c.id) -1 else c.id }, { Text(c.name) }, Modifier.padding(4.dp), leadingIcon = { ColorDot(c.color) }) }
            }
        }
        if (list.isEmpty()) item { EmptyState(Icons.Default.NotificationsNone, "Здесь пусто", "Создайте напоминание или запустите готовый сценарий на вкладке «Сценарии»") }
        items(list, key = { it.id }) { r ->
            val c = cats.firstOrNull { it.id == r.categoryId }
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { nav.navigate("reminder/${r.id}") },
                colors = if (r.awaitingAck) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(48.dp).background(Color(r.priority.color)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (r.completed) FontWeight.Normal else FontWeight.Medium,
                            color = if (r.completed || !r.enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val whenText = when {
                            r.completed -> "Выполнено"
                            r.snoozedUntil > System.currentTimeMillis() -> "Повтор в ${TimeUtil.time(r.snoozedUntil)}"
                            r.nextTriggerAt > 0 -> TimeUtil.full(r.nextTriggerAt) + " · " + TimeUtil.relative(r.nextTriggerAt)
                            r.awaitingAck -> "Сработало, ждёт подтверждения"
                            else -> "Не запланировано"
                        }
                        Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow {
                            if (r.repeat != RepeatType.ONCE) Tag(r.repeat.title)
                            if (r.preReminders.isNotEmpty()) Tag("заранее: " + r.preReminders.joinToString { TimeUtil.describeMinutes(it) })
                            if (r.scenarioRunId != 0L) Tag("сценарий")
                            if (r.stockEnabled) Tag("запас: ${r.stockCount}", if (r.stockCount <= r.stockThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer)
                            if (r.alert.ringMode) Tag("звонок")
                            if (r.alert.nagEveryMinutes > 0) Tag("до подтверждения")
                            c?.let { Tag(it.name, Color(it.color).copy(alpha = 0.25f)) }
                        }
                    }
                    if (r.awaitingAck) FilledTonalButton({ scope.launch { Engine.reminderDone(app, r.id) } }) { Text("Готово") }
                    else if (!r.completed) Switch(r.enabled, { on -> scope.launch { Scheduler.scheduleReminder(app, r.copy(enabled = on)) } })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val profiles by app.db.profiles().all().collectAsState(initial = emptyList())
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var r by remember { mutableStateOf<Reminder?>(null) }
    LaunchedEffect(id) {
        r = if (id == 0L) {
            val start = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val pre = app.settings.defaultPreReminder
            Reminder(startAt = TimeUtil.ms(start), preReminders = if (pre > 0) listOf(pre) else emptyList(),
                alert = AlertSettings(snoozeMinutes = app.settings.defaultSnooze))
        } else app.db.reminders().get(id)
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var customPre by remember { mutableStateOf("") }
    val cur = r ?: return
    val start = TimeUtil.ldt(cur.startAt)

    fun save() = scope.launch {
        val base = cur.copy(completed = false, awaitingAck = false, snoozedUntil = 0, nagCount = 0, snoozeCount = 0, enabled = true)
        val newId = app.db.reminders().upsert(base)
        val saved = app.db.reminders().get(if (cur.id == 0L) newId else cur.id) ?: return@launch
        Notifier.cancelReminder(app, saved.id)
        val s = Scheduler.scheduleReminder(app, saved)
        android.widget.Toast.makeText(app, if (s.nextTriggerAt > 0) "Сработает ${TimeUtil.relative(s.nextTriggerAt)}" else "Время уже прошло — напоминание не запланировано", android.widget.Toast.LENGTH_SHORT).show()
        nav.popBackStack()
    }

    EditScaffold(if (id == 0L) "Новое напоминание" else "Напоминание", { nav.popBackStack() }, actions = {
        if (id != 0L) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, "Удалить") }
        TextButton({ save() }) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (cur.awaitingAck) Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сработало и ждёт подтверждения", Modifier.weight(1f))
                    Button({ scope.launch { Engine.reminderDone(app, cur.id); nav.popBackStack() } }) { Text("Готово") }
                }
            }
            OutlinedTextField(cur.title, { r = cur.copy(title = it) }, label = { Text("Что напомнить") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(cur.description, { r = cur.copy(description = it) }, label = { Text("Подробное описание") }, minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

            SectionHeader("Когда")
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton({ pickDate = true }, Modifier.weight(1f)) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(6.dp)); Text(TimeUtil.date(cur.startAt)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ pickTime = true }) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(TimeUtil.time(cur.startAt)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                listOf("Через 15 мин" to 15L, "Через час" to 60L, "Через 3 ч" to 180L, "Завтра 9:00" to -1L).forEach { (t, m) ->
                    AssistChip({
                        val v = if (m > 0) System.currentTimeMillis() + m * 60_000 else TimeUtil.ms(LocalDate.now().plusDays(1).atTime(9, 0))
                        r = cur.copy(startAt = v / 60_000 * 60_000)
                    }, { Text(t) }, Modifier.padding(4.dp))
                }
            }
            Dropdown("Повтор", RepeatType.entries, cur.repeat, { it.title }) { r = cur.copy(repeat = it) }
            when (cur.repeat) {
                RepeatType.WEEKLY -> DaysSelector(if (cur.weekDays == 0) 1 shl (start.dayOfWeek.value - 1) else cur.weekDays) { r = cur.copy(weekDays = it) }
                RepeatType.INTERVAL -> {
                    var unit by remember { mutableIntStateOf(if (cur.intervalMinutes % 1440 == 0L) 2 else if (cur.intervalMinutes % 60 == 0L) 1 else 0) }
                    val mult = listOf(1L, 60L, 1440L)[unit]
                    NumberField("Каждые", (cur.intervalMinutes / mult).toInt(), Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { v -> r = cur.copy(intervalMinutes = (v.coerceAtLeast(1) * mult)) }
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        listOf("мин", "ч", "дн").forEachIndexed { i, t ->
                            FilterChip(unit == i, { val v = cur.intervalMinutes / mult; unit = i; r = cur.copy(intervalMinutes = v.coerceAtLeast(1) * listOf(1L, 60L, 1440L)[i]) }, { Text(t) }, Modifier.padding(2.dp))
                        }
                    }
                }
                else -> {}
            }
            if (cur.repeat != RepeatType.ONCE) {
                SettingRow("Повторять до", if (cur.endAt == 0L) "Бессрочно" else TimeUtil.date(cur.endAt), Icons.Default.EventAvailable, onClick = { pickEnd = true }) {
                    if (cur.endAt != 0L) IconButton({ r = cur.copy(endAt = 0) }) { Icon(Icons.Default.Clear, "Сбросить") }
                }
                SwitchRow("Пропускать выходные", cur.skipWeekends, icon = Icons.Default.Weekend) { r = cur.copy(skipWeekends = it) }
                SwitchRow("Пропускать праздники", cur.skipHolidays, icon = Icons.Default.Celebration) { r = cur.copy(skipHolidays = it) }
                SkipDatesEditor("Пропустить даты", cur.skipDates) { r = cur.copy(skipDates = it) }
            }

            SectionHeader("Приоритет")
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(cur.priority == p, { r = cur.copy(priority = p) }, { Text(p.title) }, Modifier.padding(3.dp), leadingIcon = { ColorDot(p.color.toInt()) })
                }
            }
            if (cur.priority == Priority.CRITICAL) Text("Критический приоритет может звучать в тихие часы (см. настройки).",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))

            SectionHeader("Предварительные напоминания")
            FlowRow(Modifier.padding(horizontal = 12.dp)) {
                (listOf(5, 10, 15, 30, 60, 120, 180, 1440, 2880, 10080) + cur.preReminders).distinct().sorted().forEach { m ->
                    FilterChip(m in cur.preReminders, {
                        r = cur.copy(preReminders = if (m in cur.preReminders) cur.preReminders - m else (cur.preReminders + m).sorted().take(9))
                    }, { Text("за " + TimeUtil.describeMinutes(m)) }, Modifier.padding(3.dp))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(customPre, { customPre = it }, label = { Text("Своё: напр. «45 мин», «2 ч», «3 д»") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton({
                    TimeUtil.parseOffset(customPre)?.let { v -> val m = kotlin.math.abs(v).toInt(); if (m > 0) r = cur.copy(preReminders = (cur.preReminders + m).distinct().sorted().take(9)) }
                    customPre = ""
                }) { Icon(Icons.Default.AddCircle, "Добавить") }
            }

            SectionHeader("Профиль и категория")
            Dropdown("Применить профиль", listOf<Profile?>(null) + profiles, profiles.firstOrNull { it.id == cur.profileId },
                { it?.let { p -> "${p.emoji} ${p.name}" } ?: "— не выбран —" }) { p ->
                r = if (p == null) cur.copy(profileId = 0) else cur.copy(profileId = p.id, alert = p.alert, priority = p.priority,
                    preReminders = if (p.preReminders.isNotEmpty()) p.preReminders else cur.preReminders,
                    categoryId = if (p.categoryId != 0L) p.categoryId else cur.categoryId)
            }
            CategoryPicker(cats, cur.categoryId) { r = cur.copy(categoryId = it) }

            SectionHeader("Учёт запаса (лекарства и т.п.)")
            SwitchRow("Считать остаток", cur.stockEnabled, "При нажатии «Принял» остаток уменьшается; предупреждение, когда заканчивается", Icons.Default.Inventory) { r = cur.copy(stockEnabled = it) }
            if (cur.stockEnabled) {
                Stepper("Остаток", cur.stockCount, 0..9999) { r = cur.copy(stockCount = it) }
                Stepper("Расход за приём", cur.stockPerDose, 1..20) { r = cur.copy(stockPerDose = it) }
                Stepper("Предупредить, когда останется", cur.stockThreshold, 0..200) { r = cur.copy(stockThreshold = it) }
            }

            AlertSettingsEditor(cur.alert, AlertMode.REMINDER) { r = cur.copy(alert = it) }
        }
    }
    if (pickDate) DatePickDialog(start.toLocalDate(), { pickDate = false }) { d -> r = cur.copy(startAt = TimeUtil.ms(d.atTime(start.toLocalTime()))); pickDate = false }
    if (pickTime) TimePickDialog(start.hour, start.minute, { pickTime = false }) { h, m -> r = cur.copy(startAt = TimeUtil.ms(start.toLocalDate().atTime(h, m))); pickTime = false }
    if (pickEnd) DatePickDialog(if (cur.endAt == 0L) start.toLocalDate().plusMonths(1) else TimeUtil.ldt(cur.endAt).toLocalDate(), { pickEnd = false }) { d ->
        r = cur.copy(endAt = TimeUtil.ms(d.atTime(LocalTime.of(23, 59)))); pickEnd = false
    }
    if (confirmDel) ConfirmDialog("Удалить напоминание?", cur.title, onDismiss = { confirmDel = false }) {
        scope.launch { Scheduler.cancel(app, Scheduler.T_REMINDER, cur.id); Notifier.cancelReminder(app, cur.id); app.db.reminders().delete(cur); nav.popBackStack() }
    }
}

enum class MealRelation(val title: String) { ANY("Независимо от еды"), BEFORE("До еды (за 30 мин)"), WITH("Во время еды"), AFTER("После еды") }

/** Мастер «Курс лекарств» */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedCourseScreen(nav: NavHostController) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val profiles by app.db.profiles().all().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("1 таблетка") }
    var meal by remember { mutableStateOf(MealRelation.AFTER) }
    var times by remember { mutableStateOf(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var days by remember { mutableIntStateOf(7) }
    var stockOn by remember { mutableStateOf(false) }
    var stock by remember { mutableIntStateOf(20) }
    var perDose by remember { mutableIntStateOf(1) }
    var threshold by remember { mutableIntStateOf(4) }
    var notes by remember { mutableStateOf("") }
    var profileId by remember { mutableLongStateOf(0L) }
    var addTime by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    LaunchedEffect(profiles) { if (profileId == 0L) profiles.firstOrNull { it.name == "Лекарства" }?.let { profileId = it.id } }

    fun create() = scope.launch {
        if (name.isBlank()) { android.widget.Toast.makeText(app, "Укажите название лекарства", android.widget.Toast.LENGTH_SHORT).show(); return@launch }
        val prof = profiles.firstOrNull { it.id == profileId }
        val runId = app.db.scenarios().upsertRun(ScenarioRun(name = "💊 $name", eventAt = TimeUtil.ms(startDate.atTime(times.minOrNull() ?: LocalTime.of(8, 0)))))
        val health = app.db.categories().list().firstOrNull { it.name == "Здоровье" }?.id ?: 0
        val end = if (days > 0) TimeUtil.ms(startDate.plusDays(days.toLong() - 1).atTime(23, 59)) else 0L
        times.sorted().forEachIndexed { i, t ->
            val desc = buildString {
                append("Доза: $dose\n${meal.title}")
                if (notes.isNotBlank()) append("\n$notes")
                if (days > 0) append("\nКурс: $days дн. (до ${startDate.plusDays(days.toLong() - 1)})")
                append("\n\nСхему приёма определяет врач.")
            }
            val r = Reminder(title = "💊 $name — приём ${i + 1} из ${times.size}", description = desc,
                startAt = TimeUtil.ms(startDate.atTime(t)), repeat = RepeatType.DAILY, endAt = end,
                priority = prof?.priority ?: Priority.HIGH, alert = prof?.alert ?: AlertSettings(nagEveryMinutes = 10),
                categoryId = health, profileId = profileId, scenarioRunId = runId,
                stockEnabled = stockOn, stockCount = stock, stockPerDose = perDose, stockThreshold = threshold)
            val rid = app.db.reminders().upsert(r)
            app.db.reminders().get(rid)?.let { Scheduler.scheduleReminder(app, it) }
        }
        logHistory("Лекарства", name, "Создан курс: ${times.size} приём(а) в день" + if (days > 0) ", $days дн." else "")
        android.widget.Toast.makeText(app, "Курс создан", android.widget.Toast.LENGTH_SHORT).show()
        nav.popBackStack()
    }

    EditScaffold("Курс лекарств", { nav.popBackStack() }, actions = { TextButton({ create() }) { Text("Создать") } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text("Приложение не заменяет консультацию врача. Схему приёма, дозировку и длительность курса определяет врач.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Название лекарства") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(dose, { dose = it }, label = { Text("Доза") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            Dropdown("Приём относительно еды", MealRelation.entries, meal, { it.title }) { meal = it }
            OutlinedTextField(notes, { notes = it }, label = { Text("Особые указания (запивать водой, не сочетать с…)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            SectionHeader("Время приёма (${times.size} раз в день)")
            FlowRow(Modifier.padding(horizontal = 12.dp)) {
                times.sorted().forEach { t -> InputChip(false, { times = times - t }, { Text("%02d:%02d".format(t.hour, t.minute)) }, Modifier.padding(3.dp),
                    trailingIcon = { Icon(Icons.Default.Close, "Убрать", Modifier.size(16.dp)) }) }
                AssistChip({ addTime = true }, { Text("Добавить время") }, Modifier.padding(3.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("1 раз" to listOf(9), "2 раза" to listOf(8, 20), "3 раза" to listOf(8, 14, 20), "4 раза" to listOf(8, 12, 16, 20)).forEach { (t, hs) ->
                    AssistChip({ times = hs.map { LocalTime.of(it, 0) } }, { Text(t) }, Modifier.padding(3.dp))
                }
            }
            SettingRow("Начало курса", TimeUtil.date(TimeUtil.ms(startDate.atStartOfDay())), Icons.Default.Event, onClick = { pickStart = true })
            Stepper("Длительность курса", days, 0..365, format = { if (it == 0) "бессрочно" else "$it дн" }) { days = it }
            SectionHeader("Запас")
            SwitchRow("Учитывать остаток", stockOn, "Предупредить, когда лекарство заканчивается", Icons.Default.Inventory) { stockOn = it }
            if (stockOn) {
                Stepper("Сейчас есть (шт.)", stock, 0..9999) { stock = it }
                Stepper("Расход за приём", perDose, 1..20) { perDose = it }
                Stepper("Предупредить при остатке", threshold, 0..200) { threshold = it }
                if (days > 0) Text("На курс нужно: ${days * times.size * perDose} шт." + if (stock < days * times.size * perDose) " — не хватит!" else "",
                    modifier = Modifier.padding(horizontal = 16.dp), color = if (stock < days * times.size * perDose) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            SectionHeader("Настройки оповещения")
            Dropdown("Профиль", profiles, profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull() ?: Profile(name = "—"), { "${it.emoji} ${it.name}" }) { profileId = it.id }
        }
    }
    if (addTime) TimePickDialog(12, 0, { addTime = false }) { h, m -> times = (times + LocalTime.of(h, m)).distinct(); addTime = false }
    if (pickStart) DatePickDialog(startDate, { pickStart = false }) { startDate = it; pickStart = false }
}
