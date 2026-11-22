package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.CompassSensorManager
import com.example.data.FalakPreferencesRepository
import com.example.data.firebase.FalakCloudRepository
import com.example.domain.falak.ReportScope
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutFromGoogle
import com.example.ui.components.ExportReportBottomSheet
import com.example.ui.components.MarkazSelectorBottomSheet
import com.example.ui.screens.HisabScreen
import com.example.ui.screens.IstiwakScreen
import com.example.ui.screens.KompasScreen
import com.example.ui.screens.SimulasiUfukScreen
import com.example.ui.theme.HilalCheckerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_HISAB = "hisab"
const val ROUTE_SIMULASI = "simulasi"
const val ROUTE_KOMPAS = "kompas"
const val ROUTE_ISTIWAK = "istiwak"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    NavDestinationItem(
        route = ROUTE_HISAB,
        labelRes = R.string.tab_hisab,
        selectedIcon = Icons.Filled.Brightness3,
        unselectedIcon = Icons.Outlined.Brightness3,
        testTag = "nav_tab_hisab"
    ),
    NavDestinationItem(
        route = ROUTE_SIMULASI,
        labelRes = R.string.tab_ufuk,
        selectedIcon = Icons.Filled.WbTwilight,
        unselectedIcon = Icons.Outlined.WbTwilight,
        testTag = "nav_tab_simulasi"
    ),
    NavDestinationItem(
        route = ROUTE_KOMPAS,
        labelRes = R.string.tab_kompas,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_tab_kompas"
    ),
    NavDestinationItem(
        route = ROUTE_ISTIWAK,
        labelRes = R.string.tab_istiwak,
        selectedIcon = Icons.Filled.Schedule,
        unselectedIcon = Icons.Outlined.Schedule,
        testTag = "nav_tab_istiwak"
    )
)

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun HilalCheckerRoot(
    auth: FirebaseAuth = Firebase.auth
) {
    val user by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    if (user == null) {
        HilalCheckerTheme(darkTheme = true) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val signedInUser = user!!
        val context = LocalContext.current
        val appContext = context.applicationContext
        key(signedInUser.uid) {
            val viewModel: HilalViewModel = viewModel(
                key = signedInUser.uid,
                factory = viewModelFactory {
                    initializer {
                        val db = FirebaseFirestore.getInstance(
                            appContext.getString(R.string.firestore_database_id)
                        )
                        HilalViewModel(
                            appContext = appContext,
                            prefsRepo = FalakPreferencesRepository(appContext),
                            compassManager = CompassSensorManager(appContext),
                            cloudRepo = FalakCloudRepository(db = db, auth = auth),
                            currentUserId = signedInUser.uid,
                            currentUserDisplayName = signedInUser.displayName ?: "Pengamat Falak",
                            currentUserEmail = signedInUser.email
                        )
                    }
                }
            )
            HilalCheckerApp(
                viewModel = viewModel,
                userDisplayName = signedInUser.displayName ?: "Pengamat Falak",
                userEmail = signedInUser.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HilalCheckerApp(
    viewModel: HilalViewModel,
    userDisplayName: String = "Pengamat Falak",
    userEmail: String? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val observationsState by viewModel.observationsUiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HISAB
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var showAccountDialog by remember { mutableStateOf(false) }

    fun showFeedback(message: String) {
        appScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    HilalCheckerTheme(darkTheme = state.isDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    val defaultScope = when (currentRoute) {
                                        ROUTE_ISTIWAK -> ReportScope.ISTIWAK_ONLY
                                        ROUTE_HISAB, ROUTE_SIMULASI -> ReportScope.HILAL_ONLY
                                        else -> ReportScope.COMPLETE
                                    }
                                    viewModel.openExportSheet(defaultScope)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("top_export_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = stringResource(id = R.string.cd_export_report),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = if (state.isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = if (state.isDarkTheme) "Beralih ke tema terang" else "Beralih ke tema gelap",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("top_account_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = stringResource(id = R.string.cd_account_sync),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                val label = stringResource(id = item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                val label = stringResource(id = item.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_HISAB,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            composable(ROUTE_HISAB) {
                                HisabScreen(
                                    state = state,
                                    observationsState = observationsState,
                                    onSelectMonth = viewModel::selectHijriMonth,
                                    onShiftYear = viewModel::shiftHijriYear,
                                    onSelectCriteria = viewModel::selectCriteria,
                                    onOpenMarkazSheet = { viewModel.setMarkazSheetOpen(true) },
                                    onOpenSimulation = { navigateTo(ROUTE_SIMULASI) },
                                    onLunarDayOffsetChange = viewModel::setLunarDayOffset,
                                    onExportReport = { viewModel.openExportSheet(ReportScope.COMPLETE) },
                                    onSaveObservation = { sighted, notes ->
                                        viewModel.saveObservation(sighted, notes) { res ->
                                            if (res.isSuccess) {
                                                showFeedback("Log rukyat berhasil disimpan ke Cloud Firestore.")
                                            } else {
                                                showFeedback(res.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan log rukyat.")
                                            }
                                        }
                                    },
                                    onUpdateObservation = { id, sighted, notes ->
                                        viewModel.updateObservation(id, sighted, notes) { res ->
                                            if (res.isSuccess) {
                                                showFeedback("Catatan rukyat diperbarui di Cloud.")
                                            } else {
                                                showFeedback(res.exceptionOrNull()?.localizedMessage ?: "Gagal memperbarui catatan.")
                                            }
                                        }
                                    },
                                    onDeleteObservation = { id ->
                                        viewModel.deleteObservation(id) { res ->
                                            if (res.isSuccess) {
                                                showFeedback("Log rukyat dihapus dari Cloud.")
                                            } else {
                                                showFeedback(res.exceptionOrNull()?.localizedMessage ?: "Gagal menghapus log rukyat.")
                                            }
                                        }
                                    }
                                )
                            }
                            composable(ROUTE_SIMULASI) {
                                SimulasiUfukScreen(
                                    state = state,
                                    onOffsetChange = viewModel::setHorizonOffset,
                                    onTogglePlay = viewModel::toggleHorizonPlayback,
                                    onOpenMarkazSheet = { viewModel.setMarkazSheetOpen(true) }
                                )
                            }
                            composable(ROUTE_KOMPAS) {
                                KompasScreen(
                                    state = state,
                                    onSelectMode = viewModel::setCompassTargetMode,
                                    onToggleManualOverride = viewModel::setManualCompassOverride,
                                    onManualAzimuthChange = viewModel::setManualCompassAzimuth,
                                    onOpenMarkazSheet = { viewModel.setMarkazSheetOpen(true) },
                                    onRefreshGpsDirect = { viewModel.fetchGpsLocation(closeSheetOnSuccess = false) },
                                    onPermissionDenied = viewModel::setGpsStatusMessage
                                )
                            }
                            composable(ROUTE_ISTIWAK) {
                                IstiwakScreen(
                                    state = state,
                                    onOpenMarkazSheet = { viewModel.setMarkazSheetOpen(true) },
                                    onShiftIstiwakDay = viewModel::shiftIstiwakDay,
                                    onResetIstiwakToday = viewModel::resetIstiwakDayToToday,
                                    onRefreshGpsDirect = { viewModel.fetchGpsLocation(closeSheetOnSuccess = false) },
                                    onPermissionDenied = viewModel::setGpsStatusMessage,
                                    onExportReport = { viewModel.openExportSheet(ReportScope.COMPLETE) },
                                    onShiftCalendarMonth = viewModel::shiftCalendarMonth,
                                    onResetCalendarMonth = viewModel::resetCalendarToCurrentMonth
                                )
                            }
                        }
                    }
                }
            }

            if (state.isMarkazSheetOpen) {
                MarkazSelectorBottomSheet(
                    currentMarkaz = state.markaz,
                    isFetchingGps = state.isFetchingGps,
                    gpsStatusMessage = state.gpsStatusMessage,
                    onDismiss = { viewModel.setMarkazSheetOpen(false) },
                    onSelectPreset = viewModel::selectPresetMarkaz,
                    onFetchGps = viewModel::fetchGpsLocation,
                    onPermissionDenied = viewModel::setGpsStatusMessage,
                    onSaveManual = viewModel::saveManualCoordinates
                )
            }

            if (state.isExportSheetOpen) {
                ExportReportBottomSheet(
                    state = state,
                    scope = state.exportScope,
                    onSelectScope = viewModel::setExportScope,
                    onDismiss = { viewModel.closeExportSheet() },
                    onShowMessage = ::showFeedback
                )
            }

            if (showAccountDialog) {
                AlertDialog(
                    onDismissRequest = { showAccountDialog = false },
                    title = {
                        Text(
                            text = "Akun & Sinkronisasi Cloud",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Preferensi markaz & log rukyatul hilal tersinkron ke Cloud Firestore.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAccountDialog = false
                                signOutFromGoogle(
                                    credentialManager = credentialManager,
                                    scope = appScope
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keluar")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showAccountDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Tutup")
                        }
                    }
                )
            }
        }
    }
}
