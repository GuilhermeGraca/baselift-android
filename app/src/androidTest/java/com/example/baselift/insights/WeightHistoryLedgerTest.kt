package com.example.baselift.insights

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.R
import com.example.baselift.View.insights.components.TechnicalHistoryLedger
import com.example.baselift.View.insights.components.WeightHistoryDeleteDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightHistoryLedgerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun log(id: Int) = WeightLogEntity(id, 80f + id, 1_780_000_000_000 + id * 86_400_000L)
    private fun label(log: WeightLogEntity) = "ID: ${log.id}-${log.timestamp.toString().takeLast(4)}"

    private fun setLedger(
        logs: State<List<WeightLogEntity>>,
        listState: LazyListState = LazyListState(),
        initiallyExpanded: Boolean = false,
        onRequestDelete: (WeightLogEntity) -> Unit = {},
        onDelete: (WeightLogEntity) -> Unit = {}
    ) {
        compose.setContent {
            var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
            var pendingId by remember { mutableStateOf<Int?>(null) }
            val sorted = remember(logs.value) { logs.value.sortedByDescending { it.timestamp } }
            val pending = sorted.firstOrNull { it.id == pendingId }
            MaterialTheme {
                LazyColumn(Modifier.height(500.dp).testTag("history"), state = listState) {
                    TechnicalHistoryLedger(sorted, isGainGoal = false, expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                        onRequestDelete = { onRequestDelete(it); pendingId = it.id })
                }
                pending?.let { log ->
                    WeightHistoryDeleteDialog(onDismiss = { pendingId = null }, onConfirm = {
                        onDelete(log)
                        pendingId = null
                    })
                }
            }
        }
    }

    @Test
    fun thousandRecordExpansionKeepsRowsLazyAndCanCollapseFromTheEnd() {
        val logs = mutableStateOf((1..1_000).map(::log))
        setLedger(logs)
        compose.onAllNodes(hasText("ID:", substring = true)).assertCountEquals(4)
        compose.onNodeWithText(label(log(996))).assertDoesNotExist()
        // The fourth preview row still compares with the fifth, hidden record.
        compose.onAllNodesWithText("+1.0 KG DELTA").assertCountEquals(4)
        compose.onNodeWithText(text(R.string.insights_load_more_data)).performClick()
        val composed = compose.onAllNodes(hasText("ID:", substring = true)).fetchSemanticsNodes().size
        assertTrue("Expanded rows should be bounded by the viewport, got $composed", composed in 5..24)
        compose.onNodeWithText(label(log(1))).assertDoesNotExist()
        compose.onNodeWithTag("history").performScrollToIndex(1_000)
        compose.onNodeWithText(label(log(1))).assertIsDisplayed()
        compose.onNodeWithTag("history").performScrollToIndex(1_001)
        compose.onNodeWithText(text(R.string.insights_load_less_data)).performClick()
        compose.onNodeWithText(text(R.string.insights_load_more_data)).assertIsDisplayed()
        compose.onNodeWithText(label(log(1))).assertDoesNotExist()
        compose.onNodeWithTag("history").performScrollToIndex(0)
        compose.onAllNodes(hasText("ID:", substring = true)).assertCountEquals(4)
    }

    @Test
    fun deletionAndUpdatesKeepLongPressAttachedToTheCorrectRecord() {
        val logs = mutableStateOf((1..6).map(::log))
        val requested = mutableListOf<WeightLogEntity>()
        val deleted = mutableListOf<WeightLogEntity>()
        setLedger(logs, onRequestDelete = { requested += it }, onDelete = { target ->
            deleted += target
            logs.value = logs.value.filterNot { it.id == target.id }
        })
        compose.onNodeWithText(label(log(6))).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.workout_delete)).performClick()
        compose.runOnIdle { assertEquals(listOf(6), deleted.map { it.id }) }
        // A different ID now occupies the first row's previous position.
        compose.onNodeWithText(label(log(5))).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(5, requested.last().id) }
        compose.onNodeWithText(text(R.string.workout_cancel)).performClick()
        val updated = log(5).copy(weightValue = 95f)
        compose.runOnIdle { logs.value = logs.value.map { if (it.id == 5) updated else it } }
        compose.onNodeWithText(label(updated)).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(updated, requested.last()) }
        compose.onNodeWithText(text(R.string.workout_delete)).performClick()
        compose.runOnIdle { assertEquals(listOf(6, 5), deleted.map { it.id }) }
        compose.onNodeWithText(text(R.string.insights_load_more_data)).assertDoesNotExist()
    }

    @Test
    fun insertAndDeleteAboveViewportPreserveTheVisibleRecordAnchor() {
        val logs = mutableStateOf((1..500).map(::log))
        val listState = LazyListState()
        setLedger(logs, listState, initiallyExpanded = true)
        compose.onNodeWithTag("history").performScrollToIndex(250)
        var anchor: Any? = null
        var offset = 0
        compose.runOnIdle {
            anchor = listState.layoutInfo.visibleItemsInfo.first().key
            offset = listState.firstVisibleItemScrollOffset
            logs.value = logs.value + log(501)
        }
        compose.runOnIdle {
            assertEquals(anchor, listState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(offset, listState.firstVisibleItemScrollOffset)
            logs.value = logs.value.filterNot { it.id == 500 }
        }
        compose.runOnIdle {
            assertEquals(anchor, listState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(offset, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun emptyAndSingleRecordHistoriesDoNotOfferExpansionOrAnInventedDelta() {
        val logs = mutableStateOf(emptyList<WeightLogEntity>())
        setLedger(logs)
        compose.onNodeWithText(text(R.string.insights_no_entries)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.insights_load_more_data)).assertDoesNotExist()
        compose.runOnIdle { logs.value = listOf(log(1)) }
        compose.onNodeWithText(label(log(1))).assertIsDisplayed()
        compose.onAllNodes(hasText("DELTA", substring = true)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.insights_load_more_data)).assertDoesNotExist()
        compose.runOnIdle { logs.value = emptyList() }
        compose.onNodeWithText(text(R.string.insights_no_entries)).assertIsDisplayed()
    }
}
