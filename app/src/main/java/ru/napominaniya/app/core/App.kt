package ru.napominaniya.app.core

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.napominaniya.app.data.*

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db by lazy { AppDb.get(this) }
    val settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifier.ensureBaseChannels(this)
        scope.launch {
            seedIfNeeded()
            Scheduler.rescheduleAll(this@App, reportMissed = false)
            db.history().trim(System.currentTimeMillis() - settings.historyDays * 86_400_000L)
        }
    }

    suspend fun seedIfNeeded() {
        if (settings.seeded) return
        val catIds = Defaults.categories.associate { it.name to db.categories().upsert(it) }
        Defaults.profiles().forEach { p ->
            val cat = when (p.name) { "Работа", "Встреча" -> catIds["Работа"]; "Лекарства" -> catIds["Здоровье"]; else -> null } ?: 0
            db.profiles().upsert(p.copy(categoryId = cat))
        }
        Defaults.scenarios().forEach { s ->
            val cat = when {
                s.group.startsWith("Здоровье") || s.group == "Лекарства" || s.group == "Питание" -> catIds["Здоровье"]
                s.group == "Поездки" -> catIds["Поездки"]
                s.group.startsWith("Работа") -> catIds["Работа"]
                s.group.startsWith("Финансы") -> catIds["Финансы"]
                else -> catIds["Личное"]
            } ?: 0
            db.scenarios().upsert(s.copy(categoryId = cat))
        }
        settings.seeded = true
    }

    /** Восстановить встроенные шаблоны сценариев (добавить отсутствующие) */
    suspend fun restoreBuiltInScenarios() {
        val existing = db.scenarios().names().toSet()
        Defaults.scenarios().filter { it.name !in existing }.forEach { db.scenarios().upsert(it) }
    }

    fun rules() = Rules(settings.weekendDays, settings.ruHolidays, settings.customHolidays)

    companion object {
        lateinit var instance: App
            private set
    }
}
