package com.tarixustoz.app.presentation.navigation

import android.util.Base64
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
import androidx.navigation.navArgument
import com.tarixustoz.app.presentation.home.HomeScreen
import com.tarixustoz.app.presentation.leaderboard.LeaderboardScreen
import com.tarixustoz.app.presentation.mistakes.MistakesScreen
import com.tarixustoz.app.presentation.mistakes.MistakesViewModel
import com.tarixustoz.app.presentation.profile.EditProfileScreen
import com.tarixustoz.app.presentation.profile.ProfileScreen
import com.tarixustoz.app.presentation.quiz.PeriodSelectionScreen
import com.tarixustoz.app.presentation.quiz.QuizOptionsScreen
import com.tarixustoz.app.presentation.quiz.QuizScreen
import com.tarixustoz.app.presentation.quiz.TopicSelectionScreen
import com.tarixustoz.app.presentation.tests.TestsScreen
import com.tarixustoz.app.presentation.textbooks.PdfViewerScreen
import com.tarixustoz.app.presentation.textbooks.TextbooksScreen
import com.tarixustoz.app.presentation.textbooks.TopicDetailScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Bosh sahifa", Icons.Default.Home)
    data object Textbooks : Screen("textbooks", "Darsliklar", Icons.Default.Book)
    data object Quiz : Screen("quiz", "Testlar", Icons.Default.Quiz)
    data object Mistakes : Screen("mistakes", "Xatolar", Icons.Default.Warning)
    data object Leaderboard : Screen("leaderboard", "Reyting", Icons.Default.Leaderboard)
    data object Profile : Screen("profile", "Profil", Icons.Default.Person)
    data object EditProfile : Screen("edit_profile", "Profilni tahrirlash", Icons.Default.Person)
    data object Achievements : Screen("achievements", "Yutuqlar", Icons.Default.EmojiEvents)
    data object Analytics : Screen("analytics", "Tahlil", Icons.Default.Analytics)
    data object QuizOptions : Screen("quiz_options", "Test Sozlamalari", Icons.Default.Quiz)
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
            val currentRoute = currentDestination?.route
            val showBottomBar = bottomItems.any { it.route == currentRoute }

            if (showBottomBar) {
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
                    onNavigateToAi = { },
                    onNavigateToLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
                    onNavigateToTestsHub = { navController.navigate(Screen.Quiz.route) },
                    onNavigateToNotifications = { },
                    onNavigateToTopicSelection = { navController.navigate(Screen.Textbooks.route) }
                )
            }
            composable(Screen.Textbooks.route) {
                TextbooksScreen(
                    onTopicSelected = { topicId ->
                        navController.navigate("topic_detail/$topicId")
                    },
                    onOpenPdf = { title, pdfAsset ->
                        val encodedTitle = Base64.encodeToString(title.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
                        val encodedPath = Base64.encodeToString(pdfAsset.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
                        navController.navigate("pdf_viewer?title=$encodedTitle&pdfAsset=$encodedPath")
                    }
                )
            }
            composable(
                route = "pdf_viewer?title={title}&pdfAsset={pdfAsset}",
                arguments = listOf(
                    androidx.navigation.navArgument("title") { defaultValue = "" },
                    navArgument("pdfAsset") { defaultValue = "" }
                )
            ) { backStackEntry ->
                val titleArg = backStackEntry.arguments?.getString("title") ?: ""
                val pdfAssetArg = backStackEntry.arguments?.getString("pdfAsset") ?: ""
                val decodedTitle = try {
                    String(Base64.decode(titleArg, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
                } catch (e: Exception) { "Darslik PDF" }
                val decodedPath = try {
                    String(Base64.decode(pdfAssetArg, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
                } catch (e: Exception) { pdfAssetArg }

                PdfViewerScreen(
                    title = if (decodedTitle.isBlank()) "Darslik PDF" else decodedTitle,
                    pdfAssetPath = decodedPath,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("topic_detail/{topicId}") {
                TopicDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onStartQuiz = { topicId ->
                        navController.navigate("quiz/$topicId")
                    }
                )
            }
            composable(Screen.Quiz.route) {
                TestsScreen(
                    onStartQuizCategory = { count, categoryCode, diff, time ->
                        navController.navigate("quiz/custom?count=$count&cat=$categoryCode&diff=$diff&time=$time&topics=all")
                    },
                    onStartQuizForBookOrTopic = { id ->
                        navController.navigate("quiz/$id")
                    },
                    onNavigateToTopicSelection = { navController.navigate("topic_selection") },
                    onNavigateToMistakes = { navController.navigate(Screen.Mistakes.route) },
                    onNavigateToAi = { },
                    onNavigateToCustomOptions = { navController.navigate(Screen.QuizOptions.route) }
                )
            }
            composable("topic_selection") {
                TopicSelectionScreen(
                    onBack = { navController.popBackStack() },
                    onStartTopicQuiz = { topicIds ->
                        val topicsStr = if (topicIds.isEmpty()) "all" else topicIds.joinToString(",")
                        navController.navigate("quiz/custom?count=15&cat=ALL&diff=MEDIUM&time=15&topics=$topicsStr")
                    }
                )
            }
            composable("period_selection") {
                PeriodSelectionScreen(
                    onBack = { navController.popBackStack() },
                    onStartPeriodQuiz = { _, _, _ ->
                        navController.navigate("quiz/custom?count=15&cat=YEAR&diff=MEDIUM&time=15&topics=all")
                    }
                )
            }
            composable(Screen.QuizOptions.route) {
                QuizOptionsScreen(
                    onStartQuiz = { count, category, diff, time, topics ->
                        val topicsStr = if (topics.isEmpty()) "all" else topics.joinToString(",")
                        navController.navigate("quiz/custom?count=$count&cat=${category.name}&diff=${diff.name}&time=$time&topics=$topicsStr")
                    }
                )
            }
            composable("quiz/{topicId}?count={count}&cat={cat}&diff={diff}&time={time}&topics={topics}") { backStackEntry ->
                val topicId = backStackEntry.arguments?.getString("topicId")
                val count = backStackEntry.arguments?.getString("count")?.toIntOrNull()
                val cat = backStackEntry.arguments?.getString("cat")
                val diff = backStackEntry.arguments?.getString("diff")
                val time = backStackEntry.arguments?.getString("time")?.toIntOrNull()
                val topicsArg = backStackEntry.arguments?.getString("topics")
                val selectedTopics = if (topicsArg == null || topicsArg == "all") emptyList() else topicsArg.split(",")
                
                QuizScreen(
                    topicId = if (topicId == "custom") null else topicId,
                    customCount = count,
                    customCategory = cat,
                    customDifficulty = diff,
                    timeLimitMinutes = time,
                    selectedTopicIds = selectedTopics
                )
            }
            composable(Screen.Mistakes.route) {
                MistakesScreen(
                    onReviewWrongQuestions = {
                        navController.navigate("quiz/wrong_questions?count=10")
                    }
                )
            }
            composable(Screen.Leaderboard.route) {
                LeaderboardScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) }
                )
            }
            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
