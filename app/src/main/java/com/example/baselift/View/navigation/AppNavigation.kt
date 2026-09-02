package com.example.baselift.View.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.baselift.AppContainer
import com.example.baselift.View.dashboard.DashboardScreen
import com.example.baselift.View.insights.InsightsScreen
import com.example.baselift.View.onboarding.CustomTargetsScreen
import com.example.baselift.View.onboarding.OnboardingScreen
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import com.example.baselift.ViewModel.onboarding.OnboardingViewModelFactory
import com.example.baselift.ViewModel.progress.ProgressViewModel
import com.example.baselift.ViewModel.progress.ProgressViewModelFactory
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.ViewModel.workout.WorkoutViewModelFactory
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import com.example.baselift.ViewModel.nutrition.NutritionViewModelFactory
import com.example.baselift.View.nutrition.NutritionScreen
import com.example.baselift.ViewModel.dashboard.DashboardViewModel
import com.example.baselift.ViewModel.dashboard.DashboardViewModelFactory

/**
 * Rotas da aplicação
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val CUSTOM_TARGETS = "custom_targets"
    const val DASHBOARD = "dashboard"
    const val WORKOUT = "workout"
    const val NUTRITION = "nutrition"
    const val INSIGHTS = "insights"
}

/**
 * Componente que gere a navegação
 * define os ecrãs e as transições
 */
@Composable
fun AppNavigation(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // tabs principais na navegação inferior
    val mainTabs = listOf(Routes.DASHBOARD, Routes.WORKOUT, Routes.NUTRITION, Routes.INSIGHTS)
    val showBottomBar = currentRoute in mainTabs

    // viewModel partilhado para onboarding e insights
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(appContainer.userRepository, appContainer.progressRepository)
    )

    val progressViewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModelFactory(appContainer.progressRepository, appContainer.userRepository)
    )

    val workoutViewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModelFactory(appContainer.workoutRepository)
    )

    val nutritionViewModel: NutritionViewModel = viewModel(
        factory = NutritionViewModelFactory(appContainer.nutritionRepository, appContainer.userRepository)
    )

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(appContainer.workoutRepository, appContainer.nutritionRepository)
    )

    val isLoaded by onboardingViewModel.isLoaded.collectAsStateWithLifecycle()
    val isRecalibrating by onboardingViewModel.isRecalibrating.collectAsStateWithLifecycle()
    val hasUser by onboardingViewModel.hasUser.collectAsStateWithLifecycle()
    val userState by progressViewModel.user.collectAsStateWithLifecycle()
    var resetType by remember { mutableStateOf<ResetType?>(null) }

    // redirecionar para onboarding quando os dados são eliminados
    LaunchedEffect(isLoaded, userState) {
        if (isLoaded && userState == null && navController.currentDestination?.route != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (!isLoaded) {
        PremiumSplashScreen()
    } else {
        val startDestination = remember(isLoaded, hasUser, isRecalibrating) {
            when {
                isRecalibrating -> Routes.ONBOARDING  // utilizador clicou explicitamente em "Recalibrate"
                hasUser -> Routes.DASHBOARD            // utilizador já existe, ir direto para o dashboard
                else -> Routes.ONBOARDING              // primeiro uso, sem dados
            }
        }
        Scaffold(
            topBar = {
                if (showBottomBar) {
                    TopHeaderBar(
                        profilePhotoUri = userState?.profilePhotoUri,
                        onResetClick = { type -> resetType = type },
                        onProfileClick = {
                            navController.navigate(Routes.INSIGHTS) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            if (resetType != null) {
                val title = when (resetType) {
                    ResetType.ALL -> "ELIMINAR TODOS OS DADOS"
                    ResetType.WORKOUT -> "ELIMINAR DADOS DE WORKOUT"
                    ResetType.NUTRITION -> "ELIMINAR DADOS DE NUTRIÇÃO"
                    null -> ""
                }
                val message = when (resetType) {
                    ResetType.ALL -> "Tem a certeza que deseja eliminar todos os seus dados? Esta ação é completamente irreversível e irá apagar todo o seu histórico de pesos, fotografias e baseline do utilizador."
                    ResetType.WORKOUT -> "Tem a certeza que deseja eliminar todos os dados de treino? Esta ação irá apagar todos os seus exercícios, rotinas e histórico de treinos de forma irreversível."
                    ResetType.NUTRITION -> "Tem a certeza que deseja eliminar todos os dados de nutrição? Esta ação irá apagar todos os seus registos diários e refeições de forma irreversível."
                    null -> ""
                }

                ResetConfirmationDialog(
                    title = title,
                    message = message,
                    onDismiss = { resetType = null },
                    onConfirm = {
                        when (resetType) {
                            ResetType.ALL -> progressViewModel.deleteAllData()
                            ResetType.WORKOUT -> workoutViewModel.deleteAllWorkouts()
                            ResetType.NUTRITION -> nutritionViewModel.deleteAllNutrition()
                            else -> {}
                        }
                        resetType = null
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = modifier.padding(innerPadding),
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None }
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onNavigateToDashboard = {
                            navController.navigate(Routes.INSIGHTS) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                        onNavigateToCustomTargets = {
                            navController.navigate(Routes.CUSTOM_TARGETS)
                        }
                    )
                }
                
                composable(Routes.CUSTOM_TARGETS) {
                    CustomTargetsScreen(
                        viewModel = onboardingViewModel,
                        onSaveAndSync = {
                            val previousRoute = navController.previousBackStackEntry?.destination?.route
                            if (previousRoute == Routes.NUTRITION) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.INSIGHTS) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            }
                        },
                        onRevert = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Routes.DASHBOARD) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val sharedPrefs = context.getSharedPreferences("baselift_settings", android.content.Context.MODE_PRIVATE)
                            val wrDays = sharedPrefs.getInt("workout_rest_days", 4)
                            val nrDays = sharedPrefs.getInt("nutrition_rest_days", 0)
                            dashboardViewModel.setRestDays(wrDays)
                            dashboardViewModel.setNutritionRestDays(nrDays)
                        }
                    }
                    
                    val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
                    DashboardScreen(
                        uiState = dashboardUiState,
                        onSetRestDays = { days ->
                            dashboardViewModel.setRestDays(days)
                            val sharedPrefs = context.getSharedPreferences("baselift_settings", android.content.Context.MODE_PRIVATE)
                            sharedPrefs.edit().putInt("workout_rest_days", days).apply()
                        },
                        onSetNutritionRestDays = { days ->
                            dashboardViewModel.setNutritionRestDays(days)
                            val sharedPrefs = context.getSharedPreferences("baselift_settings", android.content.Context.MODE_PRIVATE)
                            sharedPrefs.edit().putInt("nutrition_rest_days", days).apply()
                        }
                    )
                }
                
                composable(Routes.WORKOUT) {
                    WorkoutScreen(viewModel = workoutViewModel)
                }
                
                composable(Routes.NUTRITION) {
                    NutritionScreen(
                        viewModel = nutritionViewModel,
                        onNavigateToCustomTargets = {
                            navController.navigate(Routes.CUSTOM_TARGETS)
                        }
                    )
                }
                
                composable(Routes.INSIGHTS) {
                    InsightsScreen(
                        onboardingViewModel = onboardingViewModel,
                        progressViewModel = progressViewModel,
                        onRecalibrate = {
                            onboardingViewModel.startRecalibration()
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class ResetType { ALL, WORKOUT, NUTRITION }

@Composable
fun TopHeaderBar(
    profilePhotoUri: String?,
    onResetClick: (ResetType) -> Unit,
    onProfileClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(PureBlack)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Profile Photo (Avatar)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DeepCharcoal)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!profilePhotoUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = profilePhotoUri,
                        contentDescription = "Profile Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "No Profile Photo",
                        tint = MediumGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // App Name Title
            Text(
                text = "BASELIFT",
                color = CrystalWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            // Settings Icon and DropdownMenu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MediumGrey,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(DeepCharcoal)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Eliminar dados de workout",
                                color = CrystalWhite,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onResetClick(ResetType.WORKOUT)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Eliminar dados de nutrição",
                                color = CrystalWhite,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onResetClick(ResetType.NUTRITION)
                        }
                    )
                    Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Eliminar todos os dados",
                                color = SoftCoral,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onResetClick(ResetType.ALL)
                        }
                    )
                }
            }
        }
        Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
    }
}

@Composable
fun ResetConfirmationDialog(
    title: String = "ELIMINAR TODOS OS DADOS",
    message: String = "Tem a certeza que deseja eliminar todos os seus dados? Esta ação é completamente irreversível e irá apagar todo o seu histórico de pesos, fotografias e baseline do utilizador.",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PureBlack)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        color = SoftCoral,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = CrystalWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCELAR", color = MediumGrey, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ELIMINAR", color = PureBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BaseLift",
                color = NeonGreen,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            CircularProgressIndicator(
                color = NeonGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
