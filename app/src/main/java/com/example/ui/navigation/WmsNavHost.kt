package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.preferences.WmsPreferencesRepository
import com.example.data.repository.WmsRepository
import com.example.ui.components.WmsBottomNavigationBar
import com.example.ui.components.WmsNavigationRail
import com.example.ui.components.WmsTopAppBar
import com.example.ui.screens.admin.AdminScreen
import com.example.ui.screens.cyclecount.CycleCountScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.inventory.MaterialsScreen
import com.example.ui.screens.map.MapScreen
import com.example.ui.screens.ocr.OcrImportScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.voice.VoiceCommandScreen
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.CycleCountViewModel
import com.example.ui.viewmodel.MaterialsViewModel
import com.example.ui.viewmodel.OcrImportViewModel
import com.example.ui.viewmodel.WmsAppState
import com.example.ui.viewmodel.WmsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun WmsAppShell(
    uiState: WmsAppState,
    viewModel: WmsViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedback) {
        val fb = uiState.feedback
        if (fb != null) {
            snackbarHostState.showSnackbar(fb.message)
            viewModel.clearFeedback()
        }
    }

    val currentTopLevelRoute: WmsRoute = when {
        destination?.hasRoute<InventoryRoute>() == true -> InventoryRoute
        destination?.hasRoute<HistoryRoute>() == true -> HistoryRoute
        destination?.hasRoute<MapRoute>() == true -> MapRoute
        destination?.hasRoute<AdminRoute>() == true -> AdminRoute
        else -> DashboardRoute
    }

    val isScannerOpen = destination?.hasRoute<ScannerRoute>() == true
    val isFullScreenWorkflow = isScannerOpen ||
        destination?.hasRoute<OcrImportRoute>() == true ||
        destination?.hasRoute<CycleCountRoute>() == true ||
        destination?.hasRoute<VoiceCommandRoute>() == true

    fun navigateTopLevel(route: WmsRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                WmsTopAppBar(
                    factories = uiState.factories,
                    selectedFactoryId = uiState.selectedFactoryId,
                    language = uiState.language,
                    themeMode = uiState.themeMode,
                    userRole = uiState.activeRole,
                    onSelectFactory = viewModel::selectFactory,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onOpenScanner = {
                        if (!isScannerOpen) {
                            navController.navigate(ScannerRoute) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onSignOut = onSignOut
                )
            },
            bottomBar = {
                if (!useNavigationRail && !isFullScreenWorkflow) {
                    WmsBottomNavigationBar(
                        currentRoute = currentTopLevelRoute,
                        onNavigate = ::navigateTopLevel
                    )
                }
            },
            floatingActionButton = {
                if (!isFullScreenWorkflow) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            navController.navigate(VoiceCommandRoute) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.voice_cmd_fab)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.voice_cmd_fab),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_voice_command")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isFullScreenWorkflow) {
                    WmsNavigationRail(
                        currentRoute = currentTopLevelRoute,
                        onNavigate = ::navigateTopLevel
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = DashboardRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<DashboardRoute> {
                        DashboardScreen(
                            uiState = uiState,
                            onSelectFactory = viewModel::selectFactory,
                            onOpenScanner = {
                                navController.navigate(ScannerRoute) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenCycleCount = {
                                navController.navigate(CycleCountRoute()) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenOcrImport = {
                                navController.navigate(OcrImportRoute) {
                                    launchSingleTop = true
                                }
                            },
                            onCreateFactory = viewModel::createFactory,
                            onRegisterMaterial = viewModel::registerMaterial,
                            onExecuteStockMovement = viewModel::executeStockMovement
                        )
                    }

                    composable<InventoryRoute> {
                        val profile = uiState.userProfile
                        val materialsViewModel: MaterialsViewModel = viewModel(
                            key = "materials_${profile?.uid ?: "guest"}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val dbId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(dbId)
                                    MaterialsViewModel(
                                        repository = WmsRepository(db = db, auth = Firebase.auth),
                                        preferences = WmsPreferencesRepository(app),
                                        currentUserId = profile?.uid.orEmpty(),
                                        currentUserEmail = profile?.email.orEmpty(),
                                        currentUserName = profile?.displayName.orEmpty()
                                    )
                                }
                            }
                        )

                        LaunchedEffect(uiState.selectedFactoryId, uiState.activeFactory) {
                            materialsViewModel.syncFactoryContext(
                                factoryId = uiState.selectedFactoryId,
                                activeFactory = uiState.activeFactory
                            )
                        }

                        val materialsUiState by materialsViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(materialsUiState.actionFeedback) {
                            val fb = materialsUiState.actionFeedback
                            if (fb != null) {
                                snackbarHostState.showSnackbar(fb.message)
                                materialsViewModel.clearFeedback()
                            }
                        }

                        MaterialsScreen(
                            uiState = materialsUiState,
                            userRole = uiState.activeRole,
                            language = uiState.language,
                            viewModel = materialsViewModel
                        )
                    }

                    composable<HistoryRoute> {
                        HistoryScreen(uiState = uiState)
                    }

                    composable<MapRoute> {
                        MapScreen(
                            uiState = uiState,
                            onOpenCycleCountForPart = { part ->
                                navController.navigate(CycleCountRoute(prefillPartNumber = part)) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable<AdminRoute> {
                        val profile = uiState.userProfile
                        val adminViewModel: AdminViewModel = viewModel(
                            key = "admin_${profile?.uid ?: "guest"}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val dbId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(dbId)
                                    AdminViewModel(
                                        repository = WmsRepository(db = db, auth = Firebase.auth)
                                    )
                                }
                            }
                        )

                        LaunchedEffect(uiState.selectedFactoryId, uiState.activeRole) {
                            adminViewModel.syncContext(
                                factoryId = uiState.selectedFactoryId,
                                isAdmin = uiState.activeRole.isAdmin()
                            )
                        }

                        val adminUiState by adminViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(adminUiState.feedback) {
                            val fb = adminUiState.feedback
                            if (fb != null) {
                                snackbarHostState.showSnackbar(fb.message)
                                adminViewModel.clearFeedback()
                            }
                        }

                        AdminScreen(
                            appState = uiState,
                            adminState = adminUiState,
                            onSelectThemeMode = viewModel::setThemeMode,
                            onOpenOcrImport = {
                                navController.navigate(OcrImportRoute) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenCycleCount = {
                                navController.navigate(CycleCountRoute()) {
                                    launchSingleTop = true
                                }
                            },
                            onCreateFactory = viewModel::createFactory,
                            onUpdateUserAccess = adminViewModel::updateUserAccess
                        )
                    }

                    composable<ScannerRoute> {
                        ScannerScreen(
                            uiState = uiState,
                            onExecuteStockMovement = viewModel::executeStockMovement,
                            onOpenCycleCount = { part ->
                                navController.navigate(CycleCountRoute(prefillPartNumber = part)) {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<OcrImportRoute> {
                        val profile = uiState.userProfile
                        val ocrViewModel: OcrImportViewModel = viewModel(
                            key = "ocr_${profile?.uid ?: "guest"}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val dbId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(dbId)
                                    OcrImportViewModel(
                                        repository = WmsRepository(db = db, auth = Firebase.auth)
                                    )
                                }
                            }
                        )

                        LaunchedEffect(uiState.selectedFactoryId, uiState.activeFactory) {
                            ocrViewModel.syncFactoryContext(
                                factoryId = uiState.selectedFactoryId,
                                activeFactory = uiState.activeFactory
                            )
                        }

                        val ocrUiState by ocrViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(ocrUiState.feedback) {
                            val fb = ocrUiState.feedback
                            if (fb != null) {
                                snackbarHostState.showSnackbar(fb.message)
                                ocrViewModel.clearFeedback()
                            }
                        }

                        OcrImportScreen(
                            uiState = ocrUiState,
                            viewModel = ocrViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<CycleCountRoute> { entry ->
                        val route = entry.toRoute<CycleCountRoute>()
                        val profile = uiState.userProfile
                        val cycleCountViewModel: CycleCountViewModel = viewModel(
                            key = "cycle_count_${profile?.uid ?: "guest"}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val dbId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(dbId)
                                    CycleCountViewModel(
                                        repository = WmsRepository(db = db, auth = Firebase.auth),
                                        currentUserId = profile?.uid.orEmpty(),
                                        currentUserEmail = profile?.email.orEmpty(),
                                        currentUserName = profile?.displayName.orEmpty()
                                    )
                                }
                            }
                        )

                        LaunchedEffect(uiState.selectedFactoryId) {
                            cycleCountViewModel.syncFactory(uiState.selectedFactoryId)
                        }

                        LaunchedEffect(uiState.selectedFactoryId, route.prefillPartNumber) {
                            if (route.prefillPartNumber.isNotBlank() && uiState.selectedFactoryId.isNotBlank()) {
                                cycleCountViewModel.selectOrLookupPart(route.prefillPartNumber)
                            }
                        }

                        val cycleCountUiState by cycleCountViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(cycleCountUiState.feedback) {
                            val fb = cycleCountUiState.feedback
                            if (fb != null) {
                                snackbarHostState.showSnackbar(fb.message)
                                cycleCountViewModel.clearFeedback()
                            }
                        }

                        CycleCountScreen(
                            uiState = cycleCountUiState,
                            viewModel = cycleCountViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<VoiceCommandRoute> {
                        VoiceCommandScreen(
                            uiState = uiState,
                            onExecuteStockMovement = viewModel::executeStockMovement,
                            onExecuteCycleCount = viewModel::executeVoiceCycleCount,
                            onOpenCycleCountForPart = { part ->
                                navController.navigate(CycleCountRoute(prefillPartNumber = part)) {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
