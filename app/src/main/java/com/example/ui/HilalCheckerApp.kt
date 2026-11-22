package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.domain.falak.ReportScope
import com.example.ui.components.ExportReportBottomSheet
import com.example.ui.components.MarkazSelectorBottomSheet
import com.example.ui.screens.HisabScreen
import com.example.ui.screens.IstiwakScreen
import com.example.ui.screens.KompasScreen
import com.example.ui.screens.SimulasiUfukScreen
import com.example.ui.theme.HilalCheckerTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HilalCheckerApp(viewModel: HilalViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HISAB
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()

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
                                    onSelectMonth = viewModel::selectHijriMonth,
                                    onShiftYear = viewModel::shiftHijriYear,
                                    onSelectCriteria = viewModel::selectCriteria,
                                    onOpenMarkazSheet = { viewModel.setMarkazSheetOpen(true) },
                                    onOpenSimulation = { navigateTo(ROUTE_SIMULASI) },
                                    onLunarDayOffsetChange = viewModel::setLunarDayOffset,
                                    onExportReport = { viewModel.openExportSheet(ReportScope.COMPLETE) }
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
                    onShowMessage = { msg ->
                        appScope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                )
            }
        }
    }
}
