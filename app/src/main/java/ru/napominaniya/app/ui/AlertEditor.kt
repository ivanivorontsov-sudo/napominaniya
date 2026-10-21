package ru.napominaniya.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.napominaniya.app.core.Notifier
import ru.napominaniya.app.data.*

enum class AlertMode { ALARM, REMINDER, STEP }

/** Полный редактор детальных настроек оповещения */
@Composable
fun AlertSettingsEditor(a: AlertSettings, mode: AlertMode, onChange: (AlertSettings) -> Unit) {
    val ctx = LocalContext.current
    val ring = mode == AlertMode.ALARM || a.ringMode
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val def = RingtoneManager.getDefaultUri(if (ring) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            onChange(a.copy(soundUri = when { uri == null -> ""; uri == def || uri.toString().startsWith("content://settings/system/") -> null; else -> uri.toString() }))
        }
    }
    var advanced by remember { mutableStateOf(false) }

    SectionHeader("Звук и вибрация")
    SettingRow("Мелодия", Notifier.soundTitle(ctx, a.soundUri), Icons.Default.MusicNote, onClick = {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (ring) RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите мелодию")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, when (a.soundUri) { null -> RingtoneManager.getDefaultUri(if (ring) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION); "" -> null; else -> Uri.parse(a.soundUri) })
        }
        runCatching { picker.launch(i) }
    })
    if (mode != AlertMode.ALARM) {
        SwitchRow("Режим «звонок»", a.ringMode, "Полноэкранный звонок, как у будильника (громкость, нарастание, задания)", Icons.Default.Alarm) { onChange(a.copy(ringMode = it)) }
    }
    if (ring) {
        SettingRow("Громкость: ${a.volume}%", "Громкость потока «Будильник» на время звонка")
        Slider(a.volume.toFloat(), { onChange(a.copy(volume = it.toInt())) }, valueRange = 5f..100f, modifier = Modifier.padding(horizontal = 16.dp))
        SettingRow("Плавное нарастание: " + if (a.gradualSeconds == 0) "выкл" else "${a.gradualSeconds} с", "Громкость растёт от тихой до заданной")
        Slider(a.gradualSeconds.toFloat(), { onChange(a.copy(gradualSeconds = (it / 5).toInt() * 5)) }, valueRange = 0f..180f, modifier = Modifier.padding(horizontal = 16.dp))
    }
    Dropdown("Вибрация", VibrationKind.entries, a.vibration, { it.title }) { onChange(a.copy(vibration = it)) }
    if (a.vibration == VibrationKind.CUSTOM) {
        OutlinedTextField(a.customPattern, { onChange(a.copy(customPattern = it)) }, label = { Text("Шаблон, мс: пауза,вибро,пауза,вибро…") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
    if (a.vibration != VibrationKind.NONE) TextButton({ Notifier.vibrateOnce(ctx, a.vibrationPattern()) }, Modifier.padding(horizontal = 8.dp)) {
        Icon(Icons.Default.Vibration, null); Spacer(Modifier.width(6.dp)); Text("Проверить вибрацию")
    }

    SectionHeader("Откладывание и повторы")
    Stepper("Отложить на", a.snoozeMinutes, 1..120, format = { "$it мин" }) { onChange(a.copy(snoozeMinutes = it)) }
    Stepper("Лимит откладываний", a.maxSnoozes, 0..20, format = { if (it == 0) "∞" else "$it раз" }) { onChange(a.copy(maxSnoozes = it)) }
    Stepper("Автоотключение через", a.autoDismissMinutes, 0..120, format = { if (it == 0) "никогда" else "$it мин" },
        subtitle = if (ring) "Звонок прекратится сам, если нет реакции" else "Уведомление само исчезнет") { onChange(a.copy(autoDismissMinutes = it)) }
    if (mode != AlertMode.ALARM) {
        Stepper("Повторять, пока не подтвержу", a.nagEveryMinutes, 0..120, format = { if (it == 0) "выкл" else "каждые $it мин" }) { onChange(a.copy(nagEveryMinutes = it)) }
        if (a.nagEveryMinutes > 0) Stepper("Максимум повторов", a.nagMaxTimes, 1..50) { onChange(a.copy(nagMaxTimes = it)) }
    }

    SectionHeader("Отключение будильника")
    Dropdown("Задание для отключения", Challenge.entries, a.challenge, { it.title }) { onChange(a.copy(challenge = it)) }
    when (a.challenge) {
        Challenge.MATH -> {
            Stepper("Сложность", a.mathDifficulty, 1..3, format = { listOf("", "лёгкая", "средняя", "сложная")[it] }) { onChange(a.copy(mathDifficulty = it)) }
            Stepper("Количество примеров", a.mathCount, 1..10) { onChange(a.copy(mathCount = it)) }
        }
        Challenge.SHAKE -> Stepper("Сколько раз потрясти", a.shakeCount, 5..100, 5) { onChange(a.copy(shakeCount = it)) }
        Challenge.TYPE_TEXT -> OutlinedTextField(a.challengePhrase, { onChange(a.copy(challengePhrase = it)) }, label = { Text("Фраза для ввода") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Challenge.NONE -> {}
    }
    if (a.challenge != Challenge.NONE && !ring) Text("Задание применяется только в режиме «звонок».",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))

    SectionHeader("Особые ситуации")
    Dropdown("Если телефон в беззвучном режиме", SilentBehavior.entries, a.silentBehavior, { it.title }) { onChange(a.copy(silentBehavior = it)) }
    if (mode != AlertMode.ALARM) SwitchRow("Игнорировать тихие часы", a.ignoreQuietHours, "Звучать даже в тихие часы", Icons.Default.DoNotDisturbOff) { onChange(a.copy(ignoreQuietHours = it)) }

    SettingRow(if (advanced) "Скрыть расширенные" else "Расширенные: канал, важность, индикатор…", icon = Icons.Default.Tune, onClick = { advanced = !advanced }) {
        Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
    }
    if (advanced) {
        if (mode != AlertMode.ALARM) {
            Dropdown("Важность (канал уведомлений)", Importance.entries, a.importance, { it.title }) { onChange(a.copy(importance = it)) }
            Text("Для каждой комбинации «важность + звук + вибрация + индикатор» создаётся свой канал уведомлений Android — его можно тонко настроить в системных настройках.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            SwitchRow("Нельзя смахнуть", a.persistent, "Уведомление остаётся, пока не нажмёте «Готово»", Icons.Default.PushPin) { onChange(a.copy(persistent = it)) }
        }
        SwitchRow("Показывать на экране блокировки", a.showOnLockScreen, icon = Icons.Default.Lock) { onChange(a.copy(showOnLockScreen = it)) }
        if (ring) SwitchRow("Мигание экрана", a.flashScreen, "Экран звонка мигает цветом", Icons.Default.FlashOn) { onChange(a.copy(flashScreen = it)) }
        SettingRow("Цвет светодиода", if (a.ledColor == 0) "Выкл (если есть на устройстве)" else "Включён", Icons.Default.Lightbulb)
        ColorPickerRow(a.ledColor) { onChange(a.copy(ledColor = it)) }
    }
}
