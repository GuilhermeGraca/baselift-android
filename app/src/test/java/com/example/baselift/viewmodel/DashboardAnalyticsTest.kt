package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.ViewModel.dashboard.DashboardAnalytics
import com.example.baselift.ViewModel.dashboard.NutritionSummary
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DashboardAnalyticsTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun setup() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Lisbon"))
    }

    @After
    fun teardown() {
        TimeZone.setDefault(originalZone)
    }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    private fun meal(id: Int, time: Long) = NutritionLogEntity(
        id = id, calories = 100, protein = 10, carbs = 20, fats = 5, timestamp = time
    )

    @Test
    fun `both streaks apply the same increasing rest allowance to activity days`() {
        val now = timestamp(2026, 10, 14)
        val analytics = DashboardAnalytics { now }
        val days = listOf(14, 12, 11, 9, 7, 4, 2)
        val logs = days.mapIndexed { i, day -> meal(i + 1, timestamp(2026, 10, day)) } +
            meal(8, timestamp(2026, 9, 25)) +
            meal(20, timestamp(2026, 10, 14, 18))
        val nutrition = analytics.nutritionHistory(logs)
        val sessions = analytics.sessionHistory(logs.map {
            WorkoutSessionEntity(id = it.id, workoutId = 1, timestamp = it.timestamp, isCompleted = true)
        })
        val expected = listOf(1, 4, 5, 5, 7, 8, 8, 8)
        assertEquals(expected, (0..7).map { analytics.streak(nutrition.calendar.keys, it) })
        assertEquals(expected, (0..7).map { analytics.streak(sessions.calendarWorkoutIds.keys, it) })
        assertEquals(NutritionSummary(200, 20, 40, 10), nutrition.weeklyDays.getValue(3))
        assertEquals(3, sessions.weeklySessionCount)
        assertEquals(0, analytics.streak(emptySet(), 7))
    }

    @Test
    fun `every allowance permits its exact missed day budget and stops at one extra miss`() {
        val analytics = DashboardAnalytics { timestamp(2026, 10, 11) }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        (1..6).forEach { allowance ->
            // Prior week is fully active. This week misses its first N days.
            val dates = (28..30).map { timestamp(2026, 9, it) } +
                (1..4).map { timestamp(2026, 10, it) } +
                ((5 + allowance)..11).map { timestamp(2026, 10, it) }
            val active = dates.map { formatter.format(it) }.toSet()
            assertEquals("Allowance $allowance", 14 - allowance, analytics.streak(active, allowance))
            assertEquals("Exceeded allowance ${allowance - 1}", 7 - allowance,
                analytics.streak(active, allowance - 1))
        }
    }

    @Test
    fun `rest allowance resets on Monday but never on January first`() {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        // Sunday Oct 11 and Monday Oct 12 are separate weeks' rest days.
        val october = DashboardAnalytics { timestamp(2026, 10, 13) }
        val octoberDates = ((5..10).map { timestamp(2026, 10, it) } + timestamp(2026, 10, 13))
            .map { formatter.format(it) }.toSet()
        assertEquals(1, october.streak(octoberDates, 0))
        assertEquals(7, october.streak(octoberDates, 1))

        // Dec 30 and Dec 31 are in Jan 1's week: one rest day cannot cover both.
        val january = DashboardAnalytics { timestamp(2026, 1, 1) }
        val januaryDates = ((22..29).map { timestamp(2025, 12, it) } + timestamp(2026, 1, 1))
            .map { formatter.format(it) }.toSet()
        assertEquals(1, january.streak(januaryDates, 1))
        assertEquals(9, january.streak(januaryDates, 2))
    }

    @Test
    fun `empty and future activity cannot contribute and a wholly missed week resets allowances below seven`() {
        val analytics = DashboardAnalytics { timestamp(2026, 10, 13) }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val future = setOf("2026-10-14", "2026-11-01")
        (0..7).forEach {
            assertEquals(0, analytics.streak(emptySet(), it))
            assertEquals(0, analytics.streak(future, it))
        }
        val oldActivity = (1..4).map { formatter.format(timestamp(2026, 10, it)) }.toSet()
        (0..6).forEach { assertEquals(0, analytics.streak(oldActivity, it)) }
        assertEquals(4, analytics.streak(oldActivity + future, 7))
    }

    @Test
    fun `increasing rest days never decreases streak for every recent week pattern and weekday`() {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        (0..6).forEach { weekday ->
            val now = timestamp(2026, 10, 12 + weekday)
            val analytics = DashboardAnalytics { now }
            val dates = (0..13).map { daysAgo ->
                Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }.let { formatter.format(it.time) }
            }
            (0 until 128).forEach { pattern ->
                val active = ((0..6).filter { pattern and (1 shl it) != 0 }.map { dates[it] } + dates.drop(7)).toSet()
                val streaks = (0..7).map { analytics.streak(active, it) }
                assertTrue("Weekday $weekday, pattern $pattern: $streaks",
                    streaks.zipWithNext().all { (strict, relaxed) -> relaxed >= strict })
                assertTrue(streaks.all { it in 0..active.size })
            }
        }
    }

    @Test
    fun `weekly calendar uses local calendar boundaries across both daylight saving changes`() {
        listOf(Triple(3, 23, 30), Triple(10, 19, 26)).forEach { (month, monday, nextMonday) ->
            val analytics = DashboardAnalytics { timestamp(2026, month, monday + 2) }
            val logs = listOf(
                meal(1, timestamp(2026, month, monday - 1, 23, 59)),
                meal(2, timestamp(2026, month, monday, 0)),
                meal(3, timestamp(2026, month, nextMonday - 1, 23, 59)),
                meal(4, timestamp(2026, month, nextMonday, 0))
            )
            val nutrition = analytics.nutritionHistory(logs)
            val sessions = analytics.sessionHistory(logs.map {
                WorkoutSessionEntity(id = it.id, workoutId = 1, timestamp = it.timestamp, isCompleted = true)
            })
            assertEquals(setOf(1, 7), nutrition.weeklyDays.keys)
            assertEquals(setOf(1, 7), sessions.weeklyWorkoutIds.keys)
            assertEquals(2, sessions.weeklySessionCount)
            assertEquals(4, nutrition.calendar.size)
            assertEquals(4, sessions.calendarWorkoutIds.size)
        }
    }

    @Test
    fun `zero rest days count unique consecutive days ignore future records and stop at yesterday gap`() {
        val now = timestamp(2026, 10, 11)
        val analytics = DashboardAnalytics { now }
        val logs = (1..9).map { meal(it, timestamp(2026, 10, it)) } + listOf(
            meal(11, now), meal(12, timestamp(2026, 10, 11, 18)),
            meal(13, timestamp(2026, 10, 12)), meal(14, timestamp(2026, 9, 30))
        )
        val nutrition = analytics.nutritionHistory(logs)
        assertEquals(1, analytics.streak(nutrition.calendar.keys, 0))

        val continuous = analytics.nutritionHistory(logs + meal(10, timestamp(2026, 10, 10)))
        assertEquals(12, analytics.streak(continuous.calendar.keys, 0))
        val futureOnly = analytics.nutritionHistory(listOf(meal(1, timestamp(2026, 10, 12))))
        assertEquals(0, analytics.streak(futureOnly.calendar.keys, 0))
    }

    @Test
    fun `today remains unfinished but missing a completed day resets the daily streak`() {
        var now = timestamp(2026, 10, 11)
        val analytics = DashboardAnalytics { now }
        val sessions = analytics.sessionHistory((1..10).map {
            WorkoutSessionEntity(id = it, workoutId = 1, timestamp = timestamp(2026, 10, it), isCompleted = true)
        } + WorkoutSessionEntity(id = 11, workoutId = 1, timestamp = timestamp(2026, 10, 10, 18), isCompleted = true))
        assertEquals(10, analytics.streak(sessions.calendarWorkoutIds.keys, 0))
        now = timestamp(2026, 10, 12, 0)
        assertEquals(0, analytics.streak(sessions.calendarWorkoutIds.keys, 0))
    }

    @Test
    fun `all rest settings handle continuous activity and gaps across year and daylight saving boundaries`() {
        val dateRuns = listOf(
            listOf(timestamp(2025, 12, 30), timestamp(2025, 12, 31), timestamp(2026, 1, 1)),
            (27..30).map { timestamp(2026, 3, it) },
            (23..26).map { timestamp(2026, 10, it) }
        )
        dateRuns.forEach { dates ->
            val analytics = DashboardAnalytics { dates.last() }
            val nutrition = analytics.nutritionHistory(dates.mapIndexed { i, time -> meal(i + 1, time) })
            (0..7).forEach { allowance ->
                assertEquals(dates.size, analytics.streak(nutrition.calendar.keys, allowance))
            }
            val gappedDates = dates.filterIndexed { index, _ -> index != 1 }
            val gapped = analytics.nutritionHistory(gappedDates.mapIndexed { i, time -> meal(i + 1, time) })
            assertEquals(dates.size - 2, analytics.streak(gapped.calendar.keys, 0))
            (1..7).forEach { allowance ->
                assertEquals(dates.size - 1, analytics.streak(gapped.calendar.keys, allowance))
            }
        }
        val spring = DashboardAnalytics { timestamp(2026, 3, 29) }.currentDayBounds()
        val autumn = DashboardAnalytics { timestamp(2026, 10, 25) }.currentDayBounds()
        assertEquals(23 * 3_600_000L, spring.second - spring.first)
        assertEquals(25 * 3_600_000L, autumn.second - autumn.first)
    }

    @Test
    fun `charts preserve totals chronological order labels and winning weight reps for estimated one rep max`() {
        val analytics = DashboardAnalytics { timestamp(2026, 10, 14) }
        val monday = timestamp(2026, 10, 12)
        val tuesday = timestamp(2026, 10, 13)
        fun set(id: Int, exercise: Int, weight: Float, reps: Int, time: Long) = SetLogEntity(
            id = id, sessionId = 1, exerciseId = exercise, setNumber = id,
            weight = weight, reps = reps, isCompleted = true, timestamp = time
        )
        val logs = listOf(
            set(1, 1, 105f, 2, tuesday), // Deliberately unsorted timestamps.
            set(2, 1, 100f, 3, monday),
            set(3, 1, 100f, 5, monday),
            set(4, 1, 80f, 20, monday), // Greater estimated 1RM, but not the max-weight winner.
            set(5, 2, 20f, 10, monday),
            set(6, 99, 12f, 5, monday) // Orphan metadata: exercise/global charts retain it.
        )
        val history = analytics.setHistory(logs)
        assertEquals(2_870f, history.weeklyVolumes.single().totalVolume, 0f)
        assertEquals(timestamp(2026, 10, 12, 0), history.weeklyVolumes.single().weekStartTimestamp)
        val routine = analytics.workoutVolumeTrends(history, mapOf(1 to 1, 2 to 1)).getValue(1)
        assertEquals(listOf(2_600f, 210f), routine.map { it.yValue })
        assertEquals(listOf(timestamp(2026, 10, 12, 0), timestamp(2026, 10, 13, 0)), routine.map { it.xValue })
        assertEquals("2600 kg\nOct 12", routine.first().tooltipLabel)
        val exercise = analytics.exerciseCharts(logs.filter { it.exerciseId == 1 })
        assertEquals(listOf(2_400f, 210f), exercise.volumeTrend.map { it.yValue })
        val maximum = exercise.maxWeightTrend.first()
        assertEquals(100f, maximum.yValue, 0f)
        assertEquals(100f * (1 + 5 / 30f), maximum.extraValue, 0f)
        assertEquals("100.0 kg\nOct 12", maximum.tooltipLabel)
        assertTrue(analytics.exerciseCharts(logs.filter { it.exerciseId == 99 }).volumeTrend.isNotEmpty())
    }

    @Test
    fun `session calendar uses session start while charts use set timestamps and preserve missing names`() {
        val analytics = DashboardAnalytics { timestamp(2026, 10, 14) }
        val sessionTime = timestamp(2026, 10, 11, 23, 59)
        val setTime = timestamp(2026, 10, 12, 0)
        val sessions = analytics.sessionHistory(listOf(
            WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = sessionTime, isCompleted = true),
            WorkoutSessionEntity(id = 2, workoutId = 1, timestamp = sessionTime, isCompleted = true),
            WorkoutSessionEntity(id = 3, workoutId = 99, timestamp = sessionTime, isCompleted = true)
        ))
        val nutrition = analytics.nutritionHistory(listOf(meal(1, setTime), meal(2, sessionTime)))
        val calendar = analytics.mergeCalendar(nutrition.calendar, analytics.workoutCalendar(sessions, mapOf(1 to "Push")))
        assertEquals(0, sessions.weeklySessionCount)
        assertTrue(calendar.getValue("2026-10-11").hasWorkout)
        assertEquals(listOf("Push"), calendar.getValue("2026-10-11").workoutNames)
        assertEquals(100, calendar.getValue("2026-10-11").nutritionCalories)
        assertFalse(calendar.getValue("2026-10-12").hasWorkout)
        assertTrue(calendar.getValue("2026-10-12").hasNutrition)
        val sets = analytics.exerciseCharts(listOf(SetLogEntity(
            id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, weight = 100f,
            reps = 10, isCompleted = true, timestamp = setTime
        )))
        assertEquals(setTime, sets.volumeTrend.single().xValue)
    }

    @Test
    fun `existing calendar year ISO week collision remains unchanged in performance refactor`() {
        val analytics = DashboardAnalytics { timestamp(2024, 12, 30) }
        // The legacy key uses calendar year rather than ISO week year: both are 2024-W1.
        val logs = listOf(timestamp(2024, 1, 1), timestamp(2024, 12, 30)).mapIndexed { i, time ->
            SetLogEntity(id = i + 1, sessionId = 1, exerciseId = 1, setNumber = 1,
                weight = 100f, reps = 10, isCompleted = true, timestamp = time)
        }
        val history = analytics.setHistory(logs)
        assertEquals(2_000f, history.weeklyVolumes.single().totalVolume, 0f)
        assertEquals(timestamp(2024, 1, 1, 0), history.weeklyVolumes.single().weekStartTimestamp)
        assertEquals(2, analytics.exerciseCharts(logs).volumeTrend.size)
    }
}
