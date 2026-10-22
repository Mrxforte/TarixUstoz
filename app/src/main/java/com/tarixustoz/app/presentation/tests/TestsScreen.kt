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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.textbooks.TextbooksViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(
    onStartQuiz: (categoryOrTopic: String) -> Unit,
    onNavigateToTopicSelection: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToAi: () -> Unit,
    textbooksViewModel: TextbooksViewModel = hiltViewModel()
) {
    val textbooks by textbooksViewModel.textbooks.collectAsState()
    var selectedGrade by remember { mutableIntStateOf(7) }

    LaunchedEffect(Unit) {
        textbooksViewModel.load()
    }

    val grades = listOf(0 to "Barchasi", 6 to "6-sinf", 7 to "7-sinf", 8 to "8-sinf", 9 to "9-sinf", 10 to "10-sinf", 11 to "11-sinf")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Testlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                                if (grade > 0) textbooksViewModel.setGrade(grade)
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
                                    Text("11 ta xato")
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

            // DTM Imtihon Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E1F38),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartQuiz("dtm_exam") }
                        .testTag("tests_dtm_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E3056),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DTM BLOK SINOBI",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "30 savol • 60 daqiqa • Haqiqiy ball",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Categories Header
            item {
                Text(
                    text = "Yo‘nalishlar bo‘yicha testlar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Categories Grid (Tarixiy voqealar, obidalar, urushlar, shaxslar, yillar, shaharlar, sevimlilar)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Tarixiy voqealar & Tarixiy obidalar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryItemCard(
                            title = "Tarixiy voqealar",
                            count = "165 test",
                            icon = Icons.Default.Event,
                            iconTint = Color(0xFF6366F1),
                            iconBg = Color(0xFFEEF2FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("tarixiy_voqealar") }
                        )
                        CategoryItemCard(
                            title = "Tarixiy obidalar",
                            count = "95 test",
                            icon = Icons.Default.AccountBalance,
                            iconTint = Color(0xFF8B5CF6),
                            iconBg = Color(0xFFF5F3FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("tarixiy_obidalar") }
                        )
                    }

                    // Row 2: Urushlar va janglar & Sevimli testlarim
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryItemCard(
                            title = "Urushlar va janglar",
                            count = "110 test",
                            icon = Icons.Default.Security,
                            iconTint = Color(0xFFDC2626),
                            iconBg = Color(0xFFFEF2F2),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("urushlar_va_janglar") }
                        )
                        CategoryItemCard(
                            title = "Sevimli testlarim",
                            count = "Tanlanganlar",
                            icon = Icons.Default.Bookmark,
                            iconTint = Color(0xFFD97706),
                            iconBg = Color(0xFFFEF3C7),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("favorites") }
                        )
                    }

                    // Row 3: Shahar nomlari & Tarixiy shaxslar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryItemCard(
                            title = "Shahar nomlari",
                            count = "120 test",
                            icon = Icons.Default.LocationOn,
                            iconTint = Color(0xFFF97316),
                            iconBg = Color(0xFFFFF7ED),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("shahar_nomlari") }
                        )
                        CategoryItemCard(
                            title = "Tarixiy shaxslar",
                            count = "184 test",
                            icon = Icons.Default.Person,
                            iconTint = Color(0xFF7C3AED),
                            iconBg = Color(0xFFF5F3FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("tarixiy_shaxslar") }
                        )
                    }

                    // Row 4: Mavzu bo'yicha & Yillar bo'yicha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryItemCard(
                            title = "Mavzu bo‘yicha",
                            count = "Mavzular ro‘yxati",
                            icon = Icons.Default.MenuBook,
                            iconTint = Color(0xFF0284C7),
                            iconBg = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToTopicSelection() }
                        )
                        CategoryItemCard(
                            title = "Yillar bo‘yicha",
                            count = "215 test",
                            icon = Icons.Default.CalendarMonth,
                            iconTint = Color(0xFF16A34A),
                            iconBg = Color(0xFFF0FDF4),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuiz("yillar_boyicha") }
                        )
                    }
                }
            }

            // AI Tutor Teaser
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEEF2FF),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAi() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Tarixchi Ustoz",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple
                            )
                            Text(
                                text = "Tushunmagan savollaringizni AI bilan birga tahlil qiling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = BrandPurple
                        )
                    }
                }
            }

            // Textbooks & Topics Header
            item {
                Text(
                    text = "Darsliklar va mavzular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val filteredTextbooks = textbooks

            items(filteredTextbooks) { textbook ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartQuiz(textbook.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (textbook.subjectId == "uzbekiston") Color(0xFFEEF2FF) else Color(0xFFFEF3C7),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${textbook.gradeId}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (textbook.subjectId == "uzbekiston") BrandPurple else Color(0xFFD97706),
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = textbook.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${textbook.totalTopics} ta mavzu • ${textbook.totalQuestions} ta test",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CategoryItemCard(
    title: String,
    count: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("tests_category_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = count,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
