package com.tarixustoz.app.presentation.navigation

import android.util.Base64
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tarixustoz.app.presentation.auth.AuthScreen
import com.tarixustoz.app.presentation.auth.AuthViewModel
import com.tarixustoz.app.presentation.home.HomeScreen
import com.tarixustoz.app.presentation.leaderboard.LeaderboardScreen
import com.tarixustoz.app.presentation.leaderboard.TestAnalysisScreen
import com.tarixustoz.app.presentation.mistakes.MistakesScreen
import com.tarixustoz.app.presentation.mistakes.MistakesViewModel
import com.tarixustoz.app.presentation.notifications.NotificationsScreen
import com.tarixustoz.app.presentation.onboarding.OnboardingScreen
import com.tarixustoz.app.presentation.onboarding.OnboardingViewModel
import com.tarixustoz.app.presentation.onboarding.SplashScreen
import com.tarixustoz.app.presentation.profile.EditProfileScreen
import com.tarixustoz.app.presentation.profile.ProfileScreen
import kotlinx.coroutines.delay
import com.tarixustoz.app.presentation.quiz.PeriodSelectionScreen
import com.tarixustoz.app.presentation.quiz.QuizOptionsScreen
import com.tarixustoz.app.presentation.quiz.QuizScreen
import com.tarixustoz.app.presentation.quiz.TicketListScreen
import com.tarixustoz.app.presentation.quiz.TopicSelectionScreen
import com.tarixustoz.app.presentation.tests.SavedTestsScreen
import com.tarixustoz.app.presentation.tests.TestsScreen
import com.tarixustoz.app.presentation.tests.VerticalCardsScreen
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
    data object Notifications : Screen("notifications", "Bildirishnomalar", Icons.Default.Notifications)
}

/** Uzbek title for a direction or textbook code, used as the ticket list heading. */
private fun directionTitle(code: String): String = when {
    code == "EVENT" -> "Tarixiy voqealar"
    code == "PERSON" -> "Tarixiy shaxslar"
    code == "WAR" -> "Urushlar va janglar"
    code == "YEAR" -> "Yillar va sanalar"
    code == "CITY" -> "Shahar va hududlar"
    code == "FACT" -> "Tarixiy faktlar"
    code == "MONUMENT" -> "Tarixiy obidalar"
    code == "LOGICAL" -> "Mantiqiy va DTM"
    code.startsWith("book_uz_") -> "${code.removePrefix("book_uz_")}-sinf O‘zbekiston tarixi"
    code.startsWith("book_jahon_") -> "${code.removePrefix("book_jahon_")}-sinf Jahon tarixi"
    else -> "Test biletlari"
}

@Composable
fun TarixNavHost(
    pendingNotificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {}
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isOnboardingCompleted by onboardingViewModel.isOnboardingCompleted.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isTestModeActive by authViewModel.isTestModeActive.collectAsState()

    var splashMinElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(650L)
        splashMinElapsed = true
    }

    when {
        isOnboardingCompleted == null || !splashMinElapsed -> {
            SplashScreen()
        }
        isOnboardingCompleted == false -> {
            OnboardingScreen(onFinish = {})
        }
        currentUser == null && !isTestModeActive -> {
            AuthScreen(
                canNavigateBack = false,
                onAuthSuccess = {}
            )
        }
        else -> {
            val user = currentUser
            val sessionKey = user?.uid ?: "test_mode"
            key(sessionKey) {
                val navController = rememberNavController()
                val mistakesViewModel: MistakesViewModel = hiltViewModel()
                val mistakesCount by mistakesViewModel.activeCount.collectAsState()

                LaunchedEffect(user?.uid) {
                    if (user != null) {
                        authViewModel.ensureCloudSynced()
                    }
                }

                LaunchedEffect(pendingNotificationRoute, sessionKey) {
                    if (!pendingNotificationRoute.isNullOrBlank()) {
                        navController.navigate(pendingNotificationRoute) {
                            launchSingleTop = true
                        }
                        onNotificationRouteConsumed()
                    }
                }

                val bottomItems = listOf(
                    Screen.Home,
                    Screen.Quiz,
                    Screen.Mistakes,
                    Screen.Leaderboard,
                    Screen.Profile
                )

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
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
                                                val badgeText = if (mistakesCount > 9) "9+" else "$mistakesCount"
                                                BadgedBox(badge = { Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) { Text(badgeText) } }) {
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
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
            composable(
                route = "vertical_cards/{title}/{categoryType}",
                arguments = listOf(
                    navArgument("title") { defaultValue = "Testlar" },
                    navArgument("categoryType") { defaultValue = "topics" }
                )
            ) { backStackEntry ->
                val rawTitle = backStackEntry.arguments?.getString("title") ?: "Testlar"
                val catType = backStackEntry.arguments?.getString("categoryType") ?: "topics"
                val title = try { URLDecoder.decode(rawTitle, "UTF-8") } catch (e: Exception) { rawTitle }

                VerticalCardsScreen(
                    title = title,
                    categoryType = catType,
                    onBackClick = { navController.popBackStack() },
                    onCardClick = { itemId, _, itemTitle ->
                        val encodedTitle = URLEncoder.encode(itemTitle, "UTF-8")
                        navController.navigate("ticket_list/$itemId?title=$encodedTitle")
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToQuiz = { navController.navigate(Screen.Quiz.route) },
                    onStartDirectionQuiz = { code ->
                        val title = directionTitle(code)
                        navController.navigate("ticket_list/$code?title=${URLEncoder.encode(title, "UTF-8")}")
                    },
                    onNavigateToMistakes = { navController.navigate(Screen.Mistakes.route) },
                    onNavigateToTextbooks = { navController.navigate(Screen.Textbooks.route) },
                    onNavigateToAi = { },
                    onNavigateToLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
                    onNavigateToTestsHub = { navController.navigate(Screen.Quiz.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToTopicSelection = { navController.navigate("topic_selection") },
                    onNavigateToVerticalCards = { title, catType ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        navController.navigate("vertical_cards/$encodedTitle/$catType")
                    },
                    onNavigateToTickets = { catKey, title ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        navController.navigate("ticket_list/$catKey?title=$encodedTitle&grade=0")
                    },
                    onStartQuizForBookOrTopic = { id ->
                        navController.navigate("quiz/$id")
                    }
                )
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToMistakes = { navController.navigate(Screen.Mistakes.route) },
                    onNavigateToDailyQuiz = { navController.navigate("quiz/daily") },
                    onNavigateToTests = { navController.navigate(Screen.Quiz.route) },
                    onNavigateToTextbooks = { navController.navigate(Screen.Textbooks.route) },
                    onNavigateToLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
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
                    onNavigateToCustomOptions = { navController.navigate(Screen.QuizOptions.route) },
                    onNavigateToTickets = { catKey, title, grade ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        navController.navigate("ticket_list/$catKey?title=$encodedTitle&grade=$grade")
                    },
                    onNavigateToVerticalCards = { title, catType ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        navController.navigate("vertical_cards/$encodedTitle/$catType")
                    }
                )
            }
            composable(
                route = "ticket_list/{categoryKey}?title={title}&grade={grade}",
                arguments = listOf(
                    navArgument("categoryKey") { defaultValue = "EVENT" },
                    navArgument("title") { defaultValue = "Test Biletlari" },
                    navArgument("grade") { defaultValue = "0" }
                )
            ) { backStackEntry ->
                val categoryKey = backStackEntry.arguments?.getString("categoryKey") ?: "EVENT"
                val encodedTitle = backStackEntry.arguments?.getString("title") ?: "Test Biletlari"
                val gradeArg = backStackEntry.arguments?.getString("grade")?.toIntOrNull() ?: 0
                val title = try {
                    URLDecoder.decode(encodedTitle, "UTF-8")
                } catch (e: Exception) { encodedTitle }

                TicketListScreen(
                    categoryKey = categoryKey,
                    categoryTitle = title,
                    selectedGrade = gradeArg,
                    onBack = { navController.popBackStack() },
                    onStartTicket = { catKey, ticketIndex ->
                        navController.navigate("quiz/ticket_${catKey}_$ticketIndex?grade=$gradeArg")
                    },
                    onStartActiveQuiz = { route ->
                        navController.navigate(route)
                    }
                )
            }
            composable("topic_selection") {
                VerticalCardsScreen(
                    title = "Mavzulashgan Testlar",
                    categoryType = "topics",
                    onBackClick = { navController.popBackStack() },
                    onCardClick = { itemId, _, itemTitle ->
                        val encodedTitle = URLEncoder.encode(itemTitle, "UTF-8")
                        navController.navigate("ticket_list/$itemId?title=$encodedTitle")
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
            composable("quiz/{topicId}?count={count}&cat={cat}&diff={diff}&time={time}&topics={topics}&grade={grade}") { backStackEntry ->
                val topicId = backStackEntry.arguments?.getString("topicId")
                val count = backStackEntry.arguments?.getString("count")?.toIntOrNull()
                val cat = backStackEntry.arguments?.getString("cat")
                val diff = backStackEntry.arguments?.getString("diff")
                val time = backStackEntry.arguments?.getString("time")?.toIntOrNull()
                val topicsArg = backStackEntry.arguments?.getString("topics")
                val gradeArg = backStackEntry.arguments?.getString("grade")?.toIntOrNull()
                val selectedTopics = if (topicsArg == null || topicsArg == "all") emptyList() else topicsArg.split(",")

                QuizScreen(
                    topicId = if (topicId == "custom") null else topicId,
                    customCount = count,
                    customCategory = cat,
                    customDifficulty = diff,
                    timeLimitMinutes = time,
                    selectedTopicIds = selectedTopics,
                    explicitGrade = gradeArg
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
                LeaderboardScreen(
                    onStartQuiz = { categoryKey ->
                        navController.navigate("quiz/$categoryKey")
                    },
                    onNavigateToAnalysis = { title ->
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        navController.navigate("test_analysis?category=$encodedTitle")
                    }
                )
            }
            composable(
                route = "test_analysis?category={category}",
                arguments = listOf(
                    navArgument("category") { defaultValue = "Barcha testlar umumiy tahlili" }
                )
            ) { backStackEntry ->
                val rawCategory = backStackEntry.arguments?.getString("category") ?: "Barcha testlar umumiy tahlili"
                val categoryTitle = try {
                    URLDecoder.decode(rawCategory, "UTF-8")
                } catch (e: Exception) { rawCategory }

                TestAnalysisScreen(
                    initialCategory = categoryTitle,
                    onBack = { navController.popBackStack() },
                    onStartQuiz = { categoryKey ->
                        val title = directionTitle(categoryKey)
                        navController.navigate("ticket_list/$categoryKey?title=${URLEncoder.encode(title, "UTF-8")}&grade=0")
                    }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                    onNavigateToSavedTests = { navController.navigate("saved_tests") },
                    onNavigateToAuth = { navController.navigate("auth") }
                )
            }
            composable("saved_tests") {
                SavedTestsScreen(
                    onBack = { navController.popBackStack() },
                    onStartSavedQuiz = { navController.navigate("quiz/wrong_questions?count=10") }
                )
            }
            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate("auth") }
                )
            }
            composable("auth") {
                AuthScreen(
                    canNavigateBack = true,
                    onBack = { navController.popBackStack() },
                    onAuthSuccess = { navController.popBackStack() }
                )
            }
        }
    }
            }
        }
    }
}
