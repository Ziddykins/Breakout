package com.ziddy.breakout.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.ui.screens.AboutScreen
import com.ziddy.breakout.ui.screens.AppSettingsScreen
import com.ziddy.breakout.ui.screens.ConsoleScreen
import com.ziddy.breakout.ui.screens.DeveloperScreen
import com.ziddy.breakout.ui.screens.ForensicScreen
import com.ziddy.breakout.ui.screens.HomeScreen
import com.ziddy.breakout.ui.screens.PackagesScreen
import com.ziddy.breakout.ui.screens.PermissionAuditScreen
import com.ziddy.breakout.ui.screens.SettingsAuditScreen
import com.ziddy.breakout.ui.screens.UserPlaybooksScreen
import com.ziddy.breakout.ui.theme.AppColors
import kotlinx.coroutines.launch
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private data class DrawerDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val requiresShell: Boolean = true,
)

@Composable
fun AppNavigation(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val shellReady = shizukuState == ShizukuState.READY
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val selectedBottomRoute = when (currentRoute) {
        "permissions", "packages", "forensic", "harden", "playbooks" -> "main"
        else -> currentRoute
    }
    val bottomTabs = listOf(
        BottomTab("main", "Main", Icons.Rounded.Home),
        BottomTab("breakout", "Breakout", Icons.Rounded.Menu),
        BottomTab("console", "Console", Icons.Rounded.Terminal),
        BottomTab("settings", "Settings", Icons.Rounded.Settings),
    )
    val drawerDestinations = listOf(
        DrawerDestination("forensic", "Forensic", Icons.Rounded.Security),
        DrawerDestination("harden", "Harden", Icons.Rounded.Tune),
        DrawerDestination("packages", "Packages", Icons.Rounded.Apps),
        DrawerDestination("playbooks", "Playbooks", Icons.Rounded.AccountTree, requiresShell = false),
        DrawerDestination("permissions", "Permissions", Icons.Rounded.Key),
        DrawerDestination("about", "About", Icons.Rounded.Info, requiresShell = false),
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo("main") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AppColors.Surface,
                drawerContentColor = AppColors.TextPrimary,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(AppColors.SurfaceHigh, AppColors.Background),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = AppColors.NeonBlue.copy(alpha = 0.09f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            AppColors.NeonBlue.copy(alpha = 0.18f),
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Menu,
                                contentDescription = null,
                                tint = AppColors.NeonBlue,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    "BREAKOUT",
                                    color = AppColors.TextPrimary,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    letterSpacing = 2.5.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "DEVICE TOOLKIT",
                                    color = AppColors.NeonBlue,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.4.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    drawerDestinations.forEach { destination ->
                        val enabled = !destination.requiresShell || shellReady
                        val selected = currentRoute == destination.route
                        val shape = RoundedCornerShape(14.dp)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (enabled) 1f else 0.42f)
                                .semantics { if (!enabled) disabled() }
                                .clip(shape)
                                .clickable(enabled = enabled) {
                                    navigateTo(destination.route)
                                    scope.launch { drawerState.close() }
                                },
                            shape = shape,
                            color = if (selected) AppColors.NeonBlue.copy(alpha = 0.13f) else AppColors.Surface.copy(alpha = 0.48f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                AppColors.NeonBlue.copy(alpha = if (selected) 0.5f else 0.08f),
                            ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Icon(
                                    destination.icon,
                                    contentDescription = null,
                                    tint = if (selected) AppColors.NeonBlue else AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    destination.label,
                                    modifier = Modifier.weight(1f),
                                    color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                )
                                if (selected) {
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = RoundedCornerShape(50),
                                        color = AppColors.NeonBlue,
                                    ) {}
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.Surface.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(7.dp),
                                shape = RoundedCornerShape(50),
                                color = if (shellReady) AppColors.Green else AppColors.TextSecondary,
                            ) {}
                            Text(
                                if (shellReady) "Shell connected" else "Shell disconnected",
                                color = if (shellReady) AppColors.Green else AppColors.TextSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = AppColors.Background,
            bottomBar = {
                NavigationBar(containerColor = AppColors.Surface) {
                    bottomTabs.forEach { tab ->
                        val enabled = tab.route != "console" || shellReady
                        NavigationBarItem(
                            selected = if (tab.route == "breakout") {
                                drawerState.isOpen
                            } else {
                                selectedBottomRoute == tab.route
                            },
                            enabled = enabled,
                            onClick = {
                                if (tab.route == "breakout") {
                                    scope.launch { drawerState.open() }
                                } else {
                                    navigateTo(tab.route)
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.Amber,
                                selectedTextColor = AppColors.Amber,
                                indicatorColor = AppColors.Amber.copy(alpha = 0.15f),
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary,
                                disabledIconColor = AppColors.TextSecondary.copy(alpha = 0.35f),
                                disabledTextColor = AppColors.TextSecondary.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.padding(padding),
            ) {
                composable("main") {
                    HomeScreen(
                        viewModel = viewModel,
                        shellReady = shellReady,
                        onPermissions = { navigateTo("permissions") },
                        onPackages = { navigateTo("packages") },
                        onForensic = { navigateTo("forensic") },
                        onHarden = { navigateTo("harden") },
                        onUserPlaybooks = { navigateTo("playbooks") },
                    )
                }
                composable("permissions") { PermissionAuditScreen(viewModel) }
                composable("harden") { SettingsAuditScreen(viewModel) }
                composable("packages") { PackagesScreen(viewModel) }
                composable("playbooks") { UserPlaybooksScreen(viewModel) }
                composable("forensic") { ForensicScreen(viewModel) }
                composable("console") { ConsoleScreen(viewModel) }
                composable("settings") { AppSettingsScreen(viewModel) }
                composable("about") {
                    AboutScreen(
                        viewModel = viewModel,
                        onDeveloperUnlock = { navigateTo("developer") },
                    )
                }
                composable("developer") { DeveloperScreen() }
            }
        }
    }
}
