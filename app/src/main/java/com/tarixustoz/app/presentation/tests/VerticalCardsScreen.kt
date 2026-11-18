package com.tarixustoz.app.presentation.tests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Topic
import com.tarixustoz.app.domain.model.gradeId
import com.tarixustoz.app.presentation.common.components.HistoryTestCard
import com.tarixustoz.app.presentation.theme.BrandPurple

data class TestCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val totalTickets: Int,
    val passedTickets: Int,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val accentColor: Color,
    val categoryType: String // "topic", "grade", "direction"
)

fun Topic.toTestCardItem(): TestCardItem {
    val gradeStr = "${this.gradeId}-Sinf"
    val totalT = 10
    val totalQs = 300
    val subtitleText = if (summary.isNotBlank()) summary else "Tarix darsligi • $gradeStr"
    val fullSubtitle = "$subtitleText • $totalT ta bilet • $totalQs ta test"

    val colors = when (gradeId) {
        6 -> listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
        7 -> if (textbookId.contains("jahon")) listOf(Color(0xFF0F766E), Color(0xFF115E59)) else listOf(Color(0xFF065F46), Color(0xFF047857))
        8 -> if (textbookId.contains("jahon")) listOf(Color(0xFF9A3412), Color(0xFF7C2D12)) else listOf(Color(0xFF854D0E), Color(0xFFA16207))
        9 -> if (textbookId.contains("jahon")) listOf(Color(0xFF6B21A8), Color(0xFF581C87)) else listOf(Color(0xFF7C2D12), Color(0xFF9A3412))
        10 -> if (textbookId.contains("jahon")) listOf(Color(0xFF1E3A8A), Color(0xFF1E40AF)) else listOf(Color(0xFF4C1D95), Color(0xFF5B21B6))
        11 -> listOf(Color(0xFF0F172A), Color(0xFF1E293B))
        else -> listOf(Color(0xFF0F172A), Color(0xFF334155))
    }
    val accent = when (number % 4) {
        0 -> Color(0xFF10B981)
        1 -> Color(0xFF3B82F6)
        2 -> Color(0xFF8B5CF6)
        else -> Color(0xFFF59E0B)
    }
    return TestCardItem(
        id = id,
        title = title,
        subtitle = fullSubtitle,
        totalTickets = totalT,
        passedTickets = 0,
        icon = Icons.AutoMirrored.Filled.MenuBook,
        gradientColors = colors,
        accentColor = accent,
        categoryType = "topic"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalCardsScreen(
    title: String,
    categoryType: String,
    onBackClick: () -> Unit,
    onCardClick: (itemId: String, itemType: String, itemTitle: String) -> Unit,
    testsViewModel: TestsViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBookFilter by remember { mutableStateOf("all") }
    var showFilterDropdown by remember { mutableStateOf(false) }

    val dbTopics by testsViewModel.allTopics.collectAsState()

    val gradeFilters = listOf(
        "all" to "🌐 Barchasi (381 ta mavzu)",
        "6_all" to "📚 6-sinf (44 ta mavzu)",
        "7_uz" to "🇺🇿 7-sinf O‘zbekiston (35 ta mavzu)",
        "7_jahon" to "🌍 7-sinf Jahon (40 ta mavzu)",
        "8_uz" to "🇺🇿 8-sinf O‘zbekiston (45 ta mavzu)",
        "8_jahon" to "🌍 8-sinf Jahon (35 ta mavzu)",
        "9_uz" to "🇺🇿 9-sinf O‘zbekiston (30 ta mavzu)",
        "9_jahon" to "🌍 9-sinf Jahon (40 ta mavzu)",
        "10_uz" to "🇺🇿 10-sinf O‘zbekiston (30 ta mavzu)",
        "10_jahon" to "🌍 10-sinf Jahon (30 ta mavzu)",
        "11_uz" to "🇺🇿 11-sinf O‘zbekiston (25 ta mavzu)",
        "11_jahon" to "🌍 11-sinf Jahon (25 ta mavzu)"
    )

    // Sort chronologically starting from 6-sinf downwards
    val sortedDbTopics = remember(dbTopics) {
        dbTopics.sortedWith(
            compareBy<Topic> { it.gradeId }
                .thenBy { if (it.textbookId.contains("jahon")) 1 else 0 }
                .thenBy { it.number }
                .thenBy { it.title }
        )
    }

    val filteredDbTopics = remember(selectedBookFilter, sortedDbTopics) {
        sortedDbTopics.filter { topic ->
            when (selectedBookFilter) {
                "all" -> true
                "6_all" -> topic.gradeId == 6 || topic.textbookId == "uz_6"
                "7_uz" -> topic.gradeId == 7 && (topic.textbookId == "uz_7" || !topic.textbookId.contains("jahon"))
                "7_jahon" -> topic.gradeId == 7 && topic.textbookId.contains("jahon")
                "8_uz" -> topic.gradeId == 8 && (topic.textbookId == "uz_8" || !topic.textbookId.contains("jahon"))
                "8_jahon" -> topic.gradeId == 8 && topic.textbookId.contains("jahon")
                "9_uz" -> topic.gradeId == 9 && (topic.textbookId == "uz_9" || !topic.textbookId.contains("jahon"))
                "9_jahon" -> topic.gradeId == 9 && topic.textbookId.contains("jahon")
                "10_uz" -> topic.gradeId == 10 && (topic.textbookId == "uz_10" || !topic.textbookId.contains("jahon"))
                "10_jahon" -> topic.gradeId == 10 && topic.textbookId.contains("jahon")
                "11_uz" -> topic.gradeId == 11 && (topic.textbookId == "uz_11" || !topic.textbookId.contains("jahon"))
                "11_jahon" -> topic.gradeId == 11 && topic.textbookId.contains("jahon")
                else -> true
            }
        }
    }

    val allItems = remember(categoryType, filteredDbTopics) {
        when (categoryType) {
            "grades" -> listOf(
                TestCardItem("6_all", "6-Sinf Qadimgi Dunyo Tarixi", "Qadimgi sivilizatsiyalar", 8, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), Color(0xFF10B981), "grade"),
                TestCardItem("7_uz", "7-Sinf O'zbekiston Tarixi", "Ilk o'rta asrlar", 7, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF065F46), Color(0xFF047857)), Color(0xFF3B82F6), "grade"),
                TestCardItem("7_jahon", "7-Sinf Jahon Tarixi", "O'rta asrlar tarixi", 7, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF0F766E), Color(0xFF115E59)), Color(0xFF10B981), "grade"),
                TestCardItem("8_uz", "8-Sinf O'zbekiston Tarixi", "XVI-XIX asr boshlari", 9, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF854D0E), Color(0xFFA16207)), Color(0xFFF59E0B), "grade"),
                TestCardItem("8_jahon", "8-Sinf Jahon Tarixi", "Yangi davr tarixi", 7, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF9A3412), Color(0xFF7C2D12)), Color(0xFF3B82F6), "grade"),
                TestCardItem("9_uz", "9-Sinf O'zbekiston Tarixi", "Mustamlaka davri", 6, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF7C2D12), Color(0xFF9A3412)), Color(0xFF8B5CF6), "grade"),
                TestCardItem("9_jahon", "9-Sinf Jahon Tarixi", "XIX asr oxiri - XX asr", 8, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF6B21A8), Color(0xFF581C87)), Color(0xFF10B981), "grade"),
                TestCardItem("10_uz", "10-Sinf O'zbekiston Tarixi", "Jadidchilik va sho'rolar", 6, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF4C1D95), Color(0xFF5B21B6)), Color(0xFFF59E0B), "grade"),
                TestCardItem("10_jahon", "10-Sinf Jahon Tarixi", "XX asr birinchi yarmi", 6, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF1E3A8A), Color(0xFF1E40AF)), Color(0xFF3B82F6), "grade"),
                TestCardItem("11_uz", "11-Sinf O'zbekiston Tarixi", "Mustaqillik yillari", 5, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF0F172A), Color(0xFF1E293B)), Color(0xFF10B981), "grade"),
                TestCardItem("11_jahon", "11-Sinf Jahon Tarixi", "Zamonaviy dunyo", 5, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF1E293B), Color(0xFF0F172A)), Color(0xFF8B5CF6), "grade")
            )
            "directions" -> listOf(
                TestCardItem("EVENT", "Tarixiy Hodisalar va Janglar", "Saralangan biletlar", 15, 0, Icons.Default.Event, listOf(Color(0xFF1E1B4B), Color(0xFF3730A3)), Color(0xFF10B981), "direction"),
                TestCardItem("PERSON", "Tarixiy Shaxslar va Hukmdorlar", "Buyuk siymolar", 20, 0, Icons.Default.Person, listOf(Color(0xFF831843), Color(0xFF9D174D)), Color(0xFF3B82F6), "direction"),
                TestCardItem("YEAR", "Yillar va Muhim Sanalar", "Xronologik tartib", 18, 0, Icons.Default.CalendarMonth, listOf(Color(0xFF064E3B), Color(0xFF065F46)), Color(0xFFF59E0B), "direction"),
                TestCardItem("WAR", "Urushlar va Shartnomalar", "Harbiy yurishlar", 12, 0, Icons.Default.Security, listOf(Color(0xFF7F1D1D), Color(0xFF991B1B)), Color(0xFF10B981), "direction"),
                TestCardItem("CITY", "Shaharlar va Madaniyat", "Joylashuvlar", 14, 0, Icons.Default.LocationOn, listOf(Color(0xFF14532D), Color(0xFF15803D)), Color(0xFF8B5CF6), "direction"),
                TestCardItem("LOGICAL", "Mantiqiy va DTM Savollar", "DTM savollari", 25, 0, Icons.Default.Psychology, listOf(Color(0xFF0C4A6E), Color(0xFF0369A1)), Color(0xFF10B981), "direction")
            )
            else -> { // "topics"
                if (filteredDbTopics.isNotEmpty()) {
                    filteredDbTopics.map { it.toTestCardItem() }
                } else {
                    listOf(
                        TestCardItem("topic_temur", "Amir Temur va Temuriylar Saltanati", "Amir Temur davlati", 2, 0, Icons.Default.Person, listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), Color(0xFF10B981), "topic"),
                        TestCardItem("topic_silkroad", "Buyuk Ipak Yo'li va Savdo", "Qadimgi karvon yo'llari", 2, 0, Icons.Default.LocationOn, listOf(Color(0xFF065F46), Color(0xFF047857)), Color(0xFF3B82F6), "topic"),
                        TestCardItem("topic_somoniylar", "Somoniylar Davlati Madaniyati", "IX–X asrlar O'rta Osiyo", 2, 0, Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF854D0E), Color(0xFFA16207)), Color(0xFF10B981), "topic"),
                        TestCardItem("topic_qadimgi_rim", "Qadimgi Rim va Gretsiya", "Jahon tarixi", 2, 0, Icons.Default.Event, listOf(Color(0xFF7C2D12), Color(0xFF9A3412)), Color(0xFF8B5CF6), "topic")
                    )
                }
            }
        }
    }

    val filteredItems = remember(searchQuery, allItems) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Ortga"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar & Grade/Subject Dropdown Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Qidirish...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                // Dropdown Filter Button
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedBookFilter != "all") BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (selectedBookFilter != "all") BrandPurple else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(56.dp)
                            .clickable { showFilterDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Sinf tanlash",
                                tint = if (selectedBookFilter != "all") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (selectedBookFilter != "all") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showFilterDropdown,
                        onDismissRequest = { showFilterDropdown = false }
                    ) {
                        gradeFilters.forEach { (filterKey, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selectedBookFilter == filterKey) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedBookFilter == filterKey) BrandPurple else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    selectedBookFilter = filterKey
                                    showFilterDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Vertical List View of Cards
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    HistoryTestCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        totalTickets = item.totalTickets,
                        passedTickets = item.passedTickets,
                        icon = item.icon,
                        gradientColors = item.gradientColors,
                        accentColor = item.accentColor,
                        onCardClick = { onCardClick(item.id, item.categoryType, item.title) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
        }
    }
}
