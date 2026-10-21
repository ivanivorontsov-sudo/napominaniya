package ru.napominaniya.app

import org.junit.Assert.*
import org.junit.Test
import ru.napominaniya.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime

class TimeUtilTest {
    private fun ms(s: String) = TimeUtil.ms(LocalDateTime.parse(s))
    private val rules = Rules()

    @Test fun parseOffsets() {
        assertEquals(-720L, TimeUtil.parseOffset("-12 ч"))
        assertEquals(-1440L, TimeUtil.parseOffset("-1 день"))
        assertEquals(30L, TimeUtil.parseOffset("+30 мин"))
        assertEquals(-(1440L + 120), TimeUtil.parseOffset("-1д 2ч"))
        assertEquals(-2880L, TimeUtil.parseOffset("-2 дня"))
        assertEquals(-10080L, TimeUtil.parseOffset("-1 неделя"))
        assertEquals(0L, TimeUtil.parseOffset("0"))
        assertEquals(-90L, TimeUtil.parseOffset("−1,5 ч"))
        assertNull(TimeUtil.parseOffset("abc"))
        assertEquals("−12 ч", TimeUtil.formatOffset(-720))
        assertEquals(-720L, TimeUtil.parseOffset(TimeUtil.formatOffset(-720)))
        assertEquals(-(1440L * 3 + 90), TimeUtil.parseOffset(TimeUtil.formatOffset(-(1440L * 3 + 90))))
    }

    @Test fun daily() {
        val r = Reminder(startAt = ms("2026-01-10T08:00"), repeat = RepeatType.DAILY)
        assertEquals(ms("2026-03-05T08:00"), TimeUtil.nextReminder(r, ms("2026-03-04T09:00"), rules))
        assertEquals(ms("2026-01-10T08:00"), TimeUtil.nextReminder(r, ms("2025-12-01T09:00"), rules))
    }

    @Test fun onceInPast() {
        val r = Reminder(startAt = ms("2026-01-10T08:00"))
        assertNull(TimeUtil.nextReminder(r, ms("2026-01-10T08:00"), rules))
    }

    @Test fun weeklyAndSkips() {
        // Пн и Пт
        val r = Reminder(startAt = ms("2026-09-28T10:00"), repeat = RepeatType.WEEKLY, weekDays = 0b0010001)
        assertEquals(ms("2026-10-02T10:00"), TimeUtil.nextReminder(r, ms("2026-09-28T11:00"), rules))
        val r2 = r.copy(skipDates = listOf("2026-10-02"))
        assertEquals(ms("2026-10-05T10:00"), TimeUtil.nextReminder(r2, ms("2026-09-28T11:00"), rules))
    }

    @Test fun monthlyEndOfMonth() {
        val r = Reminder(startAt = ms("2026-01-31T12:00"), repeat = RepeatType.MONTHLY)
        assertEquals(ms("2026-02-28T12:00"), TimeUtil.nextReminder(r, ms("2026-02-01T00:00"), rules))
    }

    @Test fun intervalAndEnd() {
        val r = Reminder(startAt = ms("2026-01-01T08:00"), repeat = RepeatType.INTERVAL, intervalMinutes = 90, endAt = ms("2026-01-01T12:00"))
        assertEquals(ms("2026-01-01T09:30"), TimeUtil.nextReminder(r, ms("2026-01-01T08:00"), rules))
        assertEquals(ms("2026-01-01T11:00"), TimeUtil.nextReminder(r, ms("2026-01-01T10:00"), rules))
        assertNull(TimeUtil.nextReminder(r, ms("2026-01-01T11:30"), rules))
    }

    @Test fun holidaysSkipped() {
        val r = Reminder(startAt = ms("2025-12-30T09:00"), repeat = RepeatType.DAILY, skipHolidays = true)
        assertEquals(ms("2026-01-09T09:00"), TimeUtil.nextReminder(r, ms("2025-12-31T10:00"), rules))
    }

    @Test fun alarmWeekendTime() {
        // 2026-10-03 — суббота
        val a = Alarm(hour = 7, minute = 0, days = Days.ALL, weekendTimeEnabled = true, weekendHour = 9, weekendMinute = 30)
        assertEquals(ms("2026-10-03T09:30"), TimeUtil.nextAlarm(a, ms("2026-10-02T08:00"), rules))
        assertEquals(ms("2026-10-02T07:00"), TimeUtil.nextAlarm(a, ms("2026-10-01T08:00"), rules))
    }

    @Test fun alarmSkipNextAndHoliday() {
        val a = Alarm(hour = 7, minute = 0, days = Days.WEEKDAYS, skipHolidays = true)
        // 2026-11-04 (среда) — праздник
        assertEquals(ms("2026-11-05T07:00"), TimeUtil.nextAlarm(a, ms("2026-11-03T08:00"), rules))
        val b = Alarm(hour = 7, minute = 0, days = Days.WEEKDAYS, skipNextAt = ms("2026-10-01T07:00"))
        assertEquals(ms("2026-10-02T07:00"), TimeUtil.nextAlarm(b, ms("2026-09-30T08:00"), rules))
    }

    @Test fun quietHoursOverMidnight() {
        assertTrue(TimeUtil.inQuietHours(ms("2026-10-01T23:30"), true, 23 * 60, 7 * 60, Days.ALL))
        assertTrue(TimeUtil.inQuietHours(ms("2026-10-01T06:59"), true, 23 * 60, 7 * 60, Days.ALL))
        assertFalse(TimeUtil.inQuietHours(ms("2026-10-01T07:00"), true, 23 * 60, 7 * 60, Days.ALL))
        assertEquals(ms("2026-10-02T07:00"), TimeUtil.quietEndAfter(ms("2026-10-01T23:30"), 7 * 60))
    }

    @Test fun builtInScenariosValid() {
        val list = Defaults.scenarios()
        assertTrue(list.size >= 20)
        list.forEach { s -> assertTrue(s.name, s.steps.isNotEmpty()); assertTrue(s.name, s.steps.all { it.title.isNotBlank() }) }
        assertTrue(Rules().isHoliday(LocalDate.parse("2026-03-08")))
    }
}
