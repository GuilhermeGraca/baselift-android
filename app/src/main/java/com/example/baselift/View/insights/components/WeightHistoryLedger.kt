package com.example.baselift.View.insights.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.R
import com.example.baselift.View.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// The caller remembers newest-first sorting once per history change. Each record is
// an item in the screen's LazyColumn, so expansion does not compose the entire history.
fun LazyListScope.TechnicalHistoryLedger(
    sortedLogs: List<WeightLogEntity>,
    isGainGoal: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRequestDelete: (WeightLogEntity) -> Unit
) {
    item(key = "weight-history-header", contentType = "weight-history-header") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_technical_history), color = MediumGrey,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NeonGreen.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(stringResource(R.string.insights_raw_data_synced), color = NeonGreen,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (sortedLogs.isEmpty()) {
        item(key = "weight-history-empty", contentType = "weight-history-empty") {
            Text(stringResource(R.string.insights_no_entries), color = MediumGrey, fontSize = 12.sp)
        }
    } else {
        items(
            count = if (expanded) sortedLogs.size else minOf(4, sortedLogs.size),
            key = { index -> "weight-history-${sortedLogs[index].id}" },
            contentType = { "weight-history-row" }
        ) { index ->
            WeightHistoryRow(
                log = sortedLogs[index],
                previousLog = sortedLogs.getOrNull(index + 1),
                isGainGoal = isGainGoal,
                faded = !expanded && index == 3,
                onRequestDelete = onRequestDelete
            )
        }
    }
    item(key = "weight-history-footer", contentType = "weight-history-footer") {
        Column(Modifier.fillMaxWidth()) {
            if (sortedLogs.size > 4) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MediumGrey.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(if (expanded) R.string.insights_load_less_data else R.string.insights_load_more_data),
                    color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun WeightHistoryRow(
    log: WeightLogEntity,
    previousLog: WeightLogEntity?,
    isGainGoal: Boolean,
    faded: Boolean,
    onRequestDelete: (WeightLogEntity) -> Unit
) {
    val delta = if (previousLog != null) log.weightValue - previousLog.weightValue else 0f
    val deltaText = remember(delta) {
        if (delta > 0) "+${String.format(Locale.US, "%.1f", delta)}" else String.format(Locale.US, "%.1f", delta)
    }
    val deltaColor = when {
        delta > 0 -> if (isGainGoal) NeonGreen else SoftCoral
        delta < 0 -> if (isGainGoal) SoftCoral else NeonGreen
        else -> MediumGrey
    }
    val zone = TimeZone.getDefault()
    val dateLabel = remember(log.timestamp, zone.id) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).apply { timeZone = zone }
            .format(Date(log.timestamp)).uppercase(Locale.US)
    }
    var isPressed by remember(log.id) { mutableStateOf(false) }
    val currentLog by rememberUpdatedState(log)
    val currentOnRequestDelete by rememberUpdatedState(onRequestDelete)
    val animatedBgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f, animationSpec = tween(600), label = "weightHistoryPress"
    )
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (faded) 0.4f else 1f)
            .background(Brush.horizontalGradient(listOf(Color.Transparent,
                SoftCoral.copy(alpha = animatedBgAlpha), Color.Transparent)))
            .pointerInput(log.id) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try { tryAwaitRelease() } finally { isPressed = false }
                    },
                    onLongPress = {
                        isPressed = false
                        currentOnRequestDelete(currentLog)
                    }
                )
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(dateLabel, color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("ID: ${log.id}-${log.timestamp.toString().takeLast(4)}", color = MediumGrey, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${log.weightValue} KG", color = CrystalWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (previousLog != null) {
                Text("$deltaText KG DELTA", color = deltaColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Owned by the screen, independently of the row's presence in the lazy viewport.
@Composable
fun WeightHistoryDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PureBlack)
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(24.dp)
        ) {
            Text(stringResource(R.string.insights_delete_entry), color = SoftCoral, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.insights_delete_weight_record_msg), color = CrystalWhite, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.workout_cancel), color = MediumGrey)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                    shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.workout_delete), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
