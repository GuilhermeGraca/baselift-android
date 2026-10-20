package com.example.baselift.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.R
import com.example.baselift.View.dashboard.components.ConsistencyJournalSection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class DashboardCalendarDemandTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calendarRequestsOnlyItsVisibleMonthAndReleasesDemandOnClose() {
        val months = CopyOnWriteArrayList<Long?>()
        compose.setContent { MaterialTheme { Column {
            ConsistencyJournalSection(
                nutritionStreak = 0, workoutStreak = 0,
                nutritionDaysThisWeek = emptyMap(), workoutDaysThisWeek = emptyMap(),
                workoutSessionsThisWeek = 0, historicalCalendarData = emptyMap(),
                restDays = 4, nutritionRestDays = 0,
                onCalendarMonthChanged = { months += it }
            )
        } } }
        compose.runOnIdle { assertTrue(months.isEmpty()) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.dash_view_calendar)).performClick()
        compose.waitUntil(5_000) { months.isNotEmpty() }
        compose.runOnIdle { assertEquals("Initial month callbacks: $months", 1, months.size) }
        val expected = Calendar.getInstance().apply { timeInMillis = months.first()!!; add(Calendar.MONTH, -1) }
        compose.onNodeWithContentDescription("Previous Month").performClick()
        compose.waitUntil(5_000) { months.size == 2 }
        compose.runOnIdle {
            val shown = Calendar.getInstance().apply { timeInMillis = months.last()!! }
            assertEquals(expected.get(Calendar.YEAR), shown.get(Calendar.YEAR))
            assertEquals(expected.get(Calendar.MONTH), shown.get(Calendar.MONTH))
            assertEquals(1, shown.get(Calendar.DAY_OF_MONTH))
        }
        onView(isRoot()).inRoot(isDialog()).perform(pressBack())
        compose.waitUntil(5_000) { months.lastOrNull() == null }
        compose.runOnIdle { assertEquals(3, months.size) }
    }
}
