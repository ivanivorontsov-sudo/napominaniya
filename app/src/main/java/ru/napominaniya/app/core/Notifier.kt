package ru.napominaniya.app.core

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ru.napominaniya.app.MainActivity
import ru.napominaniya.app.R
import ru.napominaniya.app.data.*
import kotlinx.coroutines.launch

object Notifier {
    const val CH_RING = "alarm_ring"
    const val CH_PRE = "pre_reminders"
    const val CH_NOTES = "live_notes"
    const val CH_SILENT = "silent"
    const val CH_SYSTEM = "system"
    const val GROUP_CUSTOM = "custom"

    const val NID_REMINDER = 100_000
    const val NID_PRE = 200_000
    const val NID_RING = 300_000
    const val NID_NOTE = 400_000
    const val NID_SYSTEM = 500_000

    const val KEY_REPLY = "reply"

    fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureBaseChannels(ctx: Context) {
        val m = nm(ctx)
        m.createNotificationChannelGroup(NotificationChannelGroup(GROUP_CUSTOM, "Персональные настройки оповещений"))
        m.createNotificationChannel(NotificationChannel(CH_RING, "Будильники (звонок)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Полноэкранный звонок будильника. Звук воспроизводит само приложение."
            setSound(null, null); enableVibration(false); setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        m.createNotificationChannel(NotificationChannel(CH_PRE, "Предварительные напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "«Через 10 минут…» и другие заблаговременные уведомления"
        })
        m.createNotificationChannel(NotificationChannel(CH_NOTES, "Живые заметки", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Закреплённые заметки в шторке"; setShowBadge(false)
        })
        m.createNotificationChannel(NotificationChannel(CH_SILENT, "Тихие уведомления", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Уведомления во время тихих часов и в беззвучном режиме"
        })
        m.createNotificationChannel(NotificationChannel(CH_SYSTEM, "Служебные", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Пропущенные напоминания, запас лекарств и т.п."
        })
    }

    /** Динамический канал под конкретный набор настроек (звук, вибрация, важность, индикатор) */
    fun channelFor(ctx: Context, a: AlertSettings): String {
        val key = "${a.importance}|${a.soundUri}|${a.vibration}|${if (a.vibration == VibrationKind.CUSTOM) a.customPattern else ""}|${a.ledColor}"
        val id = "c_" + Integer.toHexString(key.hashCode())
        val m = nm(ctx)
        if (m.getNotificationChannel(id) == null) {
            val imp = when (a.importance) {
                Importance.URGENT, Importance.HIGH -> NotificationManager.IMPORTANCE_HIGH
                Importance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
                Importance.LOW -> NotificationManager.IMPORTANCE_LOW
                Importance.MIN -> NotificationManager.IMPORTANCE_MIN
            }
            val soundName = when (a.soundUri) { null -> "звук по умолч."; "" -> "без звука"; else -> soundTitle(ctx, a.soundUri) }
            val name = "${a.importance.title.substringBefore(" (")} · $soundName · ${a.vibration.title}" + if (a.ledColor != 0) " · индикатор" else ""
            val ch = NotificationChannel(id, name, imp).apply {
                group = GROUP_CUSTOM
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                when (a.soundUri) {
                    null -> setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, attrs)
                    "" -> setSound(null, null)
                    else -> setSound(Uri.parse(a.soundUri), attrs)
                }
                if (a.vibration == VibrationKind.NONE) enableVibration(false)
                else { enableVibration(true); vibrationPattern = a.vibrationPattern() }
                if (a.ledColor != 0) { enableLights(true); lightColor = a.ledColor }
                lockscreenVisibility = if (a.showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE
            }
            m.createNotificationChannel(ch)
        }
        return id
    }

    fun soundTitle(ctx: Context, uri: String?): String = when (uri) {
        null -> "По умолчанию"
        "" -> "Без звука"
        else -> runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull() ?: "Выбранный звук"
    }

    private fun openAppPi(ctx: Context, route: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("napom://open/$route")
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun actionPi(ctx: Context, action: String, id: Long, extra: Int = 0, mutable: Boolean = false): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("napom://act/$action/$id/$extra")
            putExtra("id", id); putExtra("extra", extra)
        }
        val f = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, (action.hashCode() * 31 + id * 7 + extra).toInt(), i, f or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun ringerSilent(ctx: Context): Boolean =
        ctx.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL

    fun vibrateOnce(ctx: Context, pattern: LongArray) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    fun playThroughAlarmStream(ctx: Context, uri: String?) {
        runCatching {
            val u = if (uri.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uri)
            val r = RingtoneManager.getRingtone(ctx, u) ?: return
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            r.play()
        }
    }

    // ------------------ Напоминания ------------------
    /**
     * @param silent показать без звука (тихие часы)
     * @param label префикс («Повтор», «Пропущено»)
     */
    fun showReminder(ctx: Context, r: Reminder, silent: Boolean, label: String? = null, catName: String? = null) {
        if (!canPost(ctx)) return
        val a = r.alert
        val ringerSilent = ringerSilent(ctx)
        var quiet = silent
        if (ringerSilent) when (a.silentBehavior) {
            SilentBehavior.SILENT_NOTIFY -> quiet = true
            SilentBehavior.VIBRATE_ONLY -> { quiet = true; if (!silent) vibrateOnce(ctx, a.vibrationPattern().takeIf { it.size > 1 } ?: VibrationKind.NORMAL.pattern) }
            SilentBehavior.FORCE_SOUND -> if (!silent) playThroughAlarmStream(ctx, a.soundUri)
            SilentBehavior.RESPECT -> {}
        }
        val ch = if (quiet) CH_SILENT else channelFor(ctx, a)
        val title = (label?.let { "$it: " } ?: "") + r.title.ifBlank { "Напоминание" }
        val meta = buildString {
            append("🕒 ").append(TimeUtil.full(System.currentTimeMillis()))
            append(" · приоритет: ").append(r.priority.title.lowercase())
            catName?.let { append(" · ").append(it) }
            if (r.repeat != RepeatType.ONCE) append(" · ").append(r.repeat.title.lowercase())
            if (r.stockEnabled) append("\n💊 Осталось: ${r.stockCount}")
        }
        val body = if (r.description.isBlank()) meta else r.description + "\n\n" + meta
        val canSnooze = a.maxSnoozes == 0 || r.snoozeCount < a.maxSnoozes
        val b = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(r.description.ifBlank { meta.lineSequence().first() })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText(r.priority.title))
            .setColor(r.priority.color.toInt())
            .setCategory(if (a.importance == Importance.URGENT || r.priority == Priority.CRITICAL) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPi(ctx, "reminder/${r.id}", r.id.toInt()))
            .setAutoCancel(!a.persistent)
            .setOngoing(a.persistent)
            .setOnlyAlertOnce(false)
            .setSilent(quiet)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .setVisibility(if (a.showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setDeleteIntent(actionPi(ctx, ActionReceiver.A_DELETED, r.id))
            .addAction(0, if (r.stockEnabled) "Принял ✓" else "Готово ✓", actionPi(ctx, ActionReceiver.A_DONE, r.id))
        if (canSnooze) {
            b.addAction(0, "5 мин", actionPi(ctx, ActionReceiver.A_SNOOZE, r.id, 5))
            b.addAction(0, "15 мин", actionPi(ctx, ActionReceiver.A_SNOOZE, r.id, 15))
            b.addAction(0, "1 час", actionPi(ctx, ActionReceiver.A_SNOOZE, r.id, 60))
        }
        if (a.ledColor != 0) b.setLights(a.ledColor, 1000, 1000)
        if (a.autoDismissMinutes > 0) b.setTimeoutAfter(a.autoDismissMinutes * 60_000L)
        if (a.importance == Importance.URGENT && !quiet) {
            b.setFullScreenIntent(openAppPi(ctx, "reminder/${r.id}", r.id.toInt() + 77), true)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(NID_REMINDER + r.id.toInt(), b.build()) }
    }

    fun showPre(ctx: Context, r: Reminder, minutes: Int, occurrence: Long) {
        if (!canPost(ctx)) return
        val quiet = App.instance.let { app ->
            val s = app.settings
            !r.alert.ignoreQuietHours && TimeUtil.inQuietHours(System.currentTimeMillis(), s.quietEnabled, s.quietStart, s.quietEnd, s.quietDays)
        } || (ringerSilent(ctx) && r.alert.silentBehavior == SilentBehavior.SILENT_NOTIFY)
        val b = NotificationCompat.Builder(ctx, if (quiet) CH_SILENT else CH_PRE)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Через ${TimeUtil.describeMinutes(minutes)}: ${r.title}")
            .setContentText("В ${TimeUtil.time(occurrence)} · ${TimeUtil.dateShort(occurrence)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "В ${TimeUtil.time(occurrence)}, ${TimeUtil.dateShort(occurrence)}" + if (r.description.isNotBlank()) "\n\n${r.description}" else ""))
            .setColor(r.priority.color.toInt())
            .setContentIntent(openAppPi(ctx, "reminder/${r.id}", r.id.toInt()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter((occurrence - System.currentTimeMillis()).coerceAtLeast(60_000))
        runCatching { NotificationManagerCompat.from(ctx).notify(NID_PRE + r.id.toInt(), b.build()) }
    }

    fun showMissed(ctx: Context, r: Reminder) {
        showReminder(ctx, r, silent = false, label = "Пропущено")
        App.instance.scope.launchLog("Напоминание", r.title, "Пропущено (телефон был выключен)")
    }

    fun showSystem(ctx: Context, id: Int, title: String, text: String, route: String = "") {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_SYSTEM)
            .setSmallIcon(R.drawable.ic_stat_bell).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true)
            .setContentIntent(openAppPi(ctx, route, NID_SYSTEM + id))
        runCatching { NotificationManagerCompat.from(ctx).notify(NID_SYSTEM + id, b.build()) }
    }

    fun cancelReminder(ctx: Context, id: Long) {
        nm(ctx).cancel(NID_REMINDER + id.toInt()); nm(ctx).cancel(NID_PRE + id.toInt())
    }

    // ------------------ Живые заметки ------------------
    fun showNote(ctx: Context, n: Note) {
        if (!canPost(ctx) || !n.pinned) return
        val done = n.checklist.count { it.done }
        val b = NotificationCompat.Builder(ctx, CH_NOTES)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(n.title.ifBlank { "Заметка" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(n.silentUpdates)
            .setShowWhen(true).setWhen(n.updatedAt)
            .setContentIntent(openAppPi(ctx, "note/${n.id}", NID_NOTE + n.id.toInt()))
            .setDeleteIntent(actionPi(ctx, ActionReceiver.A_NOTE_DELETED, n.id))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (n.color != 0) b.setColor(n.color).setColorized(false)
        if (n.checklist.isNotEmpty()) {
            b.setContentText("Выполнено $done из ${n.checklist.size}" + if (n.text.isNotBlank()) " · ${n.text.lineSequence().first()}" else "")
            val st = NotificationCompat.InboxStyle().setSummaryText("$done/${n.checklist.size}")
            if (n.text.isNotBlank()) st.addLine(n.text.take(80))
            (n.checklist.filter { !it.done } + n.checklist.filter { it.done }).take(7).forEach { st.addLine((if (it.done) "☑ " else "☐ ") + it.text) }
            b.setStyle(st)
            b.setProgress(n.checklist.size, done, false)
            if (done < n.checklist.size) b.addAction(0, "✓ Следующий пункт", actionPi(ctx, ActionReceiver.A_NOTE_CHECK, n.id))
        } else {
            b.setContentText(n.text.lineSequence().firstOrNull() ?: "")
            b.setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
        }
        val remote = RemoteInput.Builder(KEY_REPLY).setLabel(if (n.checklist.isNotEmpty()) "Новый пункт" else "Добавить строку").build()
        b.addAction(NotificationCompat.Action.Builder(0, if (n.checklist.isNotEmpty()) "Добавить пункт" else "Дописать",
            actionPi(ctx, ActionReceiver.A_NOTE_REPLY, n.id, mutable = true)).addRemoteInput(remote).setAllowGeneratedReplies(false).build())
        b.addAction(0, "Открепить", actionPi(ctx, ActionReceiver.A_NOTE_UNPIN, n.id))
        runCatching { NotificationManagerCompat.from(ctx).notify(NID_NOTE + n.id.toInt(), b.build()) }
    }

    fun cancelNote(ctx: Context, id: Long) = nm(ctx).cancel(NID_NOTE + id.toInt())
}

fun kotlinx.coroutines.CoroutineScope.launchLog(kind: String, title: String, action: String, text: String = "") {
    launch {
        App.instance.db.history().insert(HistoryEntry(kind = kind, title = title, text = text, action = action))
    }
}

suspend fun logHistory(kind: String, title: String, action: String, text: String = "") {
    App.instance.db.history().insert(HistoryEntry(kind = kind, title = title, text = text, action = action))
}
