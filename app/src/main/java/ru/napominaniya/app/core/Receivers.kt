package ru.napominaniya.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.napominaniya.app.data.*

fun BroadcastReceiver.goAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } catch (t: Throwable) { android.util.Log.e("Napom", "receiver", t) } finally { pending.finish() }
    }
}

/** Срабатывание запланированных событий */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val type = intent.getIntExtra(Scheduler.EX_TYPE, 0)
        val id = intent.getLongExtra(Scheduler.EX_ID, 0)
        val slot = intent.getIntExtra(Scheduler.EX_SLOT, 0)
        val at = intent.getLongExtra(Scheduler.EX_AT, 0)
        goAsync {
            when (type) {
                Scheduler.T_ALARM -> Engine.fireAlarm(ctx, id, slot, at)
                Scheduler.T_REMINDER -> Engine.fireReminder(ctx, id, slot, at, intent.getIntExtra(Scheduler.EX_PRE, 0))
            }
        }
    }
}

object Engine {
    suspend fun fireAlarm(ctx: Context, id: Long, slot: Int, at: Long) {
        val app = App.instance
        var a = app.db.alarms().get(id) ?: return
        if (!a.enabled) return
        val isSnooze = slot == Scheduler.SLOT_REFIRE
        a = a.copy(snoozedUntil = 0, skipNextAt = if (a.skipNextAt != 0L && a.skipNextAt <= System.currentTimeMillis()) 0 else a.skipNextAt)
        if (!isSnooze) a = a.copy(snoozeCount = 0)
        if (a.days == 0 && !isSnooze) a = a.copy(enabled = true) // выключим после отключения звонка
        app.db.alarms().upsert(a)
        Scheduler.scheduleAlarm(ctx, a, after = maxOf(at, System.currentTimeMillis()) + 1000)
        val silent = Notifier.ringerSilent(ctx)
        if (silent && a.alert.silentBehavior == SilentBehavior.SILENT_NOTIFY) {
            Notifier.showSystem(ctx, 1000 + id.toInt(), "⏰ ${a.label.ifBlank { "Будильник" }}", "Будильник ${TimeUtil.hm(a.hour, a.minute)} (тихий режим: без звонка)", "alarms")
            logHistory("Будильник", a.label.ifBlank { "Будильник ${TimeUtil.hm(a.hour, a.minute)}" }, "Показано без звонка (беззвучный режим)")
            if (a.days == 0) app.db.alarms().upsert(a.copy(enabled = false))
            return
        }
        RingService.start(ctx, RingService.KIND_ALARM, id, isSnooze)
        logHistory("Будильник", a.label.ifBlank { "Будильник ${TimeUtil.hm(a.hour, a.minute)}" }, if (isSnooze) "Звонок после откладывания" else "Звонок")
    }

    suspend fun fireReminder(ctx: Context, id: Long, slot: Int, at: Long, pre: Int) {
        val app = App.instance
        var r = app.db.reminders().get(id) ?: return
        if (!r.enabled || r.completed) return
        val now = System.currentTimeMillis()
        if (slot == Scheduler.SLOT_AUTODISMISS) {
            Notifier.cancelReminder(ctx, id)
            logHistory("Напоминание", r.title, "Скрыто автоматически (таймаут)")
            return
        }
        val cat = if (r.categoryId != 0L) app.db.categories().list().firstOrNull { it.id == r.categoryId }?.name else null
        if (slot in 1..9) {
            Notifier.showPre(ctx, r, pre, at + pre * 60_000L)
            logHistory("Напоминание", r.title, "Предварительно (за ${TimeUtil.describeMinutes(pre)})")
            return
        }
        val s = app.settings
        val inQuiet = TimeUtil.inQuietHours(now, s.quietEnabled, s.quietStart, s.quietEnd, s.quietDays)
        val bypass = r.alert.ignoreQuietHours || (s.quietAllowCritical && r.priority == Priority.CRITICAL)
        val quiet = inQuiet && !bypass

        if (quiet && s.quietMode == QuietMode.DEFER && slot != Scheduler.SLOT_DEFER) {
            val until = TimeUtil.quietEndAfter(now, s.quietEnd)
            r = r.copy(snoozedUntil = until, awaitingAck = true, nagCount = -1)
            app.db.reminders().upsert(r)
            Scheduler.scheduleReminder(ctx, r, after = now + 1000)
            logHistory("Напоминание", r.title, "Перенесено из-за тихих часов на ${TimeUtil.time(until)}")
            return
        }

        val isRefire = slot == Scheduler.SLOT_REFIRE || slot == Scheduler.SLOT_DEFER
        val nag = if (isRefire && r.nagCount >= 0) r.nagCount + 1 else 0
        val nagOn = r.alert.nagEveryMinutes > 0 && nag < r.alert.nagMaxTimes
        r = r.copy(
            awaitingAck = true,
            nagCount = nag,
            snoozeCount = if (isRefire) r.snoozeCount else 0,
            snoozedUntil = if (nagOn) now + r.alert.nagEveryMinutes * 60_000L else 0
        )
        app.db.reminders().upsert(r)
        r = Scheduler.scheduleReminder(ctx, r, after = maxOf(at, now) + 1000)

        if (r.alert.ringMode && !quiet && !(Notifier.ringerSilent(ctx) && r.alert.silentBehavior == SilentBehavior.SILENT_NOTIFY)) {
            RingService.start(ctx, RingService.KIND_REMINDER, id, isRefire)
        } else {
            Notifier.showReminder(ctx, r, silent = quiet, label = if (isRefire && nag > 0) "Повтор" else null, catName = cat)
        }
        if (r.alert.autoDismissMinutes > 0 && !r.alert.ringMode) {
            Scheduler.setOne(ctx, Scheduler.T_REMINDER, r.id, Scheduler.SLOT_AUTODISMISS, now + r.alert.autoDismissMinutes * 60_000L)
        }
        logHistory("Напоминание", r.title, when {
            quiet -> "Показано без звука (тихие часы)"
            nag > 0 -> "Повтор №$nag (не подтверждено)"
            isRefire -> "Показано после откладывания"
            else -> "Показано"
        }, r.description.take(200))
    }

    suspend fun reminderDone(ctx: Context, id: Long) {
        val app = App.instance
        var r = app.db.reminders().get(id) ?: return
        Notifier.cancelReminder(ctx, id)
        Scheduler.cancel(ctx, Scheduler.T_REMINDER, id, Scheduler.SLOT_REFIRE..Scheduler.SLOT_AUTODISMISS)
        r = r.copy(awaitingAck = false, snoozedUntil = 0, nagCount = 0, snoozeCount = 0,
            completed = r.repeat == RepeatType.ONCE || (r.nextTriggerAt == 0L && r.repeat != RepeatType.ONCE && r.endAt != 0L))
        app.db.reminders().upsert(r)
        Scheduler.scheduleReminder(ctx, r)
        logHistory("Напоминание", r.title, if (r.stockEnabled) "Принято" else "Выполнено")
        if (r.stockEnabled) consumeStock(ctx, r)
    }

    private suspend fun consumeStock(ctx: Context, r: Reminder) {
        val app = App.instance
        val group = if (r.scenarioRunId != 0L) app.db.reminders().byRun(r.scenarioRunId).filter { it.stockEnabled } else listOf(r)
        val newCount = (r.stockCount - r.stockPerDose).coerceAtLeast(0)
        group.forEach { app.db.reminders().upsert(it.copy(stockCount = newCount)) }
        if (newCount <= r.stockThreshold) {
            Notifier.showSystem(ctx, 2000 + r.id.toInt(), "Заканчивается: ${r.title}",
                "Осталось $newCount (≈ ${if (r.stockPerDose > 0) newCount / r.stockPerDose else 0} приём(ов)). Пора пополнить запас.", "reminder/${r.id}")
            logHistory("Лекарства", r.title, "Заканчивается запас: осталось $newCount")
        }
    }

    suspend fun reminderSnooze(ctx: Context, id: Long, minutes: Int) {
        val app = App.instance
        var r = app.db.reminders().get(id) ?: return
        Notifier.cancelReminder(ctx, id)
        val until = System.currentTimeMillis() + minutes * 60_000L
        r = r.copy(snoozedUntil = until, snoozeCount = r.snoozeCount + 1, awaitingAck = true, nagCount = -1)
        app.db.reminders().upsert(r)
        Scheduler.scheduleReminder(ctx, r)
        logHistory("Напоминание", r.title, "Отложено на ${TimeUtil.describeMinutes(minutes)} (до ${TimeUtil.time(until)})")
    }

    suspend fun alarmSnooze(ctx: Context, id: Long, minutes: Int) {
        val app = App.instance
        var a = app.db.alarms().get(id) ?: return
        val until = System.currentTimeMillis() + minutes * 60_000L
        a = a.copy(snoozedUntil = until, snoozeCount = a.snoozeCount + 1, enabled = true)
        app.db.alarms().upsert(a)
        Scheduler.scheduleAlarm(ctx, a)
        logHistory("Будильник", a.label.ifBlank { "Будильник ${TimeUtil.hm(a.hour, a.minute)}" }, "Отложен на $minutes мин (до ${TimeUtil.time(until)})")
    }

    suspend fun alarmDismiss(ctx: Context, id: Long, auto: Boolean) {
        val app = App.instance
        var a = app.db.alarms().get(id) ?: return
        a = a.copy(snoozedUntil = 0, snoozeCount = 0, enabled = if (a.days == 0) false else a.enabled)
        app.db.alarms().upsert(a)
        Scheduler.scheduleAlarm(ctx, a)
        logHistory("Будильник", a.label.ifBlank { "Будильник ${TimeUtil.hm(a.hour, a.minute)}" }, if (auto) "Автоотключение (нет реакции)" else "Выключен")
    }
}

/** Кнопки в уведомлениях */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val A_DONE = "ru.napominaniya.app.DONE"
        const val A_SNOOZE = "ru.napominaniya.app.SNOOZE"
        const val A_DELETED = "ru.napominaniya.app.DELETED"
        const val A_NOTE_CHECK = "ru.napominaniya.app.NOTE_CHECK"
        const val A_NOTE_UNPIN = "ru.napominaniya.app.NOTE_UNPIN"
        const val A_NOTE_REPLY = "ru.napominaniya.app.NOTE_REPLY"
        const val A_NOTE_DELETED = "ru.napominaniya.app.NOTE_DELETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val id = intent.getLongExtra("id", 0)
        val extra = intent.getIntExtra("extra", 0)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
        goAsync {
            val app = App.instance
            when (intent.action) {
                A_DONE -> Engine.reminderDone(ctx, id)
                A_SNOOZE -> Engine.reminderSnooze(ctx, id, extra)
                A_DELETED -> {
                    val r = app.db.reminders().get(id) ?: return@goAsync
                    if (r.alert.persistent && r.awaitingAck) Notifier.showReminder(ctx, r, silent = true)
                }
                A_NOTE_CHECK -> {
                    val n = app.db.notes().get(id) ?: return@goAsync
                    val idx = n.checklist.indexOfFirst { !it.done }
                    if (idx >= 0) {
                        val list = n.checklist.toMutableList().also { it[idx] = it[idx].copy(done = true) }
                        val nn = n.copy(checklist = list, updatedAt = System.currentTimeMillis())
                        app.db.notes().upsert(nn); Notifier.showNote(ctx, nn)
                        if (list.all { it.done }) logHistory("Заметка", n.title, "Все пункты выполнены")
                    }
                }
                A_NOTE_UNPIN -> {
                    val n = app.db.notes().get(id) ?: return@goAsync
                    app.db.notes().upsert(n.copy(pinned = false)); Notifier.cancelNote(ctx, id)
                    logHistory("Заметка", n.title, "Откреплена из шторки")
                }
                A_NOTE_REPLY -> {
                    val n = app.db.notes().get(id) ?: return@goAsync
                    val t = reply?.trim().orEmpty()
                    val nn = if (t.isEmpty()) n else if (n.checklist.isNotEmpty())
                        n.copy(checklist = n.checklist + ChecklistItem(t), updatedAt = System.currentTimeMillis())
                    else n.copy(text = if (n.text.isBlank()) t else n.text + "\n" + t, updatedAt = System.currentTimeMillis())
                    app.db.notes().upsert(nn); Notifier.showNote(ctx, nn)
                }
                A_NOTE_DELETED -> {
                    val n = app.db.notes().get(id) ?: return@goAsync
                    if (n.pinned) {
                        if (app.settings.repinOnSwipe) Notifier.showNote(ctx, n)
                        else app.db.notes().upsert(n.copy(pinned = false))
                    }
                }
            }
        }
    }
}

/** Перепланирование после перезагрузки, обновления, смены времени */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON"
        goAsync {
            App.instance.seedIfNeeded()
            Scheduler.rescheduleAll(ctx, reportMissed = boot)
            if (boot) logHistory("Система", "Перезагрузка", "Все будильники и напоминания восстановлены")
        }
    }
}
