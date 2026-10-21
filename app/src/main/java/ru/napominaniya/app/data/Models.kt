package ru.napominaniya.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Как вести себя, если телефон в беззвучном режиме / вибро */
enum class SilentBehavior(val title: String) {
    RESPECT("Как в системе (без звука в беззвучном)"),
    FORCE_SOUND("Всё равно звонить"),
    VIBRATE_ONLY("Только вибрация"),
    SILENT_NOTIFY("Только тихое уведомление")
}

enum class VibrationKind(val title: String, val pattern: LongArray) {
    NONE("Без вибрации", longArrayOf(0)),
    SHORT("Короткая", longArrayOf(0, 250, 250, 250)),
    NORMAL("Обычная", longArrayOf(0, 600, 400, 600)),
    LONG("Длинная", longArrayOf(0, 1500, 500, 1500)),
    HEARTBEAT("Пульс", longArrayOf(0, 120, 100, 120, 700)),
    SOS("SOS", longArrayOf(0, 150, 100, 150, 100, 150, 300, 450, 150, 450, 150, 450, 300, 150, 100, 150, 100, 150, 800)),
    RAPID("Частая", longArrayOf(0, 100, 80, 100, 80, 100, 80, 100, 80, 100)),
    CUSTOM("Своя", longArrayOf(0, 500))
}

enum class Challenge(val title: String) {
    NONE("Нет"), MATH("Решить пример"), SHAKE("Потрясти телефон"), TYPE_TEXT("Ввести фразу")
}

enum class Importance(val title: String, val level: Int) {
    URGENT("Срочная (всплывает, полный экран)", 4),
    HIGH("Высокая (всплывающая)", 4),
    DEFAULT("Обычная", 3),
    LOW("Низкая (без звука)", 2),
    MIN("Минимальная (свёрнута)", 1)
}

/** Детальные настройки оповещения — используются в будильниках, напоминаниях, профилях и шагах сценариев */
@Serializable
data class AlertSettings(
    val soundUri: String? = null,          // null — звук по умолчанию; "" — без звука
    val volume: Int = 80,                  // 0..100 (для режима «звонок»)
    val gradualSeconds: Int = 0,           // плавное нарастание громкости, 0 — выкл
    val vibration: VibrationKind = VibrationKind.NORMAL,
    val customPattern: String = "0,500,300,500",
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 0,               // 0 — без ограничений
    val autoDismissMinutes: Int = 0,       // 0 — никогда
    val importance: Importance = Importance.HIGH,
    val ledColor: Int = 0,                 // 0 — выкл, иначе ARGB
    val persistent: Boolean = false,       // нельзя смахнуть (ongoing)
    val nagEveryMinutes: Int = 0,          // повторять, пока не подтверждено
    val nagMaxTimes: Int = 5,
    val ringMode: Boolean = false,         // полноэкранный «звонок» как будильник
    val challenge: Challenge = Challenge.NONE,
    val mathDifficulty: Int = 1,           // 1..3
    val mathCount: Int = 1,
    val shakeCount: Int = 20,
    val challengePhrase: String = "Я проснулся",
    val silentBehavior: SilentBehavior = SilentBehavior.RESPECT,
    val ignoreQuietHours: Boolean = false,
    val showOnLockScreen: Boolean = true,
    val flashScreen: Boolean = false
) {
    fun vibrationPattern(): LongArray =
        if (vibration == VibrationKind.CUSTOM)
            customPattern.split(',', ' ', ';').mapNotNull { it.trim().toLongOrNull() }.ifEmpty { listOf(0L, 500L) }.toLongArray()
        else vibration.pattern
}

enum class RepeatType(val title: String) {
    ONCE("Однократно"), DAILY("Каждый день"), WEEKLY("По дням недели"), MONTHLY("Каждый месяц"),
    YEARLY("Каждый год"), INTERVAL("Свой интервал")
}

enum class Priority(val title: String, val color: Long) {
    LOW("Низкий", 0xFF78909C), NORMAL("Обычный", 0xFF42A5F5), HIGH("Высокий", 0xFFFFA726), CRITICAL("Критический", 0xFFE53935)
}

@Serializable
data class ChecklistItem(val text: String, val done: Boolean = false)

@Serializable
data class ScenarioStep(
    val offsetMinutes: Long,           // относительно времени события (может быть отрицательным)
    val title: String,
    val text: String = "",
    val repeat: RepeatType = RepeatType.ONCE,
    val useCustomAlert: Boolean = false,
    val alert: AlertSettings = AlertSettings(),
    val priority: Priority = Priority.NORMAL
)

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Int = 0,                  // битовая маска: бит 0 = Пн ... бит 6 = Вс; 0 — однократно
    val label: String = "",
    val enabled: Boolean = true,
    val alert: AlertSettings = AlertSettings(ringMode = true, gradualSeconds = 30),
    val weekendTimeEnabled: Boolean = false,
    val weekendHour: Int = 9,
    val weekendMinute: Int = 0,
    val skipHolidays: Boolean = false,
    val skipDates: List<String> = emptyList(),   // yyyy-MM-dd
    val skipNextAt: Long = 0,               // пропустить ближайшее срабатывание с этим временем
    val categoryId: Long = 0,
    val profileId: Long = 0,
    val nextTriggerAt: Long = 0,
    val snoozedUntil: Long = 0,
    val snoozeCount: Int = 0
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val startAt: Long = 0,               // первое срабатывание (дата+время)
    val repeat: RepeatType = RepeatType.ONCE,
    val weekDays: Int = 0,               // для WEEKLY
    val intervalMinutes: Long = 60,      // для INTERVAL
    val endAt: Long = 0,                 // повторять до (0 — бессрочно)
    val priority: Priority = Priority.NORMAL,
    val preReminders: List<Int> = emptyList(),  // минут до
    val alert: AlertSettings = AlertSettings(),
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val categoryId: Long = 0,
    val profileId: Long = 0,
    val skipHolidays: Boolean = false,
    val skipWeekends: Boolean = false,
    val skipDates: List<String> = emptyList(),
    val nextTriggerAt: Long = 0,
    val snoozedUntil: Long = 0,
    val snoozeCount: Int = 0,
    val nagCount: Int = 0,
    val awaitingAck: Boolean = false,
    val scenarioRunId: Long = 0,
    // учёт запаса лекарств
    val stockEnabled: Boolean = false,
    val stockCount: Int = 0,
    val stockPerDose: Int = 1,
    val stockThreshold: Int = 5,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val pinned: Boolean = false,
    val color: Int = 0,
    val categoryId: Long = 0,
    val silentUpdates: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val kind: String = "",       // Будильник / Напоминание / Заметка / Сценарий / Система
    val title: String = "",
    val text: String = "",
    val action: String = ""      // Показано / Выполнено / Отложено / Выключено / Пропущено
)

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val emoji: String = "🔔",
    val color: Int = 0xFF42A5F5.toInt(),
    val alert: AlertSettings = AlertSettings(),
    val priority: Priority = Priority.NORMAL,
    val preReminders: List<Int> = emptyList(),
    val categoryId: Long = 0,
    val description: String = ""
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val color: Int = 0xFF42A5F5.toInt()
)

@Entity(tableName = "scenarios")
data class ScenarioTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val emoji: String = "📋",
    val description: String = "",
    val eventLabel: String = "Время события",
    val disclaimer: String = "",
    val steps: List<ScenarioStep> = emptyList(),
    val builtIn: Boolean = false,
    val categoryId: Long = 0,
    val group: String = "Прочее"
)

@Entity(tableName = "scenario_runs")
data class ScenarioRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long = 0,
    val name: String = "",
    val eventAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter fun alertTo(v: AlertSettings): String = AppJson.encodeToString(v)
    @TypeConverter fun alertFrom(s: String): AlertSettings = runCatching { AppJson.decodeFromString<AlertSettings>(s) }.getOrDefault(AlertSettings())
    @TypeConverter fun strListTo(v: List<String>): String = AppJson.encodeToString(v)
    @TypeConverter fun strListFrom(s: String): List<String> = runCatching { AppJson.decodeFromString<List<String>>(s) }.getOrDefault(emptyList())
    @TypeConverter fun intListTo(v: List<Int>): String = AppJson.encodeToString(v)
    @TypeConverter fun intListFrom(s: String): List<Int> = runCatching { AppJson.decodeFromString<List<Int>>(s) }.getOrDefault(emptyList())
    @TypeConverter fun checkTo(v: List<ChecklistItem>): String = AppJson.encodeToString(v)
    @TypeConverter fun checkFrom(s: String): List<ChecklistItem> = runCatching { AppJson.decodeFromString<List<ChecklistItem>>(s) }.getOrDefault(emptyList())
    @TypeConverter fun stepsTo(v: List<ScenarioStep>): String = AppJson.encodeToString(v)
    @TypeConverter fun stepsFrom(s: String): List<ScenarioStep> = runCatching { AppJson.decodeFromString<List<ScenarioStep>>(s) }.getOrDefault(emptyList())
    @TypeConverter fun repeatTo(v: RepeatType): String = v.name
    @TypeConverter fun repeatFrom(s: String): RepeatType = runCatching { RepeatType.valueOf(s) }.getOrDefault(RepeatType.ONCE)
    @TypeConverter fun prioTo(v: Priority): String = v.name
    @TypeConverter fun prioFrom(s: String): Priority = runCatching { Priority.valueOf(s) }.getOrDefault(Priority.NORMAL)
}
