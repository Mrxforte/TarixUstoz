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
import com.tarixustoz.app.domain.model.Textbook
import com.tarixustoz.app.presentation.textbooks.TextbooksViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(
    onStartQuizCategory: (count: Int, categoryCode: String, diff: String, time: Int) -> Unit,
    onStartQuizForBookOrTopic: (id: String) -> Unit,
    onNavigateToTopicSelection: () -> Unit,
    onNavigateToPeriodSelection: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToCustomOptions: () -> Unit,
    textbooksViewModel: TextbooksViewModel = hiltViewModel()
) {
    val textbooks by textbooksViewModel.textbooks.collectAsState()
    var selectedGrade by remember { mutableIntStateOf(0) }
    var selectedTextbookForMenu by remember { mutableStateOf<Textbook?>(null) }

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
                        text = "Test va Imtihonlar",
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
                                textbooksViewModel.setGrade(grade)
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

            // Official Exams Section Header
            item {
                Text(
                    text = "Rasmiy va RAG Imtihonlar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 1. Milliy Sertifikat Card
            item {
                ExamCategoryCard(
                    title = "MILLIY SERTIFIKAT IMTIHONI",
                    subtitle = "BMBA • 45 savol • 90 daqiqa • Yuqori daraja",
                    description = "Tarix fanidan bilim darajasini baholash va Milliy Sertifikat sinovlari formati.",
                    containerColor = Color(0xFF0F172A),
                    accentColor = Color(0xFF38BDF8),
                    icon = Icons.Default.VerifiedUser,
                    onClick = { onStartQuizCategory(45, "NATIONAL_CERTIFICATE", "HARD", 90) }
                )
            }

            // 2. O'qituvchilar Attestatsiyasi Card
            item {
                ExamCategoryCard(
                    title = "O‘QITUVCHILAR ATTESTASIYASI",
                    subtitle = "Pedagogik toifa • 35 savol • 60 daqiqa",
                    description = "Tarix fani o'qituvchilari uchun rasmiy attestatsiya va toifa sinovlari formati.",
                    containerColor = Color(0xFF311B92),
                    accentColor = Color(0xFFB388FF),
                    icon = Icons.Default.School,
                    onClick = { onStartQuizCategory(35, "ATTESTATION", "HARD", 60) }
                )
            }

            // 3. DTM BLOK SINOBI Card
            item {
                ExamCategoryCard(
                    title = "DTM BLOK SINOBI (BMBA)",
                    subtitle = "OTMga kirish • 30 savol • 60 daqiqa",
                    description = "Oliy ta'lim muassasalariga kirish 30 ta majburiy va mutaxassislik testlari mashqi.",
                    containerColor = Color(0xFF1E1F38),
                    accentColor = Color(0xFFFBBF24),
                    icon = Icons.Default.EmojiEvents,
                    onClick = { onStartQuizCategory(30, "DTM_PRACTICE", "MEDIUM", 60) }
                )
            }

            // Categories Header
            item {
                Text(
                    text = "Yo‘nalishlar bo‘yicha testlar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Categories Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            onClick = { onStartQuizCategory(15, "EVENT", "MEDIUM", 15) }
                        )
                        CategoryItemCard(
                            title = "Tarixiy shaxslar",
                            count = "184 test",
                            icon = Icons.Default.Person,
                            iconTint = Color(0xFF7C3AED),
                            iconBg = Color(0xFFF5F3FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuizCategory(15, "PERSON", "MEDIUM", 15) }
                        )
                    }

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
                            onClick = { onStartQuizCategory(15, "EVENT", "HARD", 15) }
                        )
                        CategoryItemCard(
                            title = "Yillar va sanalar",
                            count = "Davr bo‘yicha tanlash",
                            icon = Icons.Default.CalendarMonth,
                            iconTint = Color(0xFF16A34A),
                            iconBg = Color(0xFFF0FDF4),
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToPeriodSelection() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryItemCard(
                            title = "Shahar va hududlar",
                            count = "120 test",
                            icon = Icons.Default.LocationOn,
                            iconTint = Color(0xFFF97316),
                            iconBg = Color(0xFFFFF7ED),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuizCategory(15, "CITY", "MEDIUM", 15) }
                        )
                        CategoryItemCard(
                            title = "Mantiqiy va DTM",
                            count = "95 test",
                            icon = Icons.Default.Psychology,
                            iconTint = Color(0xFF0284C7),
                            iconBg = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f),
                            onClick = { onStartQuizCategory(15, "LOGICAL", "HARD", 15) }
                        )
                    }

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
                            title = "Test Sozlamalari",
                            count = "Moslashuvchan",
                            icon = Icons.Default.Tune,
                            iconTint = Color(0xFFD97706),
                            iconBg = Color(0xFFFEF3C7),
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToCustomOptions() }
                        )
                    }
                }
            }

            // Textbooks & Topics Header
            item {
                Text(
                    text = "Darsliklar va sinflar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(textbooks) { textbook ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTextbookForMenu = textbook
                        }
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

    if (selectedTextbookForMenu != null) {
        val book = selectedTextbookForMenu!!
        val totalQs = book.totalQuestions.coerceAtLeast(30)
        val totalVariants = (totalQs / 30).coerceIn(1, 40)

        ModalBottomSheet(
            onDismissRequest = { selectedTextbookForMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${book.gradeId}-sinf • Jami ${book.totalQuestions} ta test mavjud",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selectedTextbookForMenu = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Yopish")
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Test ishlash rejimini tanlang:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandPurple
                )

                // Option 1: Mavzular bo'yicha
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTextbookForMenu = null
                            onNavigateToTopicSelection()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📚 Mavzular bo‘yicha test", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Darslikdagi mavzulardan alohida tanlab ishlash", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BrandPurple)
                    }
                }

                // Option 2: Test Variantlari list
                Text(
                    text = "📄 Test Variantlari (${totalVariants} ta variant, har biri 30 ta savol):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(totalVariants) { index ->
                        val varNum = index + 1
                        val startQ = (index * 30) + 1
                        val endQ = ((index + 1) * 30).coerceAtMost(book.totalQuestions)
                        Button(
                            onClick = {
                                selectedTextbookForMenu = null
                                onStartQuizForBookOrTopic("variant_${book.gradeId}_${book.subjectId}_$varNum")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple.copy(alpha = 0.12f), contentColor = BrandPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Variant $varNum", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$startQ–$endQ-savollar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Option 3: Aralash DTM / Olimpiada Test
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTextbookForMenu = null
                            onStartQuizForBookOrTopic("book_${book.gradeId}")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🏆 Aralash DTM / Olimpiada Test (30 savol)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0369A1))
                            Text("Ushbu darslik bo‘yicha tasodifiy 30 ta savol", fontSize = 11.sp, color = Color(0xFF0284C7))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF0284C7))
                    }
                }

                // Option 4: 100 ta savoldan iborat Katta Blok Test
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTextbookForMenu = null
                            onStartQuizCategory(100, "ALL", "MEDIUM", 120)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ Katta Blok Test (100 ta savol)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFB45309))
                            Text("Maksimal bilim sinovi • 120 daqiqa", fontSize = 11.sp, color = Color(0xFFD97706))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFD97706))
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ExamCategoryCard(
    title: String,
    subtitle: String,
    description: String,
    containerColor: Color,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
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
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
