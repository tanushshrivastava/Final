package com.cs407.afinal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun SmartSleepApp() {
    val navController = rememberNavController()
    val destinations = remember { listOf(BottomDestination.Planner, BottomDestination.Trends, BottomDestination.Account) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomDestination.Planner.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(BottomDestination.Planner.route) {
                SleepCalculatorScreen()
            }
            composable(BottomDestination.Trends.route) {
                TrendsScreen()
            }
            composable(BottomDestination.Account.route) {
                AccountScreen()
            }
        }
    }
}

private sealed class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Planner : BottomDestination("planner", "Planner", Icons.Outlined.Alarm)
    object Trends : BottomDestination("trends", "Trends", Icons.Outlined.Leaderboard)
    object Account : BottomDestination("account", "Account", Icons.Outlined.AccountCircle)
}