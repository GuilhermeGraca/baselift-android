package com.example.baselift.View.insights.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.baselift.Model.local.entity.WeightLogEntity
import java.util.Calendar
import java.util.TimeZone

private data class WeightDay(val year: Int, val dayOfYear: Int)
private fun Calendar.weightDay() = WeightDay(get(Calendar.YEAR), get(Calendar.DAY_OF_YEAR))

// The DAO supplies ascending weights. Keep the first match for a day, including
// equal timestamps, instead of overwriting it with a later entry in that day.
class PhotoWeightIndex(weightLogs: List<WeightLogEntity>, zone: TimeZone) {
    private val timeZone = zone.clone() as TimeZone
    private val weightsByDay: Map<WeightDay, WeightLogEntity> = buildMap {
        val calendar = Calendar.getInstance(timeZone)
        weightLogs.forEach { log ->
            calendar.timeInMillis = log.timestamp
            val day = calendar.weightDay()
            if (!containsKey(day)) put(day, log)
        }
    }

    fun weightAt(timestamp: Long): WeightLogEntity? {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        return weightsByDay[calendar.weightDay()]
    }
}

@Composable
internal fun rememberPhotoWeightIndex(weightLogs: List<WeightLogEntity>): PhotoWeightIndex {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var zoneId by remember { mutableStateOf(TimeZone.getDefault().id) }
    DisposableEffect(context, lifecycleOwner) {
        val refresh = { zoneId = TimeZone.getDefault().id }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return remember(weightLogs, zoneId) { PhotoWeightIndex(weightLogs, TimeZone.getTimeZone(zoneId)) }
}
