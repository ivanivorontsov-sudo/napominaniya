package ru.napominaniya.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import ru.napominaniya.app.MainActivity
import ru.napominaniya.app.data.*

object Scheduler {
    const val T_ALARM = 1
    const val T_REMINDER = 2
    const val SLOT_MAIN = 0          // 1..9 — предварительные
    const val SLOT_REFIRE = 10       // отложено / повтор до подтверждения
    const val SLOT_DEFER = 12        // перенос из-за тихих часов
    const val SLOT_AUTODISMISS = 13
    const val ACTION_FIRE = "ru.napominaniya.app.FIRE"

    const val EX_TYPE = "type"; const val EX_ID = "id"; const val EX_SLOT = "slot"; const val EX_AT = "at"; const val EX_PRE = "pre"

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    private fun pi(ctx: Context, type: Int, id: Long, slot: Int, at: Long = 0, pre: Int = 0, create: Boolean = true): PendingIntent? {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("napom://fire/$type/$id/$slot")
            putExtra(EX_TYPE, type); putExtra(EX_ID, id); putExtra(EX_SLOT, slot); putExtra(EX_AT, at); putExtra(EX_PRE, pre)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, (type * 1_000_000 + id * 20 + slot).toInt(), i, flags)
    }

    private fun set(ctx: Context, at: Long, op: PendingIntent, alarmClock: Boolean) {
        val m = am(ctx)
        try {
            if (canExact(ctx)) {
                if (alarmClock) {
                    val show = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                    m.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
                } else m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            } else {
                // Без разрешения на точные будильники — неточный режим (может опаздывать в Doze)
                m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            }
        } catch (e: SecurityException) {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    fun cancel(ctx: Context, type: Int, id: Long, slots: IntRange = 0..13) {
        for (s in slots) pi(ctx, type, id, s, create = false)?.let { am(ctx).cancel(it); it.cancel() }
    }

    fun setOne(ctx: Context, type: Int, id: Long, slot: Int, at: Long, alarmClock: Boolean = false, pre: Int = 0) {
        set(ctx, at, pi(ctx, type, id, slot, at, pre)!!, alarmClock)
    }

    // ---------------- Будильники ----------------
    suspend fun scheduleAlarm(ctx: Context, a0: Alarm, after: Long = 0): Alarm {
        val app = App.instance
        cancel(ctx, T_ALARM, a0.id)
        val now = System.currentTimeMillis()
        var a = a0
        if (a.skipNextAt != 0L && a.skipNextAt < now) a = a.copy(skipNextAt = 0)
        if (!a.enabled) {
            a = a.copy(nextTriggerAt = 0, snoozedUntil = 0)
        } else if (a.snoozedUntil > now) {
            setOne(ctx, T_ALARM, a.id, SLOT_REFIRE, a.snoozedUntil, alarmClock = true)
            val next = TimeUtil.nextAlarm(a, maxOf(now, after), app.rules()) ?: 0
            a = a.copy(nextTriggerAt = next)
            if (next > a.snoozedUntil) setOne(ctx, T_ALARM, a.id, SLOT_MAIN, next, alarmClock = true)
        } else {
            val next = TimeUtil.nextAlarm(a, maxOf(now, after), app.rules())
            a = a.copy(nextTriggerAt = next ?: 0, snoozedUntil = 0)
            if (next != null) setOne(ctx, T_ALARM, a.id, SLOT_MAIN, next, alarmClock = true)
        }
        if (a != a0) app.db.alarms().upsert(a)
        return a
    }

    // ---------------- Напоминания ----------------
    suspend fun scheduleReminder(ctx: Context, r0: Reminder, after: Long = 0): Reminder {
        val app = App.instance
        cancel(ctx, T_REMINDER, r0.id, 0..12)
        val now = System.currentTimeMillis()
        var r = r0
        if (!r.enabled || r.completed) {
            r = r.copy(nextTriggerAt = 0)
        } else {
            val next = TimeUtil.nextReminder(r, maxOf(now, after), app.rules())
            r = r.copy(nextTriggerAt = next ?: 0)
            val strong = r.alert.ringMode || r.alert.importance == Importance.URGENT || r.priority == Priority.CRITICAL
            if (next != null) {
                setOne(ctx, T_REMINDER, r.id, SLOT_MAIN, next, alarmClock = strong)
                r.preReminders.take(9).forEachIndexed { i, m ->
                    val t = next - m * 60_000L
                    if (m > 0 && t > now) setOne(ctx, T_REMINDER, r.id, 1 + i, t, pre = m)
                }
            }
            if (r.snoozedUntil > now) setOne(ctx, T_REMINDER, r.id, SLOT_REFIRE, r.snoozedUntil, alarmClock = strong)
        }
        if (r != r0) app.db.reminders().upsert(r)
        return r
    }

    suspend fun rescheduleAll(ctx: Context, reportMissed: Boolean) {
        val app = App.instance
        val now = System.currentTimeMillis()
        app.db.alarms().list().forEach { scheduleAlarm(ctx, it) }
        app.db.reminders().list().forEach { r ->
            if (reportMissed && app.settings.missedOnBoot && r.enabled && !r.completed &&
                r.nextTriggerAt in (now - 86_400_000L) until now) {
                Notifier.showMissed(ctx, r)
            }
            scheduleReminder(ctx, r)
        }
        app.db.notes().list().filter { it.pinned }.forEach { Notifier.showNote(ctx, it) }
    }
}
