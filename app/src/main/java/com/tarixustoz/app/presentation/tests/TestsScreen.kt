package com.tarixustoz.app.presentation.tests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Topic
import com.tarixustoz.app.domain.model.gradeId
import com.tarixustoz.app.presentation.textbooks.TextbooksViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple
import com.tarixustoz.app.presentation.common.components.TestSectionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(
    onStartQuizCategory: (count: Int, categoryCode: String, diff: String, time: Int) -> Unit,
    onStartQuizForBookOrTopic: (id: String) -> Unit,
    onNavigateToTopicSelection: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToCustomOptions: () -> Unit,
    onNavigateToTickets: (categoryKey: String, categoryTitle: String, grade: Int) -> Unit,
    onNavigateToVerticalCards: (title: String, categoryType: String) -> Unit = { _, _ -> },
    textbooksViewModel: TextbooksViewModel = hiltViewModel(),
    testsViewModel: TestsViewModel = hiltViewModel()
) {
    val directionCounts by testsViewModel.directionCounts.collectAsState()
    var selectedGrade by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedGrade) {
        testsViewModel.loadCounts(selectedGrade)
    }

    val grades = listOf(0 to "Barchasi", 6 to "6-sinf", 7 to "7-sinf", 8 to "8-sinf", 9 to "9-sinf", 10 to "10-sinf", 11 to "11-sinf")

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    val testsTopicCards by testsViewModel.testsTopicCards.collectAsState()
    val testsGradeCards by testsViewModel.testsGradeCards.collectAsState()
    val testsDirectionCards by testsViewModel.testsDirectionCards.collectAsState()

    LaunchedEffect(Unit) {
        testsViewModel.loadCounts(selectedGrade)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Test va Imtihonlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    testsViewModel.loadCounts(selectedGrade)
                    isRefreshing = false
                }
            },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    color = BrandPurple,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Grade Filter Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(grades) { (grade, label) ->
                            val isSelected = selectedGrade == grade
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedGrade = grade
                                    testsViewModel.loadCounts(grade)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPurple,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }

                // Xatolar Ustida Ishlash (Mistakes Card)
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToMistakes() }
                            .testTag("tests_mistakes_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Bilmagan savollarim",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                        Text("Xatolar")
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Xatolaringiz ustida ishlab, bilimlarni mustahkamlang",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Tarixiy Yo'nalishlar Section (Cards like Image #2)
                item {
                    TestSectionRow(
                        sectionTitle = "Tarixiy yo'nalishli testlar",
                        items = testsDirectionCards,
                        onSeeAllClick = { onNavigateToVerticalCards("Tarixiy Yo'nalishlar", "directions") },
                        onCardClick = { code, _, _ -> onNavigateToTickets(code, "Test Biletlari", selectedGrade) }
                    )
                }

                // Mavzulashgan Testlar Section
                item {
                    TestSectionRow(
                        sectionTitle = "Mavzulashgan testlar",
                        items = testsTopicCards,
                        onSeeAllClick = { onNavigateToVerticalCards("Mavzulashgan Testlar", "topics") },
                        onCardClick = { topicId, _, _ ->
                            onStartQuizForBookOrTopic(topicId)
                        }
                    )
                }

                // Sinf Testlari Section
                item {
                    TestSectionRow(
                        sectionTitle = "Sinf testlari (6-11 sinflار)",
                        items = testsGradeCards,
                        onSeeAllClick = { onNavigateToVerticalCards("Sinf Testlari", "grades") },
                        onCardClick = { gradeKey, _, gradeTitle ->
                            onNavigateToTickets(gradeKey, gradeTitle, selectedGrade)
                        }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
