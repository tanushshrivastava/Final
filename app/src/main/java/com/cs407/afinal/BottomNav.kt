package com.cs407.afinal

import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val items = listOf(
        BottomNavItem(
            route = "alarm",
            label = "Alarm",
            icon = { Icon(Icons.Outlined.Alarm, contentDescription = "Alarm") }
        ),
        BottomNavItem(
            route = "schedule",
            label = "Schedule",
            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Schedule") }
        ),
        BottomNavItem(
            route = "account",
            label = "Account",
            icon = { Icon(Icons.Outlined.Person, contentDescription = "Account") }
        )
    )

    Scaffold(
        bottomBar = {
            BottomBar(
                items = items,
                navController = navController
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "alarm",
            modifier = modifier.padding(innerPadding)
        ) {
            composable("alarm") {
                SleepCalculatorScreen()
            }
            composable("schedule") {
                ScheduleScreen()
            }
            composable("account") {
                AccountScreen()
            }
        }
    }
}

@Composable
private fun BottomBar(
    items: List<BottomNavItem>,
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == item.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Avoid building up a large back stack when switching tabs
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = item.icon,
                label = { Text(item.label) }
            )
        }
    }
}
