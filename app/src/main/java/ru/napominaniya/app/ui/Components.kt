@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.napominaniya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ru.napominaniya.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val palette = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
    0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
    0xFF6D4C41, 0xFF757575, 0xFF546E7A
).map { it.toInt() }

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null,
               trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, icon, onClick = { onChange(!checked) }) { Switch(checked, onChange) }
}

@Composable
fun Stepper(title: String, value: Int, range: IntRange, step: Int = 1, subtitle: String? = null,
            format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    SettingRow(title, subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, "Меньше") }
            Text(format(value), modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "Больше") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T, titleOf: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = titleOf(selected), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(titleOf(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { t -> text = t.filter { it.isDigit() }.take(6); text.toIntOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
fun DaysSelector(mask: Int, onChange: (Int) -> Unit) {
    // Равные доли ширины — 7 кружков помещаются на любом экране
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Days.short.forEachIndexed { i, d ->
            val on = mask and (1 shl i) != 0
            Box(
                Modifier.weight(1f).widthIn(max = 48.dp).aspectRatio(1f).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center
            ) { Text(d, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ColorPickerRow(selected: Int, allowNone: Boolean = true, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (allowNone) {
            Box(Modifier.padding(4.dp).size(34.dp).clip(CircleShape).border(2.dp, if (selected == 0) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                .clickable { onSelect(0) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Block, "Нет", Modifier.size(18.dp)) }
        }
        palette.forEach { c ->
            Box(Modifier.padding(4.dp).size(34.dp).clip(CircleShape).background(Color(c))
                .border(3.dp, if (c == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .clickable { onSelect(c) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val st = rememberTimePickerState(hour, minute, is24Hour = true)
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(st.hour, st.minute) }) { Text("ОК") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        text = { TimePicker(st) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({ st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }) { Text("ОК") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } }) { DatePicker(st) }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Удалить", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}

@Composable
fun ColorDot(color: Int, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (color == 0) Color.Gray else Color(color)))
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, onColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = onColor,
        modifier = Modifier.padding(end = 4.dp, top = 2.dp).clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** Список дат-исключений */
@Composable
fun SkipDatesEditor(title: String, dates: List<String>, onChange: (List<String>) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    SettingRow(title, if (dates.isEmpty()) "Нет" else "${dates.size} шт.", Icons.Default.EventBusy, onClick = { pick = true }) {
        Icon(Icons.Default.Add, "Добавить")
    }
    if (dates.isNotEmpty()) FlowRowChips(dates.sorted()) { d -> onChange(dates - d) }
    if (pick) DatePickDialog(LocalDate.now(), { pick = false }) { d -> pick = false; if (d.toString() !in dates) onChange(dates + d.toString()) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowChips(items: List<String>, onRemove: (String) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp)) {
        items.forEach { d ->
            InputChip(selected = false, onClick = { onRemove(d) }, label = {
                Text(runCatching { LocalDate.parse(d).let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) } }.getOrDefault(d))
            }, trailingIcon = { Icon(Icons.Default.Close, "Убрать", Modifier.size(16.dp)) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<Category>, selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilterChip(selected = selected == 0L, onClick = { onSelect(0) }, label = { Text("Без категории") }, modifier = Modifier.padding(end = 6.dp))
        categories.forEach { c ->
            FilterChip(selected = selected == c.id, onClick = { onSelect(c.id) }, label = { Text(c.name) },
                leadingIcon = { ColorDot(c.color) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScaffold(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }, actions = actions)
    }, content = content)
}
