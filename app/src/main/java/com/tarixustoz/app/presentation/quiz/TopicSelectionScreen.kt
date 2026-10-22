package com.tarixustoz.app.presentation.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Topic
import com.tarixustoz.app.presentation.textbooks.TextbooksViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSelectionScreen(
    onBack: () -> Unit,
    onStartTopicQuiz: (List<String>) -> Unit,
    viewModel: TextbooksViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGradeFilter by remember { mutableIntStateOf(0) } // 0 = Barchasi
    val selectedTopicIds = remember { mutableStateListOf<String>() }

    // Seeded topic list with metadata
    val allTopics = remember {
        listOf(
            TopicMeta("uz6_t1", "uz_6", 6, "Qadimgi Misr madaniyati va Nil daryosi", "6-sinf • Jahon tarixi", 18),
            TopicMeta("uz6_t2", "uz_6", 6, "Qadimgi Baqtriya va So‘g‘diyona davlati", "6-sinf • Jahon tarixi", 15),
            TopicMeta("uz6_t3", "uz_6", 6, "Yunon-Baqtriya podsholigi va Kushonlar", "6-sinf • Jahon tarixi", 14),
            TopicMeta("uz7_t1", "uz_7", 7, "Xioniylar, Kidariylar va Eftallar", "7-sinf • O‘zbekiston tarixi", 20),
            TopicMeta("uz7_t2", "uz_7", 7, "Turk xoqonligi va Buyuk Ipak yo‘li", "7-sinf • O‘zbekiston tarixi", 22),
            TopicMeta("uz7_t3", "uz_7", 7, "Arablarning Movarounnahrga kirib kelishi", "7-sinf • O‘zbekiston tarixi", 19),
            TopicMeta("uz7_t4", "uz_7", 7, "Somoniylar davlatining tashkil topishi va madaniyat", "7-sinf • O‘zbekiston tarixi", 25),
            TopicMeta("uz7_t5", "uz_7", 7, "Amir Temur saltanatining yuksalishi", "7-sinf • O‘zbekiston tarixi", 30),
            TopicMeta("jahon7_t1", "jahon_7", 7, "Vizantiya imperiyasi va Salib yurishlari", "7-sinf • Jahon tarixi", 16),
            TopicMeta("uz8_t1", "uz_8", 8, "Buxoro amirligi, Xiva va Qo‘qon xonliklari", "8-sinf • O‘zbekiston tarixi", 24),
            TopicMeta("uz9_t1", "uz_9", 9, "Jadidchilik harakati va Mahmudxo‘ja Behbudiy", "9-sinf • O‘zbekiston tarixi", 21),
            TopicMeta("uz10_t1", "uz_10", 10, "Turkiston Muxtoriyati fojiasi", "10-sinf • O‘zbekiston tarixi", 17),
            TopicMeta("uz11_t1", "uz_11", 11, "O‘zbekiston Respublikasining Mustaqilligi e’lon qilinishi", "11-sinf • O‘zbekiston tarixi", 28)
        )
    }

    val gradeFilters = listOf(0 to "Barchasi", 6 to "6-sinf", 7 to "7-sinf", 8 to "8-sinf", 9 to "9-sinf", 10 to "10-sinf", 11 to "11-sinf")

    val filteredTopics = remember(searchQuery, selectedGradeFilter) {
        allTopics.filter { topic ->
            val matchesGrade = selectedGradeFilter == 0 || topic.gradeId == selectedGradeFilter
            val matchesQuery = searchQuery.isBlank() ||
                    topic.title.contains(searchQuery, ignoreCase = true) ||
                    topic.textbookTitle.contains(searchQuery, ignoreCase = true)
            matchesGrade && matchesQuery
        }
    }

    val isAllSelected = filteredTopics.isNotEmpty() && filteredTopics.all { selectedTopicIds.contains(it.id) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mavzular bo‘yicha test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isAllSelected) {
                                filteredTopics.forEach { selectedTopicIds.remove(it.id) }
                            } else {
                                filteredTopics.forEach {
                                    if (!selectedTopicIds.contains(it.id)) selectedTopicIds.add(it.id)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (isAllSelected) "Bekor qilish" else "Barchasini tanlash",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            val ids = if (selectedTopicIds.isEmpty()) filteredTopics.map { it.id } else selectedTopicIds.toList()
                            onStartTopicQuiz(ids)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_topic_quiz_button")
                    ) {
                        Text(
                            text = if (selectedTopicIds.isNotEmpty())
                                "Testni boshlash (${selectedTopicIds.size} ta mavzu)"
                            else
                                "Barcha mavzulardan test boshlash",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar for Topics & Textbooks
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Mavzu yoki darslik nomini qidirish...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Qidirish",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("topic_search_field")
            )

            Spacer(Modifier.height(10.dp))

            // Darsliklar / Sinflar bo‘yicha filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(gradeFilters) { (grade, label) ->
                    val isSelected = selectedGradeFilter == grade
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGradeFilter = grade },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
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

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${filteredTopics.size} ta mavzu topildi • ${selectedTopicIds.size} ta tanlandi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Topics list with checkboxes
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredTopics) { topic ->
                    val isChecked = selectedTopicIds.contains(topic.id)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isChecked) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isChecked) 1.5.dp else 1.dp,
                            if (isChecked) BrandPurple else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selectedTopicIds.remove(topic.id)
                                else selectedTopicIds.add(topic.id)
                            }
                            .testTag("topic_item_${topic.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedTopicIds.add(topic.id)
                                    else selectedTopicIds.remove(topic.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BrandPurple
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topic.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = topic.textbookTitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = " • ${topic.questionCount} ta test",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrandPurple
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

private data class TopicMeta(
    val id: String,
    val textbookId: String,
    val gradeId: Int,
    val title: String,
    val textbookTitle: String,
    val questionCount: Int
)
