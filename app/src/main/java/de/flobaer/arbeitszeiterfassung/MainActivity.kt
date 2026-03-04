package de.flobaer.arbeitszeiterfassung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.flobaer.arbeitszeiterfassung.ui.edit.EditWorkTimeScreen
import de.flobaer.arbeitszeiterfassung.ui.settings.SettingsScreen
import de.flobaer.arbeitszeiterfassung.ui.theme.ZeiterfassungTheme


@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeiterfassungTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "main",
                    enterTransition = { fadeIn() + slideInHorizontally { it } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -it } },
                    popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
                    popExitTransition = { fadeOut() + slideOutHorizontally { it } }
                ) {
                    composable("main") {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Arbeitszeiterfassung") },
                                    actions = {
                                        IconButton(onClick = { navController.navigate("settings") }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                                        }
                                    }
                                )
                            }
                        ) { paddingValues ->
                            MainScreen(
                                paddingValues,
                                onEditWorkTime = { id -> navController.navigate("edit/$id") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("edit/{workTimeId}") { backStackEntry ->
                        val workTimeIdArg = backStackEntry.arguments?.getString("workTimeId")
                        val workTimeId = workTimeIdArg?.toLongOrNull()
                        if (workTimeId == null) {
                            navController.popBackStack()
                        } else {
                            EditWorkTimeScreen(workTimeId = workTimeId, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}