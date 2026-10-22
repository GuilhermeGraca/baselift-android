package com.example.baselift.View.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.baselift.R
import com.example.baselift.View.components.DelayedPendingSpinner
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.onboarding.LoadedProfile
import com.example.baselift.ViewModel.onboarding.ProfileLoadState

internal fun profileRedirect(hasUser: Boolean?, isRecalibrating: Boolean, route: String?): String? = when {
    hasUser == null || route == null -> null
    !hasUser && route in listOf(Routes.DASHBOARD, Routes.WORKOUT, Routes.NUTRITION, Routes.INSIGHTS) -> Routes.ONBOARDING
    hasUser && !isRecalibrating && route == Routes.ONBOARDING -> Routes.INSIGHTS
    else -> null
}

@Composable
internal fun ProfileLoadGate(
    state: ProfileLoadState,
    onRetry: () -> Unit,
    content: @Composable (LoadedProfile) -> Unit
) {
    val profile = state.profile
    if (profile != null) {
        content(profile)
        if (state.hasError) {
            // Keep the graph composed so a read failure cannot discard navigation or form state.
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.profile_load_error_title)) },
                text = { Text(stringResource(R.string.profile_load_error_message)) },
                confirmButton = { ProfileRetryButton(state.isLoading, onRetry) }
            )
        }
    } else if (state.hasError) {
        Column(
            Modifier.fillMaxSize().background(PureBlack).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.profile_load_error_title), color = CrystalWhite)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.profile_load_error_message), color = MediumGrey)
            Spacer(Modifier.height(16.dp))
            ProfileRetryButton(state.isLoading, onRetry)
        }
    } else {
        PremiumSplashScreen()
    }
}

@Composable
private fun ProfileRetryButton(isLoading: Boolean, onRetry: () -> Unit) {
    val retryLabel = stringResource(R.string.dash_retry)
    val loadingLabel = stringResource(R.string.profile_loading)
    TextButton(
        onClick = onRetry,
        enabled = !isLoading,
        modifier = Modifier.semantics {
            contentDescription = retryLabel
            if (isLoading) stateDescription = loadingLabel
        }
    ) {
        if (isLoading) {
            DelayedPendingSpinner(
                color = NeonGreen,
                modifier = Modifier.widthIn(min = 56.dp).height(20.dp),
                spinnerModifier = Modifier.testTag("profile-retry-pending-spinner")
            ) { Text(retryLabel) }
        } else {
            Text(retryLabel)
        }
    }
}
