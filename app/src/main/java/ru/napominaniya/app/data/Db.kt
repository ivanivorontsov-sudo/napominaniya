package ru.napominaniya.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute") fun all(): Flow<List<Alarm>>
    @Query("SELECT * FROM alarms") suspend fun list(): List<Alarm>
    @Query("SELECT * FROM alarms WHERE id=:id") suspend fun get(id: Long): Alarm?
    @Upsert suspend fun upsert(a: Alarm): Long
    @Delete suspend fun delete(a: Alarm)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY completed, nextTriggerAt") fun all(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders") suspend fun list(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE id=:id") suspend fun get(id: Long): Reminder?
    @Query("SELECT * FROM reminders WHERE scenarioRunId=:runId") suspend fun byRun(runId: Long): List<Reminder>
    @Upsert suspend fun upsert(r: Reminder): Long
    @Delete suspend fun delete(r: Reminder)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC") fun all(): Flow<List<Note>>
    @Query("SELECT * FROM notes") suspend fun list(): List<Note>
    @Query("SELECT * FROM notes WHERE id=:id") suspend fun get(id: Long): Note?
    @Upsert suspend fun upsert(n: Note): Long
    @Delete suspend fun delete(n: Note)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 1000") fun all(): Flow<List<HistoryEntry>>
    @Insert suspend fun insert(h: HistoryEntry): Long
    @Query("DELETE FROM history") suspend fun clear()
    @Query("DELETE FROM history WHERE time < :before") suspend fun trim(before: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name") fun all(): Flow<List<Profile>>
    @Query("SELECT * FROM profiles") suspend fun list(): List<Profile>
    @Query("SELECT * FROM profiles WHERE id=:id") suspend fun get(id: Long): Profile?
    @Upsert suspend fun upsert(p: Profile): Long
    @Delete suspend fun delete(p: Profile)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name") fun all(): Flow<List<Category>>
    @Query("SELECT * FROM categories") suspend fun list(): List<Category>
    @Upsert suspend fun upsert(c: Category): Long
    @Delete suspend fun delete(c: Category)
}

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY `group`, name") fun all(): Flow<List<ScenarioTemplate>>
    @Query("SELECT * FROM scenarios WHERE id=:id") suspend fun get(id: Long): ScenarioTemplate?
    @Query("SELECT name FROM scenarios") suspend fun names(): List<String>
    @Upsert suspend fun upsert(s: ScenarioTemplate): Long
    @Delete suspend fun delete(s: ScenarioTemplate)
    @Query("SELECT * FROM scenario_runs ORDER BY eventAt DESC") fun runs(): Flow<List<ScenarioRun>>
    @Upsert suspend fun upsertRun(r: ScenarioRun): Long
    @Delete suspend fun deleteRun(r: ScenarioRun)
}

@Database(
    entities = [Alarm::class, Reminder::class, Note::class, HistoryEntry::class, Profile::class,
        Category::class, ScenarioTemplate::class, ScenarioRun::class],
    version = 1, exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun reminders(): ReminderDao
    abstract fun notes(): NoteDao
    abstract fun history(): HistoryDao
    abstract fun profiles(): ProfileDao
    abstract fun categories(): CategoryDao
    abstract fun scenarios(): ScenarioDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "napominaniya.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}

enum class QuietMode(val title: String) { SILENT("Показывать без звука"), DEFER("Отложить до конца тихих часов") }
enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

/** Глобальные настройки (SharedPreferences) */
class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value++ }

    var defaultSnooze: Int get() = p.getInt("snooze", 10); set(v) { p.edit().putInt("snooze", v).apply(); bump() }
    var defaultPreReminder: Int get() = p.getInt("pre", 0); set(v) { p.edit().putInt("pre", v).apply(); bump() }
    var quietEnabled: Boolean get() = p.getBoolean("q_on", false); set(v) { p.edit().putBoolean("q_on", v).apply(); bump() }
    var quietStart: Int get() = p.getInt("q_start", 23 * 60); set(v) { p.edit().putInt("q_start", v).apply(); bump() }
    var quietEnd: Int get() = p.getInt("q_end", 7 * 60); set(v) { p.edit().putInt("q_end", v).apply(); bump() }
    var quietDays: Int get() = p.getInt("q_days", 0x7F); set(v) { p.edit().putInt("q_days", v).apply(); bump() }
    var quietMode: QuietMode get() = runCatching { QuietMode.valueOf(p.getString("q_mode", "SILENT")!!) }.getOrDefault(QuietMode.SILENT); set(v) { p.edit().putString("q_mode", v.name).apply(); bump() }
    var quietAllowCritical: Boolean get() = p.getBoolean("q_crit", true); set(v) { p.edit().putBoolean("q_crit", v).apply(); bump() }
    var weekendDays: Int get() = p.getInt("weekend", (1 shl 5) or (1 shl 6)); set(v) { p.edit().putInt("weekend", v).apply(); bump() }
    var ruHolidays: Boolean get() = p.getBoolean("ru_hol", true); set(v) { p.edit().putBoolean("ru_hol", v).apply(); bump() }
    var customHolidays: Set<String> get() = p.getStringSet("hol", emptySet()) ?: emptySet(); set(v) { p.edit().putStringSet("hol", v).apply(); bump() }
    var repinOnSwipe: Boolean get() = p.getBoolean("repin", true); set(v) { p.edit().putBoolean("repin", v).apply(); bump() }
    var theme: ThemeMode get() = runCatching { ThemeMode.valueOf(p.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM); set(v) { p.edit().putString("theme", v.name).apply(); bump() }
    var dynamicColor: Boolean get() = p.getBoolean("dyn", true); set(v) { p.edit().putBoolean("dyn", v).apply(); bump() }
    var historyDays: Int get() = p.getInt("hist_days", 30); set(v) { p.edit().putInt("hist_days", v).apply(); bump() }
    var missedOnBoot: Boolean get() = p.getBoolean("missed", true); set(v) { p.edit().putBoolean("missed", v).apply(); bump() }
    var seeded: Boolean get() = p.getBoolean("seeded", false); set(v) { p.edit().putBoolean("seeded", v).apply() }
    var onboarded: Boolean get() = p.getBoolean("onboarded", false); set(v) { p.edit().putBoolean("onboarded", v).apply(); bump() }
    var lowStockNotified: Set<String> get() = p.getStringSet("lowstock", emptySet()) ?: emptySet(); set(v) { p.edit().putStringSet("lowstock", v).apply() }
}
