package com.tarixustoz.app.presentation.quiz

import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
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
import com.tarixustoz.app.domain.model.gradeId
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
    val selectedTopicIds = remember { mutableStateListOf<String>() }

    val realTopics by viewModel.allTopicsFlow.collectAsState()
    val topicCountsMap by viewModel.topicCountsMap.collectAsState()
    var selectedBookFilter by remember { mutableStateOf("all") }
    var isReversedSort by remember { mutableStateOf(false) }

    val allTopics = remember(realTopics, topicCountsMap) {
        realTopics.map { t ->
            val textbookTitle = when {
                t.textbookId == "uz_6" -> "6-sinf • Qadimgi dunyo tarixi"
                t.textbookId == "uz_7" -> "7-sinf • O‘zbekiston tarixi"
                t.textbookId == "jahon_7" -> "7-sinf • Jahon tarixi"
                t.textbookId == "uz_8" -> "8-sinf • O‘zbekiston tarixi"
                t.textbookId == "jahon_8" -> "8-sinf • Jahon tarixi"
                t.textbookId == "uz_9" -> "9-sinf • O‘zbekiston tarixi"
                t.textbookId == "jahon_9" -> "9-sinf • Jahon tarixi"
                t.textbookId == "uz_10" -> "10-sinf • O‘zbekiston tarixi"
                t.textbookId == "jahon_10" -> "10-sinf • Jahon tarixi"
                t.textbookId == "uz_11" -> "11-sinf • O‘zbekiston tarixi"
                t.textbookId == "jahon_11" -> "11-sinf • Jahon tarixi"
                t.textbookId.contains("jahon") -> "${t.gradeId}-sinf • Jahon tarixi"
                else -> "${t.gradeId}-sinf • O‘zbekiston tarixi"
            }
            val realCount = topicCountsMap[t.id]
                ?: topicCountsMap["topic_${t.id}"]
                ?: topicCountsMap[t.id.removePrefix("topic_")]
                ?: 0

            TopicMeta(
                id = t.id,
                textbookId = t.textbookId,
                gradeId = t.gradeId,
                title = t.title,
                textbookTitle = textbookTitle,
                questionCount = realCount,
                number = t.number
            )
        }
    }

    // Sort chronologically starting from 6-sinf to 11-sinf
    val sortedTopics = remember(allTopics, isReversedSort) {
        val baseSorted = allTopics.sortedWith(
            compareBy<TopicMeta> { it.gradeId }
                .thenBy { if (it.textbookId.contains("jahon")) 1 else 0 }
                .thenBy { it.number }
                .thenBy { it.title }
        )
        if (isReversedSort) baseSorted.reversed() else baseSorted
    }

    val gradeFilters = listOf(
        "all" to "Barchasi (381 ta mavzu)",
        "6_all" to "6-sinf (44 ta mavzu)",
        "7_uz" to "7-sinf O‘zbekiston (35 ta mavzu)",
        "7_jahon" to "7-sinf Jahon (40 ta mavzu)",
        "8_uz" to "8-sinf O‘zbekiston (45 ta mavzu)",
        "8_jahon" to "8-sinf Jahon (35 ta mavzu)",
        "9_uz" to "9-sinf O‘zbekiston (30 ta mavzu)",
        "9_jahon" to "9-sinf Jahon (40 ta mavzu)",
        "10_uz" to "10-sinf O‘zbekiston (30 ta mavzu)",
        "10_jahon" to "10-sinf Jahon (30 ta mavzu)",
        "11_uz" to "11-sinf O‘zbekiston (25 ta mavzu)",
        "11_jahon" to "11-sinf Jahon (25 ta mavzu)"
    )

    val filteredTopics = remember(searchQuery, selectedBookFilter, sortedTopics) {
        sortedTopics.filter { topic ->
            val matchesFilter = when (selectedBookFilter) {
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
            val matchesQuery = searchQuery.isBlank() ||
                    topic.title.contains(searchQuery, ignoreCase = true) ||
                    topic.textbookTitle.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val isAllSelected = filteredTopics.isNotEmpty() && filteredTopics.all { selectedTopicIds.contains(it.id) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
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
                windowInsets = WindowInsets(0.dp),
                actions = {
                    IconButton(
                        onClick = { isReversedSort = !isReversedSort },
                        modifier = Modifier.testTag("reverse_sort_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Tartibni almashtirish (Reverse)",
                            tint = BrandPurple
                        )
                    }

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
                items(gradeFilters) { (filterKey, label) ->
                    val isSelected = selectedBookFilter == filterKey
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { selectedBookFilter = filterKey }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Subheader: topic count and Reverse sort toggle chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTopics.size} ta mavzu topildi • ${selectedTopicIds.size} ta tanlandi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandPurple.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BrandPurple.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { isReversedSort = !isReversedSort }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isReversedSort) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = BrandPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isReversedSort) "11 ➔ 6-sinf (Z-A)" else "6 ➔ 11-sinf (A-Z)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPurple
                        )
                    }
                }
            }

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
    val questionCount: Int,
    val number: Int = 0
)
