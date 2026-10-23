package com.example.baselift.helpers

import androidx.lifecycle.viewModelScope
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

// Existing command/selection tests represent a visible screen. Scope disposal cancels its observer.
fun WorkoutViewModel.observeForTest() {
    viewModelScope.launch { uiState.collect {} }
}
