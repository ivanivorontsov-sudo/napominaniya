package ru.napominaniya.app.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Days {
    val short = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val full = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val ALL = 0b1111111
    fun has(mask: Int, dow: DayOfWeek) = mask and (1 shl (dow.value - 1)) != 0
    fun describe(mask: Int): String = when (mask) {
        0 -> "Однократно"
        ALL -> "Каждый день"
        WEEKDAYS -> "По будням"
        WEEKEND -> "По выходным"
        else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { short[it] }
    }
}

/** Правила пропусков: выходные, праздники */
data class Rules(
    val weekendMask: Int = Days.WEEKEND,
    val ruHolidays: Boolean = true,
    val customHolidays: Set<String> = emptySet()
) {
    fun isHoliday(d: LocalDate): Boolean {
        if (customHolidays.contains(d.toString())) return true
        if (!ruHolidays) return false
        val md = d.monthValue * 100 + d.dayOfMonth
        return md in 101..108 || md == 223 || md == 308 || md == 501 || md == 509 || md == 612 || md == 1104
    }
    fun isWeekend(d: LocalDate) = Days.has(weekendMask, d.dayOfWeek)
}

object TimeUtil {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val ru = Locale("ru")
    private val fTime = DateTimeFormatter.ofPattern("HH:mm", ru)
    private val fDate = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val fDateShort = DateTimeFormatter.ofPattern("EE, d MMM", ru)
    private val fFull = DateTimeFormatter.ofPattern("EE, d MMM yyyy, HH:mm", ru)

    fun ldt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    fun ms(l: LocalDateTime): Long = l.atZone(zone).toInstant().toEpochMilli()
    fun time(ms: Long) = ldt(ms).format(fTime)
    fun date(ms: Long) = ldt(ms).format(fDate)
    fun dateShort(ms: Long) = ldt(ms).format(fDateShort)
    fun full(ms: Long) = ldt(ms).format(fFull)
    fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = ms - now
        val mins = abs(diff) / 60000
        val s = when {
            mins < 1 -> return if (diff >= 0) "меньше чем через минуту" else "только что"
            mins < 60 -> "$mins мин"
            mins < 60 * 24 -> "${mins / 60} ч ${mins % 60} мин"
            else -> "${mins / 1440} дн ${(mins % 1440) / 60} ч"
        }
        return if (diff >= 0) "через $s" else "$s назад"
    }

    // ---------- Смещения для сценариев: «-12 ч», «-1 день», «+30 мин», «-1д 2ч» ----------
    private val unitMap = linkedMapOf(
        "нед" to 10080L, "н" to 10080L, "w" to 10080L,
        "сут" to 1440L, "дн" to 1440L, "ден" to 1440L, "дня" to 1440L, "д" to 1440L, "d" to 1440L,
        "час" to 60L, "ч" to 60L, "h" to 60L,
        "мин" to 1L, "м" to 1L, "m" to 1L
    )

    fun parseOffset(input: String): Long? {
        val s = input.trim().lowercase(ru).replace('−', '-').replace('–', '-')
        if (s.isEmpty()) return null
        if (s == "0" || s == "в момент события" || s == "сразу") return 0
        var sign = 1L
        var rest = s
        if (rest.startsWith("-")) { sign = -1; rest = rest.substring(1) } else if (rest.startsWith("+")) rest = rest.substring(1)
        val re = Regex("(\\d+(?:[.,]\\d+)?)\\s*([a-zа-яё]*)")
        var total = 0.0
        var matched = false
        for (m in re.findAll(rest)) {
            val num = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val u = m.groupValues[2]
            val mult = if (u.isEmpty()) 1L else unitMap.entries.firstOrNull { u.startsWith(it.key) }?.value ?: return null
            total += num * mult
            matched = true
        }
        if (!matched) return null
        return sign * Math.round(total)
    }

    fun formatOffset(min: Long): String {
        if (min == 0L) return "в момент события"
        val a = abs(min)
        val parts = mutableListOf<String>()
        val w = a / 10080; val d = (a % 10080) / 1440; val h = (a % 1440) / 60; val m = a % 60
        if (w > 0 && d == 0L && h == 0L && m == 0L) parts += "$w нед" else {
            val dd = a / 1440
            if (dd > 0) parts += "$dd д"
            if (h > 0) parts += "$h ч"
            if (m > 0) parts += "$m мин"
        }
        return (if (min < 0) "−" else "+") + parts.joinToString(" ")
    }

    fun describeOffset(min: Long): String =
        if (min == 0L) "в момент события" else formatOffset(min).substring(1) + if (min < 0) " до" else " после"

    fun describeMinutes(m: Int): String = when {
        m % 1440 == 0 -> "${m / 1440} дн"
        m % 60 == 0 -> "${m / 60} ч"
        else -> "$m мин"
    }

    // ---------- Расчёт ближайшего срабатывания ----------

    fun nextAlarm(a: Alarm, now: Long, rules: Rules): Long? {
        val today = ldt(now).toLocalDate()
        for (i in 0..400) {
            val d = today.plusDays(i.toLong())
            if (a.days != 0 && !Days.has(a.days, d.dayOfWeek)) continue
            val weekend = rules.isWeekend(d)
            val (h, m) = if (a.weekendTimeEnabled && weekend) a.weekendHour to a.weekendMinute else a.hour to a.minute
            val t = ms(d.atTime(h, m))
            if (t <= now) continue
            if (a.skipDates.contains(d.toString())) continue
            if (a.skipHolidays && rules.isHoliday(d)) continue
            if (a.skipNextAt != 0L && t == a.skipNextAt) continue
            return t
        }
        return null
    }

    /** Ближайшее срабатывание напоминания строго позже [after] */
    fun nextReminder(r: Reminder, after: Long, rules: Rules): Long? {
        if (r.startAt == 0L) return null
        val start = ldt(r.startAt)
        fun ok(t: Long): Boolean {
            val d = ldt(t).toLocalDate()
            if (r.skipDates.contains(d.toString())) return false
            if (r.skipHolidays && rules.isHoliday(d)) return false
            if (r.skipWeekends && rules.isWeekend(d)) return false
            return true
        }
        fun endOk(t: Long) = r.endAt == 0L || t <= r.endAt
        when (r.repeat) {
            RepeatType.ONCE -> return if (r.startAt > after) r.startAt else null
            RepeatType.INTERVAL -> {
                val iv = r.intervalMinutes.coerceAtLeast(1) * 60000
                var k = if (after < r.startAt) 0 else (after - r.startAt) / iv + 1
                repeat(5000) {
                    val t = r.startAt + k * iv
                    if (!endOk(t)) return null
                    if (t > after && ok(t)) return t
                    k++
                }
                return null
            }
            else -> {
                // Грубый прыжок вперёд, чтобы не перебирать годы истории
                var k = 0L
                if (after > r.startAt) {
                    val daysBetween = Duration.between(start, ldt(after)).toDays()
                    k = when (r.repeat) {
                        RepeatType.DAILY, RepeatType.WEEKLY -> (daysBetween - 1).coerceAtLeast(0)
                        RepeatType.MONTHLY -> (daysBetween / 31 - 1).coerceAtLeast(0)
                        RepeatType.YEARLY -> (daysBetween / 366 - 1).coerceAtLeast(0)
                        else -> 0
                    }
                }
                val mask = if (r.weekDays == 0) 1 shl (start.dayOfWeek.value - 1) else r.weekDays
                repeat(3000) {
                    val c = when (r.repeat) {
                        RepeatType.DAILY, RepeatType.WEEKLY -> start.plusDays(k)
                        RepeatType.MONTHLY -> start.plusMonths(k)
                        else -> start.plusYears(k)
                    }
                    k++
                    if (r.repeat == RepeatType.WEEKLY && !Days.has(mask, c.dayOfWeek)) return@repeat
                    val t = ms(c)
                    if (!endOk(t)) return null
                    if (t > after && ok(t)) return t
                }
                return null
            }
        }
    }

    fun inQuietHours(t: Long, enabled: Boolean, startMin: Int, endMin: Int, days: Int): Boolean {
        if (!enabled) return false
        val l = ldt(t)
        val cur = l.hour * 60 + l.minute
        return if (startMin <= endMin) {
            cur in startMin until endMin && Days.has(days, l.dayOfWeek)
        } else {
            // через полночь: день начала определяет активность
            if (cur >= startMin) Days.has(days, l.dayOfWeek)
            else if (cur < endMin) Days.has(days, l.dayOfWeek.minus(1))
            else false
        }
    }

    fun quietEndAfter(t: Long, endMin: Int): Long {
        val l = ldt(t)
        var e = l.toLocalDate().atTime(endMin / 60, endMin % 60)
        if (!e.isAfter(l)) e = e.plusDays(1)
        return ms(e)
    }
}
