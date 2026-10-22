package com.tarixustoz.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tarixustoz.app.presentation.ai.AiTutorScreen
import com.tarixustoz.app.presentation.home.HomeScreen
import com.tarixustoz.app.presentation.leaderboard.LeaderboardScreen
import com.tarixustoz.app.presentation.leaderboard.TestAnalysisScreen
import com.tarixustoz.app.presentation.mistakes.MistakesScreen
import com.tarixustoz.app.presentation.notifications.NotificationsScreen
import com.tarixustoz.app.presentation.profile.ProfileScreen
import com.tarixustoz.app.presentation.quiz.QuizScreen
import com.tarixustoz.app.presentation.quiz.TopicSelectionScreen
import com.tarixustoz.app.presentation.tests.TestsScreen
import com.tarixustoz.app.presentation.textbooks.TextbooksScreen
import com.tarixustoz.app.presentation.theme.BrandPurple

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Bosh sahifa", Icons.Default.Home)
    data object TestsHub : Screen("tests_hub", "Testlar", Icons.Default.GridView)
    data object Textbooks : Screen("textbooks", "Darsliklar", Icons.Default.MenuBook)
    data object Leaderboard : Screen("leaderboard", "Reyting", Icons.Default.Leaderboard)
    data object Profile : Screen("profile", "Profil", Icons.Default.Person)
    data object Mistakes : Screen("mistakes", "Xatolar", Icons.Default.GridView)
    data object Notifications : Screen("notifications", "Bildirishnomalar", Icons.Default.GridView)
    data object TopicSelection : Screen("topic_selection", "Mavzular", Icons.Default.GridView)
    data object AiTutor : Screen("ai_tutor", "AI Ustoz", Icons.Default.Person)
    data object TestAnalysis : Screen("test_analysis/{categoryTitle}", "Test Tahlili", Icons.Default.Leaderboard)
}

@Composable
fun TarixNavHost() {
    val navController = rememberNavController()

    // 5 Bottom navigation tabs: Home, Tests, Textbooks (in place of mistakes), Leaderboard, Profile
    val bottomItems = listOf(
        Screen.Home,
        Screen.TestsHub,
        Screen.Textbooks,
        Screen.Leaderboard,
        Screen.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandPurple,
                                selectedTextColor = BrandPurple,
                                indicatorColor = BrandPurple.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}"),
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToQuiz = { category ->
                        val targetRoute = if (category != null) "quiz/$category" else "quiz/all"
                        navController.navigate(targetRoute)
                    },
                    onNavigateToMistakes = {
                        navController.navigate(Screen.Mistakes.route)
                    },
                    onNavigateToTextbooks = {
                        navController.navigate(Screen.Textbooks.route)
                    },
                    onNavigateToAi = {
                        navController.navigate(Screen.AiTutor.route)
                    },
                    onNavigateToLeaderboard = {
                        navController.navigate(Screen.Leaderboard.route)
                    },
                    onNavigateToTestsHub = {
                        navController.navigate(Screen.TestsHub.route)
                    },
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateToTopicSelection = {
                        navController.navigate(Screen.TopicSelection.route)
                    }
                )
            }

            composable(Screen.TestsHub.route) {
                TestsScreen(
                    onStartQuiz = { categoryOrTopic ->
                        navController.navigate("quiz/$categoryOrTopic")
                    },
                    onNavigateToTopicSelection = {
                        navController.navigate(Screen.TopicSelection.route)
                    },
                    onNavigateToMistakes = {
                        navController.navigate(Screen.Mistakes.route)
                    },
                    onNavigateToAi = {
                        navController.navigate(Screen.AiTutor.route)
                    }
                )
            }

            composable(Screen.Textbooks.route) {
                TextbooksScreen(
                    onTextbookSelected = { textbookId ->
                        navController.navigate("quiz/$textbookId")
                    }
                )
            }

            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToMistakes = {
                        navController.navigate(Screen.Mistakes.route)
                    },
                    onNavigateToDailyQuiz = {
                        navController.navigate("quiz/daily")
                    },
                    onNavigateToTests = {
                        navController.navigate(Screen.TestsHub.route)
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    }
                )
            }

            composable(Screen.TopicSelection.route) {
                TopicSelectionScreen(
                    onBack = { navController.popBackStack() },
                    onStartTopicQuiz = { topicIds ->
                        val param = "topics:" + topicIds.joinToString(",")
                        navController.navigate("quiz/$param")
                    }
                )
            }

            composable("quiz/{param}") { backStackEntry ->
                val param = backStackEntry.arguments?.getString("param")
                QuizScreen(
                    param = param,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Mistakes.route) {
                MistakesScreen(
                    onStartMistakesQuiz = {
                        navController.navigate("quiz/mistakes")
                    }
                )
            }

            composable(Screen.Leaderboard.route) {
                LeaderboardScreen(
                    onStartQuiz = { category ->
                        navController.navigate("quiz/$category")
                    },
                    onNavigateToAnalysis = { categoryTitle ->
                        val encoded = java.net.URLEncoder.encode(categoryTitle, "UTF-8")
                        navController.navigate("test_analysis/$encoded")
                    }
                )
            }

            composable("test_analysis/{categoryTitle}") { backStackEntry ->
                val rawTitle = backStackEntry.arguments?.getString("categoryTitle") ?: "Barcha testlar umumiy tahlili"
                val decodedTitle = try {
                    java.net.URLDecoder.decode(rawTitle, "UTF-8")
                } catch (e: Exception) {
                    rawTitle
                }
                TestAnalysisScreen(
                    initialCategory = decodedTitle,
                    onBack = { navController.popBackStack() },
                    onStartQuiz = { category ->
                        navController.navigate("quiz/$category")
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen()
            }

            composable(Screen.AiTutor.route) {
                AiTutorScreen()
            }
        }
    }
}
