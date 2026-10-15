package com.tarixustoz.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tarixustoz.app.presentation.ai.AiTutorScreen
import com.tarixustoz.app.presentation.home.HomeScreen
import com.tarixustoz.app.presentation.leaderboard.LeaderboardScreen
import com.tarixustoz.app.presentation.mistakes.MistakesScreen
import com.tarixustoz.app.presentation.mistakes.MistakesViewModel
import com.tarixustoz.app.presentation.profile.ProfileScreen
import com.tarixustoz.app.presentation.quiz.QuizScreen
import com.tarixustoz.app.presentation.textbooks.TextbooksScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Bosh sahifa", Icons.Default.Home)
    data object Textbooks : Screen("textbooks", "Darsliklar", Icons.Default.Book)
    data object Quiz : Screen("quiz", "Testlar", Icons.Default.Quiz)
    data object Mistakes : Screen("mistakes", "Xatolar", Icons.Default.Warning)
    data object Leaderboard : Screen("leaderboard", "Reyting", Icons.Default.Leaderboard)
    data object AiTutor : Screen("ai_tutor", "AI Ustoz", Icons.Default.Psychology)
    data object Profile : Screen("profile", "Profil", Icons.Default.Person)
    data object Achievements : Screen("achievements", "Yutuqlar", Icons.Default.EmojiEvents)
    data object Analytics : Screen("analytics", "Tahlil", Icons.Default.Analytics)
}

@Composable
fun TarixNavHost() {
    val navController = rememberNavController()
    val mistakesViewModel: MistakesViewModel = hiltViewModel()
    val mistakesCount by mistakesViewModel.activeCount.collectAsState()

    val bottomItems = listOf(
        Screen.Home,
        Screen.Textbooks,
        Screen.Quiz,
        Screen.Mistakes,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                bottomItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            if (screen == Screen.Mistakes && mistakesCount > 0) {
                                BadgedBox(badge = { Badge { Text("$mistakesCount") } }) {
                                    Icon(screen.icon, contentDescription = screen.title)
                                }
                            } else {
                                Icon(screen.icon, contentDescription = screen.title)
                            }
                        },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToQuiz = { navController.navigate(Screen.Quiz.route) },
                    onNavigateToMistakes = { navController.navigate(Screen.Mistakes.route) },
                    onNavigateToTextbooks = { navController.navigate(Screen.Textbooks.route) },
                    onNavigateToAi = { navController.navigate(Screen.AiTutor.route) },
                    onNavigateToLeaderboard = { navController.navigate(Screen.Leaderboard.route) }
                )
            }
            composable(Screen.Textbooks.route) {
                TextbooksScreen(
                    onTopicSelected = { topicId ->
                        navController.navigate("quiz/$topicId")
                    }
                )
            }
            composable(Screen.Quiz.route) {
                QuizScreen(topicId = null)
            }
            composable("quiz/{topicId}") { backStackEntry ->
                val topicId = backStackEntry.arguments?.getString("topicId")
                QuizScreen(topicId = topicId)
            }
            composable(Screen.Mistakes.route) {
                MistakesScreen()
            }
            composable(Screen.Leaderboard.route) {
                LeaderboardScreen()
            }
            composable(Screen.AiTutor.route) {
                AiTutorScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}
