package com.example.baselift.View.insights.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.baselift.R
import com.example.baselift.ViewModel.progress.PhotoImportResult

@Composable
fun PhotoImportFeedback(result: PhotoImportResult?, onRetry: (Long) -> Unit, onDismiss: (Long) -> Unit) {
    if (result == null || result.failedCount == 0) return
    AlertDialog(
        onDismissRequest = { onDismiss(result.id) },
        title = { Text(stringResource(if (result.wasCancelled) R.string.insights_photo_import_stopped else R.string.insights_photo_import_incomplete)) },
        text = { Text(stringResource(R.string.insights_photo_import_result, result.savedCount, result.failedCount)) },
        confirmButton = {
            TextButton(onClick = { onRetry(result.id) }) { Text(stringResource(R.string.insights_photo_import_retry)) }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(result.id) }) { Text(stringResource(R.string.insights_close)) }
        }
    )
}
