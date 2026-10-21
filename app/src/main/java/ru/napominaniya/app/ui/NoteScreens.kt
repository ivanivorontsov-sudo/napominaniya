@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.napominaniya.app.core.*
import ru.napominaniya.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(nav: NavHostController) {
    val app = App.instance
    val notes by app.db.notes().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Живые заметки") }) },
        floatingActionButton = { FloatingActionButton({ nav.navigate("note/0") }) { Icon(Icons.Default.Add, "Новая заметка") } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text("Закреплённые заметки висят в шторке уведомлений и обновляются на лету. Пункты списка можно отмечать и добавлять прямо из уведомления.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            if (notes.isEmpty()) item { EmptyState(Icons.Default.StickyNote2, "Заметок нет", "Например: список покупок, номер парковки, план на день") }
            items(notes, key = { it.id }) { n ->
                val done = n.checklist.count { it.done }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { nav.navigate("note/${n.id}") },
                    colors = if (n.color != 0) CardDefaults.cardColors(containerColor = Color(n.color).copy(alpha = 0.22f)) else CardDefaults.cardColors()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium)
                            if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (n.checklist.isNotEmpty()) {
                                LinearProgressIndicator(progress = { done / n.checklist.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                                Text("Выполнено $done из ${n.checklist.size}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Изменено: ${TimeUtil.full(n.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconToggleButton(n.pinned, { on ->
                            scope.launch {
                                val nn = n.copy(pinned = on)
                                app.db.notes().upsert(nn)
                                if (on) Notifier.showNote(app, nn) else Notifier.cancelNote(app, n.id)
                                logHistory("Заметка", n.title, if (on) "Закреплена в шторке" else "Откреплена")
                            }
                        }) { Icon(if (n.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin, "Закрепить", tint = if (n.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditScreen(nav: NavHostController, id: Long) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val cats by app.db.categories().all().collectAsState(initial = emptyList())
    var n by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(id) { n = if (id == 0L) Note(pinned = true) else app.db.notes().get(id) }
    var newItem by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    val cur = n ?: return

    fun save(back: Boolean = true) = scope.launch {
        val toSave = cur.copy(updatedAt = System.currentTimeMillis())
        val newId = app.db.notes().upsert(toSave)
        val saved = app.db.notes().get(if (cur.id == 0L) newId else cur.id) ?: return@launch
        n = saved
        if (saved.pinned) Notifier.showNote(app, saved) else Notifier.cancelNote(app, saved.id)
        if (back) nav.popBackStack()
    }

    EditScaffold(if (id == 0L) "Новая заметка" else "Заметка", { nav.popBackStack() }, actions = {
        if (id != 0L) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, "Удалить") }
        TextButton({ save() }) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            OutlinedTextField(cur.title, { n = cur.copy(title = it) }, label = { Text("Заголовок") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(cur.text, { n = cur.copy(text = it) }, label = { Text("Текст") }, minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            SwitchRow("Закрепить в шторке", cur.pinned, "Постоянное уведомление, обновляется при каждом изменении", Icons.Default.PushPin) { n = cur.copy(pinned = it) }
            SwitchRow("Обновлять без звука", cur.silentUpdates, icon = Icons.Default.VolumeOff) { n = cur.copy(silentUpdates = it) }

            SectionHeader("Список дел (${cur.checklist.count { it.done }}/${cur.checklist.size})")
            cur.checklist.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.done, { d -> n = cur.copy(checklist = cur.checklist.toMutableList().also { it[i] = item.copy(done = d) }) })
                    var text by remember(item.text, i) { mutableStateOf(item.text) }
                    OutlinedTextField(text, { v -> text = v; n = cur.copy(checklist = cur.checklist.toMutableList().also { it[i] = item.copy(text = v) }) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null))
                    if (i > 0) IconButton({ n = cur.copy(checklist = cur.checklist.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }) }) { Icon(Icons.Default.ArrowUpward, "Выше") }
                    IconButton({ n = cur.copy(checklist = cur.checklist.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Close, "Удалить пункт") }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newItem, { newItem = it }, label = { Text("Новый пункт") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (newItem.isNotBlank()) { n = cur.copy(checklist = cur.checklist + ChecklistItem(newItem.trim())); newItem = "" } }))
                IconButton({ if (newItem.isNotBlank()) { n = cur.copy(checklist = cur.checklist + ChecklistItem(newItem.trim())); newItem = "" } }) { Icon(Icons.Default.AddCircle, "Добавить") }
            }
            if (cur.checklist.any { it.done }) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton({ n = cur.copy(checklist = cur.checklist.filter { !it.done }) }) { Text("Убрать выполненные") }
                TextButton({ n = cur.copy(checklist = cur.checklist.map { it.copy(done = false) }) }) { Text("Снять все отметки") }
            }

            SectionHeader("Цвет и категория")
            ColorPickerRow(cur.color) { n = cur.copy(color = it) }
            CategoryPicker(cats, cur.categoryId) { n = cur.copy(categoryId = it) }
        }
    }
    if (confirmDel) ConfirmDialog("Удалить заметку?", cur.title, onDismiss = { confirmDel = false }) {
        scope.launch { Notifier.cancelNote(app, cur.id); app.db.notes().delete(cur); nav.popBackStack() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val app = App.instance
    val items by app.db.history().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val kinds = items.map { it.kind }.distinct()
    val list = items.filter { kind == null || it.kind == kind }
    Scaffold(topBar = {
        TopAppBar(title = { Text("История") }, actions = { IconButton({ confirm = true }) { Icon(Icons.Default.DeleteSweep, "Очистить") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    FilterChip(kind == null, { kind = null }, { Text("Все") }, Modifier.padding(4.dp))
                    kinds.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k) }, Modifier.padding(4.dp)) }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.History, "История пуста", "Здесь появятся все сработавшие уведомления и действия с ними") }
            var lastDay = ""
            list.forEach { h ->
                val day = TimeUtil.date(h.time)
                if (day != lastDay) { lastDay = day; item(key = "d$day${h.id}") { SectionHeader(day) } }
                item(key = h.id) {
                    ListItem(
                        leadingContent = {
                            Icon(when (h.kind) {
                                "Будильник" -> Icons.Default.Alarm; "Напоминание" -> Icons.Default.Notifications; "Заметка" -> Icons.Default.StickyNote2
                                "Сценарий" -> Icons.Default.AccountTree; "Лекарства" -> Icons.Default.Medication; else -> Icons.Default.Info
                            }, null)
                        },
                        headlineContent = { Text(h.title.ifBlank { h.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(h.action + if (h.text.isNotBlank()) "\n" + h.text.take(120) else "", maxLines = 3) },
                        trailingContent = { Text(TimeUtil.time(h.time), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
    if (confirm) ConfirmDialog("Очистить историю?", "Все записи будут удалены", "Очистить", { confirm = false }) { scope.launch { app.db.history().clear() } }
}
