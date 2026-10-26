package com.tarixustoz.app.presentation.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.QuestionCategory
import com.tarixustoz.app.domain.model.gradeId

private val BrandPurple = Color(0xFF6C5CE7)
private val BrandPurpleLight = Color(0xFFF0EDFF)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuizOptionsScreen(
    viewModel: QuizOptionsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onStartQuiz: (Int, QuestionCategory, Difficulty, Int, List<String>) -> Unit
) {
    var isCustomCountMode by remember { mutableStateOf(false) }
    var selectedCountPreset by remember { mutableStateOf(10) }
    var customCountText by remember { mutableStateOf("") }

    var isCustomTimeMode by remember { mutableStateOf(false) }
    var selectedTimePreset by remember { mutableStateOf(0) } // 0 = no limit
    var customTimeText by remember { mutableStateOf("") }

    var selectedCategory by remember { mutableStateOf(QuestionCategory.ALL) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedGradeFilter by remember { mutableStateOf("all") }
    val selectedTopics = remember { mutableStateListOf<String>() }

    val allTopics by viewModel.topics.collectAsState(initial = emptyList())

    val effectiveCount = remember(isCustomCountMode, selectedCountPreset, customCountText) {
        if (isCustomCountMode) {
            customCountText.toIntOrNull()?.coerceIn(1, 200) ?: 10
        } else {
            selectedCountPreset
        }
    }

    val effectiveTimeLimit = remember(isCustomTimeMode, selectedTimePreset, customTimeText) {
        if (isCustomTimeMode) {
            customTimeText.toIntOrNull()?.coerceIn(1, 180) ?: 0
        } else {
            selectedTimePreset
        }
    }

    val filteredTopics = remember(allTopics, searchQuery, selectedGradeFilter) {
        allTopics.filter { topic ->
            val matchesSearch = searchQuery.isBlank() ||
                    topic.title.contains(searchQuery, ignoreCase = true) ||
                    topic.textbookId.contains(searchQuery, ignoreCase = true)

            val matchesGrade = when (selectedGradeFilter) {
                "all" -> true
                "6_all" -> topic.gradeId == 6
                "7_uz" -> topic.gradeId == 7 && !topic.textbookId.contains("jahon")
                "7_jahon" -> topic.gradeId == 7 && topic.textbookId.contains("jahon")
                "8_uz" -> topic.gradeId == 8 && !topic.textbookId.contains("jahon")
                "8_jahon" -> topic.gradeId == 8 && topic.textbookId.contains("jahon")
                "9_uz" -> topic.gradeId == 9 && !topic.textbookId.contains("jahon")
                "9_jahon" -> topic.gradeId == 9 && topic.textbookId.contains("jahon")
                "10_uz" -> topic.gradeId == 10 && !topic.textbookId.contains("jahon")
                "10_jahon" -> topic.gradeId == 10 && topic.textbookId.contains("jahon")
                "11_uz" -> topic.gradeId == 11 && !topic.textbookId.contains("jahon")
                "11_jahon" -> topic.gradeId == 11 && topic.textbookId.contains("jahon")
                else -> true
            }

            matchesSearch && matchesGrade
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Test Sozlamalari",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$effectiveCount ta savol • ${if (effectiveTimeLimit > 0) "$effectiveTimeLimit daqiqa" else "Vaqt cheksiz"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (selectedTopics.isEmpty()) "Barcha mavzular" else "${selectedTopics.size} ta mavzu tanlandi",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onStartQuiz(
                                effectiveCount,
                                selectedCategory,
                                selectedDifficulty,
                                effectiveTimeLimit,
                                selectedTopics.toList()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "TESTNI BOSHLASH",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Savollar soni
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Savollar soni",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(10, 15, 20, 30, 45, 50, 100).forEach { count ->
                                val isSelected = !isCustomCountMode && selectedCountPreset == count
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        isCustomCountMode = false
                                        selectedCountPreset = count
                                    },
                                    label = { Text("$count ta") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPurple,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            FilterChip(
                                selected = isCustomCountMode,
                                onClick = { isCustomCountMode = true },
                                label = { Text("Ixtiyoriy son") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        if (isCustomCountMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customCountText,
                                onValueChange = { customCountText = it.filter { char -> char.isDigit() } },
                                label = { Text("Ixtiyoriy savol sonini kiriting") },
                                placeholder = { Text("Masalan: 25 yoki 36") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. Vaqt Chegarasi
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Vaqt (Daqiqa)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                0 to "Cheksiz",
                                10 to "10 daq",
                                15 to "15 daq",
                                20 to "20 daq",
                                30 to "30 daq",
                                45 to "45 daq",
                                60 to "60 daq"
                            ).forEach { (min, label) ->
                                val isSelected = !isCustomTimeMode && selectedTimePreset == min
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        isCustomTimeMode = false
                                        selectedTimePreset = min
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPurple,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            FilterChip(
                                selected = isCustomTimeMode,
                                onClick = { isCustomTimeMode = true },
                                label = { Text("Ixtiyoriy vaqt") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        if (isCustomTimeMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customTimeText,
                                onValueChange = { customTimeText = it.filter { char -> char.isDigit() } },
                                label = { Text("Ixtiyoriy vaqtni kiriting (daqiqa)") },
                                placeholder = { Text("Masalan: 12 yoki 90 daqiqa") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 3. Turkum (Category)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Turkum",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        QuestionCategory.entries.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat }
                                )
                                Text(
                                    text = "${cat.icon} ${cat.titleUz}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Qiyinchilik Darajasi
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Qiyinchilik darajasi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Difficulty.entries.forEach { diff ->
                                val isSelected = selectedDifficulty == diff
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDifficulty = diff },
                                    label = { Text(diff.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPurple,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 5. Mavzularni Tanlash (Interactive Topic Selector)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mavzularni tanlash",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(
                                onClick = {
                                    if (selectedTopics.size == filteredTopics.size && filteredTopics.isNotEmpty()) {
                                        selectedTopics.clear()
                                    } else {
                                        selectedTopics.clear()
                                        selectedTopics.addAll(filteredTopics.map { it.id })
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedTopics.size == filteredTopics.size && filteredTopics.isNotEmpty()) "Tozalash" else "Barchasini tanlash",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPurple
                                )
                            }
                        }

                        Text(
                            "Hech biri tanlanmasa, barcha mavzulardan aralashtirib beriladi.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search box
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Mavzu yoki darslik nomini qidirish...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grade filter chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val gradeFilters = listOf(
                                "all" to "Barchasi",
                                "6_all" to "6-sinf",
                                "7_uz" to "7-sinf Uzb",
                                "7_jahon" to "7-sinf Jahon",
                                "8_uz" to "8-sinf Uzb",
                                "8_jahon" to "8-sinf Jahon",
                                "9_uz" to "9-sinf Uzb",
                                "9_jahon" to "9-sinf Jahon",
                                "10_uz" to "10-sinf Uzb",
                                "10_jahon" to "10-sinf Jahon",
                                "11_uz" to "11-sinf Uzb",
                                "11_jahon" to "11-sinf Jahon"
                            )

                            items(gradeFilters) { (filterKey, label) ->
                                val isSelected = selectedGradeFilter == filterKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedGradeFilter = filterKey },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPurple,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // List of topics
            items(filteredTopics) { topic ->
                val isChecked = selectedTopics.contains(topic.id)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) BrandPurpleLight else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isChecked) BrandPurple else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (isChecked) selectedTopics.remove(topic.id)
                            else selectedTopics.add(topic.id)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked == true) selectedTopics.add(topic.id)
                                else selectedTopics.remove(topic.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BrandPurple)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${topic.gradeId}-sinf • ${topic.textbookId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}
