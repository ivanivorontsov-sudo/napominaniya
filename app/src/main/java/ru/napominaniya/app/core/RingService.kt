package ru.napominaniya.app.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.napominaniya.app.R
import ru.napominaniya.app.RingActivity
import ru.napominaniya.app.data.*

data class RingState(
    val kind: Int, val id: Long, val title: String, val text: String, val startedAt: Long,
    val alert: AlertSettings, val canSnooze: Boolean, val snoozeMinutes: Int, val snoozeCount: Int
)

/** Сервис переднего плана, воспроизводящий звонок будильника / важного напоминания */
class RingService : Service() {
    companion object {
        const val KIND_ALARM = 1
        const val KIND_REMINDER = 2
        const val ACT_START = "start"; const val ACT_SNOOZE = "snooze"; const val ACT_DISMISS = "dismiss"; const val ACT_MUTE = "mute"
        private val _state = MutableStateFlow<RingState?>(null)
        val state: StateFlow<RingState?> = _state

        fun start(ctx: Context, kind: Int, id: Long, refire: Boolean) {
            val i = Intent(ctx, RingService::class.java).setAction(ACT_START)
                .putExtra("kind", kind).putExtra("id", id).putExtra("refire", refire)
            try { ContextCompat.startForegroundService(ctx, i) }
            catch (e: Exception) {
                // Не удалось запустить сервис (ограничения фона) — хотя бы полноэкранное уведомление
                android.util.Log.e("Napom", "fgs", e)
                val n = NotificationCompat.Builder(ctx, Notifier.CH_RING).setSmallIcon(R.drawable.ic_stat_alarm)
                    .setContentTitle("Будильник").setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(PendingIntent.getActivity(ctx, 1, Intent(ctx, RingActivity::class.java), PendingIntent.FLAG_IMMUTABLE), true)
                    .build()
                runCatching { Notifier.nm(ctx).notify(Notifier.NID_RING, n) }
            }
        }
        fun command(ctx: Context, action: String, minutes: Int = 0) {
            ctx.startService(Intent(ctx, RingService::class.java).setAction(action).putExtra("min", minutes))
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var savedVolume = -1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_START -> begin(intent.getIntExtra("kind", 1), intent.getLongExtra("id", 0))
            ACT_SNOOZE -> finish(snooze = true, minutes = intent.getIntExtra("min", 0))
            ACT_DISMISS -> finish(snooze = false)
            ACT_MUTE -> stopSound()
            else -> if (_state.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(kind: Int, id: Long) {
        // Сначала сразу уходим в foreground (требование Android)
        goForeground(buildNotification("Будильник", "", kind, true, 10))
        val prev = _state.value
        if (prev != null) {
            stopSound()
            scope.launch(Dispatchers.IO) { logHistory(if (prev.kind == KIND_ALARM) "Будильник" else "Напоминание", prev.title, "Прервано новым звонком") }
        }
        scope.launch {
            val st = withContext(Dispatchers.IO) { load(kind, id) } ?: run { stopSelf(); return@launch }
            _state.value = st
            goForeground(buildNotification(st.title, st.text, kind, st.canSnooze, st.snoozeMinutes))
            startSound(st.alert)
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "napom:ring").apply {
                acquire(((st.alert.autoDismissMinutes.takeIf { it > 0 } ?: 30) * 60_000L) + 60_000L)
            }
            handler.removeCallbacksAndMessages(null)
            if (st.alert.autoDismissMinutes > 0) handler.postDelayed({ finish(snooze = false, auto = true) }, st.alert.autoDismissMinutes * 60_000L)
            runCatching {
                startActivity(Intent(this@RingService, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION))
            }
        }
    }

    private suspend fun load(kind: Int, id: Long): RingState? {
        val db = App.instance.db
        return if (kind == KIND_ALARM) {
            val a = db.alarms().get(id) ?: return null
            val can = a.alert.maxSnoozes == 0 || a.snoozeCount < a.alert.maxSnoozes
            RingState(kind, id, a.label.ifBlank { "Будильник" }, TimeUtil.hm(a.hour, a.minute), System.currentTimeMillis(), a.alert, can, a.alert.snoozeMinutes, a.snoozeCount)
        } else {
            val r = db.reminders().get(id) ?: return null
            val can = r.alert.maxSnoozes == 0 || r.snoozeCount < r.alert.maxSnoozes
            RingState(kind, id, r.title.ifBlank { "Напоминание" }, r.description, System.currentTimeMillis(), r.alert, can, r.alert.snoozeMinutes, r.snoozeCount)
        }
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, Notifier.NID_RING, n, type) }
            .onFailure { android.util.Log.e("Napom", "startForeground", it) }
    }

    private fun buildNotification(title: String, text: String, kind: Int, canSnooze: Boolean, snoozeMin: Int): Notification {
        val full = PendingIntent.getActivity(this, 11, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, Notifier.CH_RING)
            .setSmallIcon(if (kind == KIND_ALARM) R.drawable.ic_stat_alarm else R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(text.ifBlank { "Нажмите, чтобы открыть" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true).setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
        val st = _state.value
        if (canSnooze) b.addAction(0, "Отложить на $snoozeMin мин",
            PendingIntent.getService(this, 12, Intent(this, RingService::class.java).setAction(ACT_SNOOZE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (st == null || st.alert.challenge == Challenge.NONE)
            b.addAction(0, if (kind == KIND_ALARM) "Выключить" else "Готово",
                PendingIntent.getService(this, 13, Intent(this, RingService::class.java).setAction(ACT_DISMISS), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return b.build()
    }

    private fun startSound(a: AlertSettings) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val silentMode = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val playSound = a.soundUri != "" && when (a.silentBehavior) {
            SilentBehavior.FORCE_SOUND -> true
            else -> !silentMode
        }
        val doVibrate = a.vibration != VibrationKind.NONE &&
            !(silentMode && am.ringerMode == AudioManager.RINGER_MODE_SILENT && a.silentBehavior == SilentBehavior.RESPECT)
        if (doVibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
            val p = a.vibrationPattern().let { if (it.size < 2) longArrayOf(0, 500, 500) else it }
            runCatching {
                vibrator?.vibrate(VibrationEffect.createWaveform(p, 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
        if (!playSound) return
        val uri: Uri = a.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        // Громкость потока «будильник» по настройке элемента (восстанавливается после)
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, (max * a.volume / 100f).toInt().coerceIn(1, max), 0)
        }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            try { mp.setDataSource(this, uri) } catch (e: Exception) {
                mp.reset()
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            }
            mp.isLooping = true
            mp.prepare()
            val gradual = a.gradualSeconds
            if (gradual > 0) {
                mp.setVolume(0.05f, 0.05f)
                val steps = gradual * 2
                for (k in 1..steps) handler.postDelayed({
                    val v = (0.05f + 0.95f * k / steps).coerceAtMost(1f)
                    runCatching { player?.setVolume(v, v) }
                }, k * 500L)
            }
            mp.start()
            player = mp
        } catch (e: Exception) {
            android.util.Log.e("Napom", "player", e)
            runCatching { mp.release() }
        }
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        runCatching { vibrator?.cancel() }
        if (savedVolume >= 0) runCatching {
            (getSystemService(AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
        }
        savedVolume = -1
    }

    private fun finish(snooze: Boolean, minutes: Int = 0, auto: Boolean = false) {
        val st = _state.value
        stopSound()
        _state.value = null
        runCatching { wake?.release() }
        if (st != null) {
            val ctx = applicationContext
            App.instance.scope.launch {
                val m = if (minutes > 0) minutes else st.snoozeMinutes
                if (st.kind == KIND_ALARM) {
                    if (snooze) Engine.alarmSnooze(ctx, st.id, m) else Engine.alarmDismiss(ctx, st.id, auto)
                } else {
                    if (snooze) Engine.reminderSnooze(ctx, st.id, m)
                    else if (auto) {
                        logHistory("Напоминание", st.title, "Звонок завершён автоматически (нет реакции)")
                        val r = App.instance.db.reminders().get(st.id)
                        if (r != null) Notifier.showReminder(ctx, r, silent = true, label = "Пропущено")
                    } else Engine.reminderDone(ctx, st.id)
                }
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        scope.cancel()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        super.onDestroy()
    }
}
