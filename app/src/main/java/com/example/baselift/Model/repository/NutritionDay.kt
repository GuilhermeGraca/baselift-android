package com.example.baselift.Model.repository

import java.util.Calendar
import java.util.TimeZone

// One local-date/zone snapshot shared by query identity and exact database bounds.
data class NutritionDay(val startMillis: Long, val endExclusiveMillis: Long, val zoneId: String) {
    companion object {
        fun at(timestamp: Long, timeZone: TimeZone): NutritionDay {
            val zone = timeZone.clone() as TimeZone
            val calendar = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
            val next = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            return NutritionDay(calendar.firstInstantOfDate(), next.firstInstantOfDate(), zone.id)
        }

        private fun Calendar.firstInstantOfDate(): Long {
            val year = get(Calendar.YEAR)
            val day = get(Calendar.DAY_OF_YEAR)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Calendar normalizes nonexistent midnight into the first valid hour.
            // At a repeated midnight it can choose the later occurrence; include the earlier one.
            var upper = timeInMillis
            val probe = clone() as Calendar
            fun sameDate(timestamp: Long): Boolean {
                probe.timeInMillis = timestamp
                return probe.get(Calendar.YEAR) == year && probe.get(Calendar.DAY_OF_YEAR) == day
            }
            if (!sameDate(upper - 1)) return upper
            var lower = upper - 3_600_000L
            while (sameDate(lower)) lower -= 3_600_000L
            while (upper - lower > 1) {
                val middle = lower + (upper - lower) / 2
                if (sameDate(middle)) upper = middle else lower = middle
            }
            return upper
        }
    }
}
