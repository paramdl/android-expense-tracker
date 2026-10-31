package com.expensetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.BuildConfig
import com.expensetracker.data.Backup
import com.expensetracker.ui.screens.AddEditScreen
import com.expensetracker.ui.screens.DashboardScreen
import com.expensetracker.ui.screens.ImportScreen
import com.expensetracker.ui.screens.ManageScreen
import com.expensetracker.ui.screens.RecurringEditScreen
import com.expensetracker.ui.screens.RecurringScreen
import com.expensetracker.ui.screens.ReportsScreen
import com.expensetracker.ui.screens.SettingsScreen
import com.expensetracker.ui.screens.SmsScreen
import com.expensetracker.ui.screens.TransactionsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// The SMS tab only exists in the "full" edition; the lite build ships without
// SMS permissions, so showing the tab would offer a feature that cannot work.
private val TABS = listOfNotNull(
    Tab("home", "Home", Icons.Default.Home),
    Tab("transactions", "Ledger", Icons.Default.List),
    Tab("reports", "Reports", Icons.Default.DateRange),
    if (BuildConfig.SMS_CAPTURE) Tab("sms", "SMS", Icons.Default.Email) else null,
    Tab("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = TABS.any { it.route == route }

    val quick by vm.quickAction.collectAsStateWithLifecycle()
    LaunchedEffect(quick) {
        when (quick) {
            AppViewModel.Quick.ADD -> nav.navigate("edit/0")
            AppViewModel.Quick.IMPORT -> nav.navigate("import")
            null -> Unit
        }
        if (quick != null) vm.setQuickAction(null)
    }

    val shared by vm.sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null && route != "import") nav.navigate("import")
    }

    val undoable by vm.undoable.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { text ->
            // Deleting is the one destructive action reachable in a single tap,
            // so it gets a way back.
            val canUndo = undoable && text.contains("deleted", ignoreCase = true)
            val outcome = snackbar.showSnackbar(
                message = text,
                actionLabel = if (canUndo) "Undo" else null,
                withDismissAction = false
            )
            if (outcome == SnackbarResult.ActionPerformed) vm.undoDelete()
            vm.clearMessage()
        }
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_CSV)
    ) { uri -> uri?.let { vm.writeCsvTo(it) } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        // An opaque band behind the status bar. Without it the list scrolls
        // visibly under the clock and battery, which reads as a glitch.
        topBar = {
            if (onTab) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                if (route != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (tab.route == "sms" && pending.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(pending.size.toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == "home" || route == "transactions") {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate("edit/0") },
                    text = { Text("Add") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
            }
        }
    ) { scaffoldPadding ->
        val dir = LocalLayoutDirection.current
        // Tab screens draw their own horizontal gutter; keep the scaffold's vertical insets.
        val listPadding = PaddingValues(
            start = 16.dp + scaffoldPadding.calculateStartPadding(dir),
            end = 16.dp + scaffoldPadding.calculateEndPadding(dir),
            top = scaffoldPadding.calculateTopPadding() + 4.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 88.dp
        )

        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            // The default slide transitions make every tab switch feel laggy on
            // a mid-range phone; switching tabs should be immediate.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("home") {
                DashboardScreen(
                    vm = vm,
                    onOpenTx = { nav.navigate("edit/" + it) },
                    onSeeAll = { nav.navigate("transactions") },
                    onOpenSms = { nav.navigate("sms") },
                    onOpenReports = { nav.navigate("reports") },
                    onOpenRecurring = { nav.navigate("recurring") },
                    onOpenBudgets = { nav.navigate("manage/categories") },
                    contentPadding = listPadding
                )
            }
            composable("import") {
                ImportScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("recurring") {
                RecurringScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("recurringEdit/" + it) }
                )
            }
            composable(
                route = "recurringEdit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                RecurringEditScreen(
                    vm = vm,
                    ruleId = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable("transactions") {
                TransactionsScreen(
                    vm = vm,
                    onOpenTx = { nav.navigate("edit/" + it) },
                    contentPadding = listPadding
                )
            }
            composable("reports") {
                ReportsScreen(
                    vm = vm,
                    onExportCsv = { exportCsv.launch(Backup.defaultCsvName()) },
                    contentPadding = listPadding
                )
            }
            composable("sms") {
                SmsScreen(vm = vm, contentPadding = listPadding)
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onManage = { nav.navigate("manage/" + it) },
                    onOpenRecurring = { nav.navigate("recurring") },
                    onOpenImport = { nav.navigate("import") },
                    contentPadding = listPadding
                )
            }
            composable(
                route = "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                AddEditScreen(
                    vm = vm,
                    txId = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable(
                route = "manage/{kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType })
            ) { entry ->
                ManageScreen(
                    vm = vm,
                    kind = entry.arguments?.getString("kind") ?: "categories",
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

@Suppress("unused")
private fun NavHostController.safePop() {
    if (previousBackStackEntry != null) popBackStack()
}
