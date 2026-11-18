package com.tarixustoz.app.presentation.tests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
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
import com.tarixustoz.app.presentation.common.components.HistoryTestCard

data class TestCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val ratingText: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val accentColor: Color,
    val categoryType: String // "topic", "grade", "direction"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalCardsScreen(
    title: String,
    categoryType: String,
    onBackClick: () -> Unit,
    onCardClick: (itemId: String, itemType: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allItems = remember(categoryType) {
        when (categoryType) {
            "grades" -> listOf(
                TestCardItem("6", "6-Sinf O'zbekiston & Jahon Tarixi", "Qadimgi dunyo • 28 ta dars", "6-Sinf", "4.9 (240 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), Color(0xFF10B981), "grade"),
                TestCardItem("7", "7-Sinf O'zbekiston Tarixi", "Ilk o'rta asrlar • 32 ta dars", "7-Sinf", "4.9 (310 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF065F46), Color(0xFF047857)), Color(0xFF3B82F6), "grade"),
                TestCardItem("8", "8-Sinf O'zbekiston Tarixi", "O'rta asrlar tamadduni • 30 dars", "8-Sinf", "4.8 (280 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF854D0E), Color(0xFFA16207)), Color(0xFF10B981), "grade"),
                TestCardItem("9", "9-Sinf Yangi Tarix", "XVIII - XIX asrlar • 34 ta dars", "9-Sinf", "4.9 (350 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF7C2D12), Color(0xFF9A3412)), Color(0xFF8B5CF6), "grade"),
                TestCardItem("10", "10-Sinf Eng Yangi Tarix", "XX asr birinchi yarmi • 36 dars", "10-Sinf", "4.9 (410 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF4C1D95), Color(0xFF5B21B6)), Color(0xFFF59E0B), "grade"),
                TestCardItem("11", "11-Sinf Zamonaviy Tarix", "Mustaqillik davri va DTM 189", "11-Sinf", "5.0 (520 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF0F172A), Color(0xFF1E293B)), Color(0xFF10B981), "grade")
            )
            "directions" -> listOf(
                TestCardItem("EVENT", "Tarixiy Hodisalar va Janglar", "Saralangan biletlar va janglar", "15 Bilet", "4.9 (450 test)", Icons.Default.Event, listOf(Color(0xFF1E1B4B), Color(0xFF3730A3)), Color(0xFF10B981), "direction"),
                TestCardItem("PERSON", "Tarixiy Shaxslar va Hukmdorlar", "Amir Temur, Jaloliddin va boshqalar", "20 Bilet", "4.9 (520 test)", Icons.Default.Person, listOf(Color(0xFF831843), Color(0xFF9D174D)), Color(0xFF3B82F6), "direction"),
                TestCardItem("YEAR", "Yillar va Muhim Sanalar", "Sana va voqea mosligini toping", "18 Bilet", "4.8 (380 test)", Icons.Default.CalendarMonth, listOf(Color(0xFF064E3B), Color(0xFF065F46)), Color(0xFFF59E0B), "direction"),
                TestCardItem("WAR", "Urushlar va Shartnomalar", "Qadimgi va o'rta asrlar janglari", "12 Bilet", "4.9 (290 test)", Icons.Default.Security, listOf(Color(0xFF7F1D1D), Color(0xFF991B1B)), Color(0xFF10B981), "direction"),
                TestCardItem("CITY", "Shaharlar va Madaniyat", "Samarqand, Buxoro, Xiva obidalari", "14 Bilet", "4.9 (310 test)", Icons.Default.LocationOn, listOf(Color(0xFF14532D), Color(0xFF15803D)), Color(0xFF8B5CF6), "direction"),
                TestCardItem("LOGICAL", "Mantiqiy va DTM Savollar", "DTM va Attestatsiya imtihonlari", "25 Bilet", "5.0 (650 test)", Icons.Default.Psychology, listOf(Color(0xFF0C4A6E), Color(0xFF0369A1)), Color(0xFF10B981), "direction")
            )
            else -> listOf( // "topics"
                TestCardItem("topic_temur", "Amir Temur va Temuriylar Saltanati", "1370–1405-yillar • 45 bilet", "Mavzu #1", "4.9 (180 test)", Icons.Default.Person, listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), Color(0xFF10B981), "topic"),
                TestCardItem("topic_silkroad", "Buyuk Ipak Yo'li va Savdo", "Qadimgi karvon yo'llari", "Mavzu #2", "4.9 (140 test)", Icons.Default.LocationOn, listOf(Color(0xFF065F46), Color(0xFF047857)), Color(0xFF3B82F6), "topic"),
                TestCardItem("topic_somoniylar", "Somoniylar Davlati Madaniyati", "IX–X asrlar O'rta Osiyo", "Mavzu #3", "4.8 (110 test)", Icons.AutoMirrored.Filled.MenuBook, listOf(Color(0xFF854D0E), Color(0xFFA16207)), Color(0xFF10B981), "topic"),
                TestCardItem("topic_qadimgi_rim", "Qadimgi Rim va Gretsiya", "Jahon tarixi 6-sinf", "Mavzu #4", "4.9 (160 test)", Icons.Default.Event, listOf(Color(0xFF7C2D12), Color(0xFF9A3412)), Color(0xFF8B5CF6), "topic"),
                TestCardItem("topic_xivaliklar", "Xiva Xonligi va Buxoro Amirligi", "XVI-XIX asrlar tarixi", "Mavzu #5", "4.8 (130 test)", Icons.Default.Security, listOf(Color(0xFF4C1D95), Color(0xFF5B21B6)), Color(0xFFF59E0B), "topic"),
                TestCardItem("topic_dtm_2024", "DTM 2024 Saralangan Testlar", "Eng ko'p tushgan 200 ta savol", "DTM Test", "5.0 (500 test)", Icons.Default.Psychology, listOf(Color(0xFF0F172A), Color(0xFF1E293B)), Color(0xFF10B981), "topic")
            )
        }
    }

    val filteredItems = remember(searchQuery, allItems) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.subtitle.contains(searchQuery, ignoreCase = true) ||
                    it.badgeText.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                )
            )

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
                        badgeText = item.badgeText,
                        ratingText = item.ratingText,
                        icon = item.icon,
                        gradientColors = item.gradientColors,
                        accentColor = item.accentColor,
                        onCardClick = { onCardClick(item.id, item.categoryType) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
        }
    }
}
