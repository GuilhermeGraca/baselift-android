package com.example.baselift.navigation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.AppContainer
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.*
import com.example.baselift.View.navigation.*
import com.example.baselift.ViewModel.onboarding.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StartupNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val restoration = StateRestorationTester(compose)
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private var database: AppDatabase? = null
    private lateinit var nav: NavHostController
    private lateinit var onboarding: OnboardingViewModel
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private class DelayedUsers(
        private val delegate: IUserRepository,
        val stallOtherReaders: Boolean = false,
        val failFirst: Boolean = false
    ) : IUserRepository by delegate {
        val firstRead = CompletableDeferred<Unit>()
        val retryRead = CompletableDeferred<Unit>()
        val observations = AtomicInteger()
        override fun getUser(): Flow<UserEntity?> = flow {
            val attempt = observations.incrementAndGet()
            if (attempt == 1) {
                firstRead.await()
                if (failFirst) throw IOException("controlled read failure")
            } else {
                if (stallOtherReaders) awaitCancellation()
                if (failFirst) retryRead.await()
            }
            emitAll(delegate.getUser())
        }
    }

    private fun prepare(user: UserEntity?, stallOtherReaders: Boolean = false, failFirst: Boolean = false): DelayedUsers {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { database = it }
        val realUsers = UserRepository(db.userDao())
        if (user != null) runBlocking { realUsers.saveUser(user) }
        val users = DelayedUsers(realUsers, stallOtherReaders, failFirst)
        val container = object : AppContainer {
            override val database = db
            override val userRepository = users
            override val progressRepository = ProgressRepository(db.weightLogDao(), db.photoLogDao())
            override val workoutRepository = WorkoutRepository(db.workoutDao(), context)
            override val nutritionRepository = NutritionRepository(db.nutritionDao())
        }
        restoration.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                MaterialTheme {
                    nav = rememberNavController()
                    onboarding = viewModel(factory = OnboardingViewModelFactory(users, container.progressRepository))
                    AppNavigation(container, navController = nav)
                }
            }
        }
        return users
    }

    private fun waitRoute(route: String) {
        compose.waitUntil(5_000) {
            var current: String? = null
            main { current = nav.currentDestination?.route }
            current == route
        }
    }

    @After fun tearDown() {
        main { store.clear() }
        database?.close()
    }

    @Test fun oneConfirmedReaderControlsStartupAndGraphSurvivesProfileChanges() {
        val saved = UserEntity(name = "Existing", weight = 70f)
        val users = prepare(saved, stallOtherReaders = true)
        compose.onNodeWithText("BaseLift").assertIsDisplayed()
        main { assertNull(nav.currentDestination) }
        users.firstRead.complete(Unit)
        waitRoute(Routes.INSIGHTS)
        compose.waitForIdle()
        // Navigation, header and Insights share one reader; any second reader would remain blocked.
        assertEquals(1, users.observations.get())
        lateinit var initialGraph: androidx.navigation.NavGraph
        main { initialGraph = nav.graph; nav.navigate(Routes.WORKOUT) }
        waitRoute(Routes.WORKOUT)
        runBlocking { users.saveUser(saved.copy(name = "Edited", profilePhotoUri = "content://test/profile")) }
        compose.waitUntil(5_000) { onboarding.profileLoadState.value.profile?.user?.name == "Edited" }
        main {
            assertSame(initialGraph, nav.graph)
            assertEquals(Routes.WORKOUT, nav.currentDestination?.route)
            onboarding.startRecalibration()
            nav.navigate(Routes.ONBOARDING)
        }
        waitRoute(Routes.ONBOARDING)
        runBlocking { users.saveUser(saved.copy(name = "While recalibrating")) }
        compose.waitUntil(5_000) { onboarding.profileLoadState.value.profile?.user?.name == "While recalibrating" }
        main { assertEquals(Routes.ONBOARDING, nav.currentDestination?.route); onboarding.saveUserProfile() }
        waitRoute(Routes.INSIGHTS)
        runBlocking { users.clearUserData() }
        waitRoute(Routes.ONBOARDING)
        main { assertSame(initialGraph, nav.graph) }
    }

    @Test fun firstUseKeepsCustomTargetsAndUsesOneGraphWhenProfileIsSaved() {
        val users = prepare(null)
        main { assertNull(nav.currentDestination) }
        users.firstRead.complete(Unit)
        waitRoute(Routes.ONBOARDING)
        lateinit var initialGraph: androidx.navigation.NavGraph
        main { initialGraph = nav.graph; nav.navigate(Routes.CUSTOM_TARGETS) }
        waitRoute(Routes.CUSTOM_TARGETS)
        main {
            onboarding.updateCustomTargets(2300, 140, 250, 70)
            onboarding.saveUserProfile()
        }
        compose.waitUntil(5_000) { onboarding.profileLoadState.value.profile?.user?.isCustomTargets == true }
        // Saving from custom targets has its own return callback; profile presence must not reset it.
        main { assertEquals(Routes.CUSTOM_TARGETS, nav.currentDestination?.route); assertSame(initialGraph, nav.graph) }
        main { nav.navigate(Routes.INSIGHTS) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
        waitRoute(Routes.INSIGHTS)
        main { assertSame(initialGraph, nav.graph) }
        assertEquals(2300, runBlocking { users.getUser().first() }!!.dailyCaloriesGoal)
        repeat(2) {
            compose.onNodeWithText("Workout").performClick()
            waitRoute(Routes.WORKOUT)
            compose.onNodeWithText("Nutrition").performClick()
            waitRoute(Routes.NUTRITION)
        }
        // Main tabs remain anchored at Insights even though the graph originally started at onboarding.
        main { assertEquals(Routes.INSIGHTS, nav.previousBackStackEntry?.destination?.route); nav.popBackStack() }
        waitRoute(Routes.INSIGHTS)
        restoration.emulateSavedInstanceStateRestore()
        waitRoute(Routes.INSIGHTS)
        main { assertEquals(Routes.ONBOARDING, nav.graph.findNode(nav.graph.startDestinationId)?.route) }
    }

    @Test fun initialReadFailureOffersOneRetryAndNeverTreatsFailureAsNewUser() {
        val users = prepare(UserEntity(name = "Recovered"), failFirst = true)
        users.firstRead.complete(Unit)
        compose.onNodeWithText("Unable to load your profile").assertIsDisplayed()
        main { assertNull(nav.currentDestination) }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5_000) { users.observations.get() == 2 }
        main { assertNull(nav.currentDestination) }
        users.retryRead.complete(Unit)
        waitRoute(Routes.INSIGHTS)
        assertEquals("Recovered", onboarding.profileLoadState.value.profile!!.user!!.name)
    }

    @Test fun readFailureAndRetryKeepComposedContentAndItsLocalDraft() {
        val saved = LoadedProfile(UserEntity(name = "Saved"))
        val state = mutableStateOf(ProfileLoadState(saved, isLoading = false))
        compose.setContent { MaterialTheme {
            ProfileLoadGate(state.value, onRetry = { state.value = state.value.copy(isLoading = true) }) {
                var draft by remember { mutableStateOf("Initial") }
                Column { TextField(value = draft, onValueChange = { draft = it }, label = { Text("Draft") }) }
            }
        } }
        compose.onNodeWithText("Initial").performTextReplacement("Edited draft")
        compose.runOnIdle { state.value = state.value.copy(hasError = true) }
        compose.onNodeWithText("Unable to load your profile").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(state.value.isLoading); state.value = ProfileLoadState(saved, isLoading = false) }
        compose.onNodeWithText("Edited draft").assertIsDisplayed()
    }
}
