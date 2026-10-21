@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.napominaniya.app.core.*
import ru.napominaniya.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun ScenariosTab(nav: NavHostController) {
    val app = App.instance
    val templates by app.db.scenarios().all().collectAsState(initial = emptyList())
    val runs by app.db.scenarios().runs().collectAsState(initial = emptyList())
    val reminders by app.db.reminders().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var delRun by remember { mutableStateOf<ScenarioRun?>(null) }
    var delTpl by remember { mutableStateOf<ScenarioTemplate?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        if (runs.isNotEmpty()) {
            item { SectionHeader("Запущенные сценарии") }
            items(runs, key = { "run${it.id}" }) { run ->
                val items = reminders.filter { it.scenarioRunId == run.id }
                val active = items.count { !it.completed && it.nextTriggerAt > 0 }
                val next = items.filter { it.nextTriggerAt > 0 }.minByOrNull { it.nextTriggerAt }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(run.name, style = MaterialTheme.typography.titleMedium)
                            Text("Событие: ${TimeUtil.full(run.eventAt)}", style = MaterialTheme.typography.bodySmall)
                            Text("Шагов: ${items.size}, впереди: $active" + (next?.let { " · далее: ${it.title} ${TimeUtil.relative(it.nextTriggerAt)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton({ delRun = run }) { Icon(Icons.Default.Delete, "Отменить сценарий") }
                    }
                }
            }
        }
        item {
            SectionHeader("Шаблоны сценариев")
            Text("Выберите сценарий, укажите время события — приложение само расставит все напоминания до и после него.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton({ nav.navigate("medcourse") }) { Icon(Icons.Default.Medication, null); Spacer(Modifier.width(4.dp)); Text("Мастер курса лекарств") }
                TextButton({ scope.launch { app.restoreBuiltInScenarios() } }) { Text("Восстановить встроенные") }
            }
        }
        templates.groupBy { it.group }.forEach { (group, list) ->
            item(key = "g$group") { Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
            items(list, key = { "t${it.id}" }) { t ->
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { nav.navigate("scenario_run/${t.id}") }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text("${t.steps.size} шаг(ов)" + if (t.builtIn) " · встроенный" else " · свой", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Запустить") }, { menu = false; nav.navigate("scenario_run/${t.id}") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                                DropdownMenuItem({ Text("Изменить") }, { menu = false; nav.navigate("scenario_edit/${t.id}") }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                DropdownMenuItem({ Text("Копировать") }, { menu = false; scope.launch { app.db.scenarios().upsert(t.copy(id = 0, name = t.name + " (копия)", builtIn = false)) } }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                DropdownMenuItem({ Text("Удалить") }, { menu = false; delTpl = t }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            }
                        }
                    }
                }
            }
        }
    }
    delRun?.let { run ->
        ConfirmDialog("Отменить сценарий?", "Будут удалены все напоминания сценария «${run.name}».", "Отменить сценарий", { delRun = null }) {
            scope.launch {
                app.db.reminders().byRun(run.id).forEach { Scheduler.cancel(app, Scheduler.T_REMINDER, it.id); Notifier.cancelReminder(app, it.id); app.db.reminders().delete(it) }
                app.db.scenarios().deleteRun(run)
                logHistory("Сценарий", run.name, "Отменён")
            }
        }
    }
    delTpl?.let { t -> ConfirmDialog("Удалить шаблон?", t.name, onDismiss = { delTpl = null }) { scope.launch { app.db.scenarios().delete(t) } } }
}

/** Запуск сценария: выбор времени события, предпросмотр шагов */
@Composable
fun ScenarioRunScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val profiles by app.db.profiles().all().collectAsState(initial = emptyList())
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var t by remember { mutableStateOf<ScenarioTemplate?>(null) }
    var event by remember { mutableStateOf(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)) }
    var profileId by remember { mutableLongStateOf(0L) }
    var catId by remember { mutableLongStateOf(0L) }
    var courseDays by remember { mutableIntStateOf(0) }
    var excluded by remember { mutableStateOf(setOf<Int>()) }
    var title by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        t = app.db.scenarios().get(id)
        t?.let { tt ->
            catId = tt.categoryId; title = tt.name
            if (tt.steps.all { it.repeat != RepeatType.ONCE }) event = LocalDateTime.now().withSecond(0).withNano(0).let { n ->
                if (tt.steps.any { it.repeat == RepeatType.DAILY }) n.plusMinutes(5) else n.plusDays(1).withHour(9).withMinute(0)
            }
            val pName = when (tt.group) { "Лекарства" -> "Лекарства"; "Работа и учёба" -> "Работа"; "Здоровье и анализы" -> "Встреча"; else -> "" }
            profiles.firstOrNull { it.name == pName }?.let { profileId = it.id }
        }
    }
    LaunchedEffect(profiles, t) { if (profileId == 0L && t != null) {
        val pName = when (t!!.group) { "Лекарства" -> "Лекарства"; "Работа и учёба" -> "Работа"; else -> "" }
        profiles.firstOrNull { it.name == pName }?.let { profileId = it.id }
    } }
    val tpl = t ?: return
    val eventMs = TimeUtil.ms(event)
    val now = System.currentTimeMillis()
    val hasRepeating = tpl.steps.any { it.repeat != RepeatType.ONCE }

    fun launchRun() = scope.launch {
        val prof = profiles.firstOrNull { it.id == profileId }
        val runId = app.db.scenarios().upsertRun(ScenarioRun(templateId = tpl.id, name = "${tpl.emoji} ${title.ifBlank { tpl.name }}", eventAt = eventMs))
        var count = 0
        tpl.steps.forEachIndexed { i, s ->
            if (i in excluded) return@forEachIndexed
            val at = eventMs + s.offsetMinutes * 60_000L
            if (s.repeat == RepeatType.ONCE && at <= now) return@forEachIndexed
            val alert = if (s.useCustomAlert) s.alert else prof?.alert ?: AlertSettings()
            val desc = buildString {
                append(s.text)
                append("\n\n📌 ${tpl.eventLabel}: ${TimeUtil.full(eventMs)} (${TimeUtil.describeOffset(s.offsetMinutes)})")
                if (tpl.disclaimer.isNotBlank()) append("\n⚠️ ").append(tpl.disclaimer)
            }.trim()
            val end = if (courseDays > 0 && s.repeat != RepeatType.ONCE) eventMs + courseDays * 86_400_000L else 0L
            val r = Reminder(title = s.title, description = desc, startAt = at, repeat = s.repeat, endAt = end,
                priority = if (s.priority != Priority.NORMAL) s.priority else prof?.priority ?: Priority.NORMAL,
                alert = alert, categoryId = catId, profileId = profileId, scenarioRunId = runId)
            val rid = app.db.reminders().upsert(r)
            app.db.reminders().get(rid)?.let { Scheduler.scheduleReminder(app, it) }
            count++
        }
        logHistory("Сценарий", tpl.name, "Запущен: $count напоминаний, событие ${TimeUtil.full(eventMs)}")
        android.widget.Toast.makeText(app, "Запланировано напоминаний: $count", android.widget.Toast.LENGTH_SHORT).show()
        nav.popBackStack()
    }

    EditScaffold("${tpl.emoji} ${tpl.name}", { nav.popBackStack() }, actions = {
        IconButton({ nav.navigate("scenario_edit/${tpl.id}") }) { Icon(Icons.Default.Edit, "Изменить шаблон") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (tpl.description.isNotBlank()) Text(tpl.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            if (tpl.disclaimer.isNotBlank()) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(12.dp)) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text(tpl.disclaimer, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Название (например, «Анализ крови — Инвитро»)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp))
            SectionHeader(tpl.eventLabel)
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton({ pickDate = true }, Modifier.weight(1f)) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(6.dp)); Text(TimeUtil.date(eventMs)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ pickTime = true }) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(TimeUtil.time(eventMs)) }
            }
            if (hasRepeating) Stepper("Длительность повторов", courseDays, 0..365, format = { if (it == 0) "бессрочно" else "$it дн" }) { courseDays = it }
            Dropdown("Профиль оповещений (для шагов без своих настроек)", listOf<Profile?>(null) + profiles, profiles.firstOrNull { it.id == profileId },
                { it?.let { p -> "${p.emoji} ${p.name}" } ?: "Стандартные настройки" }) { profileId = it?.id ?: 0 }
            CategoryPicker(cats, catId) { catId = it }

            SectionHeader("Шаги (${tpl.steps.size - excluded.size} из ${tpl.steps.size})")
            tpl.steps.forEachIndexed { i, s ->
                val at = eventMs + s.offsetMinutes * 60_000L
                val past = s.repeat == RepeatType.ONCE && at <= now
                val off = i in excluded
                Row(Modifier.fillMaxWidth().clickable { excluded = if (off) excluded - i else excluded + i }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top) {
                    Checkbox(!off && !past, { excluded = if (off) excluded - i else excluded + i }, enabled = !past)
                    Column(Modifier.weight(1f)) {
                        Text("${TimeUtil.full(at)}  (${TimeUtil.formatOffset(s.offsetMinutes)})", style = MaterialTheme.typography.labelMedium,
                            color = if (past) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            textDecoration = if (past || off) TextDecoration.LineThrough else null)
                        if (s.text.isNotBlank()) Text(s.text, style = MaterialTheme.typography.bodySmall)
                        FlowRow {
                            if (s.repeat != RepeatType.ONCE) Tag(s.repeat.title)
                            if (s.priority != Priority.NORMAL) Tag(s.priority.title)
                            if (s.useCustomAlert) Tag("свои настройки")
                            if (past) Tag("уже прошло — пропустится", MaterialTheme.colorScheme.errorContainer)
                        }
                    }
                }
            }
            Button({ launchRun() }, Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Запланировать") }
        }
    }
    if (pickDate) DatePickDialog(event.toLocalDate(), { pickDate = false }) { d -> event = d.atTime(event.toLocalTime()); pickDate = false }
    if (pickTime) TimePickDialog(event.hour, event.minute, { pickTime = false }) { h, m -> event = event.toLocalDate().atTime(h, m); pickTime = false }
}

/** Редактор шаблона сценария (свои сценарии) */
@Composable
fun ScenarioEditScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var t by remember { mutableStateOf<ScenarioTemplate?>(null) }
    LaunchedEffect(id) {
        t = if (id == 0L) ScenarioTemplate(name = "", emoji = "📋", group = "Мои сценарии", steps = listOf(
            ScenarioStep(-1440, "Завтра событие"), ScenarioStep(-60, "Через час событие"), ScenarioStep(0, "Событие")))
        else app.db.scenarios().get(id)
    }
    var confirmDel by remember { mutableStateOf(false) }
    val cur = t ?: return

    fun save(asCopy: Boolean) = scope.launch {
        if (cur.name.isBlank()) { android.widget.Toast.makeText(app, "Введите название", android.widget.Toast.LENGTH_SHORT).show(); return@launch }
        val toSave = cur.copy(steps = cur.steps.sortedBy { it.offsetMinutes }).let { if (asCopy) it.copy(id = 0, builtIn = false, name = it.name + if (id != 0L) " (моя версия)" else "") else it }
        app.db.scenarios().upsert(toSave)
        android.widget.Toast.makeText(app, "Шаблон сохранён", android.widget.Toast.LENGTH_SHORT).show()
        nav.popBackStack()
    }

    EditScaffold(if (id == 0L) "Новый сценарий" else "Шаблон сценария", { nav.popBackStack() }, actions = {
        if (id != 0L) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, "Удалить") }
        TextButton({ save(false) }) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(cur.emoji, { t = cur.copy(emoji = it.take(4)) }, label = { Text("Значок") }, singleLine = true, modifier = Modifier.width(90.dp))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(cur.name, { t = cur.copy(name = it) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(cur.group, { t = cur.copy(group = it) }, label = { Text("Группа") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(cur.description, { t = cur.copy(description = it) }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(cur.eventLabel, { t = cur.copy(eventLabel = it) }, label = { Text("Как называется событие («Время вылета»)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(cur.disclaimer, { t = cur.copy(disclaimer = it) }, label = { Text("Примечание / предупреждение") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            CategoryPicker(cats, cur.categoryId) { t = cur.copy(categoryId = it) }

            SectionHeader("Шаги")
            Text("Смещение относительно события: «-12 ч», «-1 день», «-1д 2ч», «+30 мин», «0» — в момент события.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            cur.steps.forEachIndexed { i, s ->
                StepEditor(i, s, onChange = { ns -> t = cur.copy(steps = cur.steps.toMutableList().also { it[i] = ns }) },
                    onDelete = { t = cur.copy(steps = cur.steps.toMutableList().also { it.removeAt(i) }) },
                    onUp = if (i > 0) ({ t = cur.copy(steps = cur.steps.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }) }) else null)
            }
            OutlinedButton({ t = cur.copy(steps = cur.steps + ScenarioStep(0, "Новый шаг")) }, Modifier.padding(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить шаг")
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                Button({ save(false) }, Modifier.weight(1f)) { Text("Сохранить") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ save(true) }, Modifier.weight(1f)) { Text("Сохранить как новый") }
            }
        }
    }
    if (confirmDel) ConfirmDialog("Удалить шаблон?", cur.name, onDismiss = { confirmDel = false }) { scope.launch { app.db.scenarios().delete(cur); nav.popBackStack() } }
}

@Composable
private fun StepEditor(i: Int, s: ScenarioStep, onChange: (ScenarioStep) -> Unit, onDelete: () -> Unit, onUp: (() -> Unit)?) {
    var offText by remember(s.offsetMinutes) { mutableStateOf(TimeUtil.formatOffset(s.offsetMinutes).replace("−", "-").let { if (s.offsetMinutes == 0L) "0" else it }) }
    val parsed = TimeUtil.parseOffset(offText)
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Шаг ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                onUp?.let { IconButton(it) { Icon(Icons.Default.ArrowUpward, "Выше") } }
                IconButton(onDelete) { Icon(Icons.Default.Delete, "Удалить шаг") }
            }
            OutlinedTextField(offText, { v -> offText = v; TimeUtil.parseOffset(v)?.let { onChange(s.copy(offsetMinutes = it)) } },
                label = { Text("Смещение") }, singleLine = true, isError = parsed == null,
                supportingText = { Text(if (parsed == null) "Не понял. Пример: -12 ч, -1 день, +30 мин" else TimeUtil.describeOffset(parsed)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            OutlinedTextField(s.title, { onChange(s.copy(title = it)) }, label = { Text("Заголовок") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp))
            OutlinedTextField(s.text, { onChange(s.copy(text = it)) }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp))
            Dropdown("Повтор шага", listOf(RepeatType.ONCE, RepeatType.DAILY, RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY), s.repeat, { it.title }) { onChange(s.copy(repeat = it)) }
            Dropdown("Приоритет", Priority.entries, s.priority, { it.title }) { onChange(s.copy(priority = it)) }
            SwitchRow("Свои настройки оповещения", s.useCustomAlert, "Иначе — профиль, выбранный при запуске") { onChange(s.copy(useCustomAlert = it)); if (it) expanded = true }
            if (s.useCustomAlert) {
                TextButton({ expanded = !expanded }, Modifier.padding(horizontal = 8.dp)) { Text(if (expanded) "Свернуть настройки" else "Показать настройки") }
                if (expanded) AlertSettingsEditor(s.alert, AlertMode.STEP) { onChange(s.copy(alert = it)) }
            }
        }
    }
}
