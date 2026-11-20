package com.tarixustoz.app.presentation.mistakes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple

private val GRADE_FILTER_OPTIONS = listOf(0, 6, 7, 8, 9, 10, 11)

private fun gradeFilterLabel(grade: Int): String = when (grade) {
    0 -> "Barcha sinflar"
    6 -> "6-sinf tarixi"
    else -> "$grade-sinf tarixi"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(
    onReviewWrongQuestions: () -> Unit,
    viewModel: MistakesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedGridItem by remember { mutableStateOf<WrongQuestionUiModel?>(null) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFilterExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val showSearch = isSearchExpanded || uiState.searchQuery.isNotEmpty()
    val isFilterActive = uiState.selectedGradeFilter > 0 || uiState.sortOrder != MistakesSortOrder.NEWEST
    val showFilter = isFilterExpanded || isFilterActive

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Xatolar tahlili",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$activeCount ta takrorlanishi kerak bo‘lgan xato",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 1. View Mode button (quadrilateral / grid view mode)
                    IconButton(
                        onClick = { viewModel.onToggleViewMode() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            if (uiState.viewMode == MistakesViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Ko‘rinishni o‘zgartirish",
                            tint = BrandPurple
                        )
                    }

                    // 2. Filter button
                    IconButton(
                        onClick = { isFilterExpanded = !isFilterExpanded },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        BadgedBox(
                            badge = {
                                if (isFilterActive && !isFilterExpanded) {
                                    Badge(
                                        containerColor = BrandPurple,
                                        modifier = Modifier.size(6.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filtrlash",
                                tint = if (showFilter) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Search button
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded && uiState.searchQuery.isNotEmpty()) {
                                viewModel.onSearchQueryChange("")
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Qidirish",
                            tint = if (showSearch) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 4. Delete sweep button
                    if (uiState.totalCount > 0) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Barcha xatolarni o‘chirish",
                                tint = Color(0xFFEF4444)
                            )
                        }
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
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.totalCount > 0) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BrandPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable(onClick = onReviewWrongQuestions)
                        .testTag("mistakes_start_review_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Xatolarni qayta ishlash",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${uiState.items.size} ta savol bo‘yicha interaktiv test",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Button(
                            onClick = onReviewWrongQuestions,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Boshlash", color = BrandPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Bar
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text("Savol matni yoki manba bo‘yicha qidirish...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .focusRequester(focusRequester)
                        .testTag("mistakes_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Grade Filter Dropdown + Sort Dropdown Row
            AnimatedVisibility(
                visible = showFilter,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                var gradeMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = gradeMenuExpanded,
                    onExpandedChange = { gradeMenuExpanded = !gradeMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = gradeFilterLabel(uiState.selectedGradeFilter),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Sinf", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeMenuExpanded)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("mistakes_grade_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = gradeMenuExpanded,
                        onDismissRequest = { gradeMenuExpanded = false }
                    ) {
                        GRADE_FILTER_OPTIONS.forEach { grade ->
                            val countForGrade = if (grade == 0) {
                                uiState.totalCount
                            } else {
                                uiState.gradeCounts[grade] ?: 0
                            }
                            val label = if (countForGrade > 0) {
                                "${gradeFilterLabel(grade)} ($countForGrade)"
                            } else {
                                gradeFilterLabel(grade)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (uiState.selectedGradeFilter == grade) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.selectedGradeFilter == grade) BrandPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.onGradeFilterChange(grade)
                                    gradeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                var sortMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sortMenuExpanded,
                    onExpandedChange = { sortMenuExpanded = !sortMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.sortOrder.label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Saralash", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortMenuExpanded)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("mistakes_sort_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        MistakesSortOrder.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sort.label,
                                        fontWeight = if (uiState.sortOrder == sort) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.sortOrder == sort) BrandPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.onSortOrderChange(sort)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            }

            // Content List or Grid
            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank() || uiState.selectedGradeFilter > 0)
                                "Tanlangan filtrlar bo‘yicha xato savol topilmadi"
                            else "Hozircha xato ishlangan savollar yo‘q!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Testlarda xato qilsangiz, ular qayta mashq qilish uchun shu yerda saqlanadi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.viewMode == MistakesViewMode.LIST) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.items, key = { it.questionId }) { item ->
                        MistakeListCard(
                            item = item,
                            onDelete = { viewModel.deleteMistake(item.questionId) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.items, key = { it.questionId }) { item ->
                        MistakeGridCard(
                            item = item,
                            onClick = { selectedGridItem = item },
                            onDelete = { viewModel.deleteMistake(item.questionId) }
                        )
                    }
                }
            }
        }
    }

    selectedGridItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedGridItem = null },
            confirmButton = {
                TextButton(onClick = { selectedGridItem = null }) {
                    Text("Yopish", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = "${item.gradeId}-sinf • ${item.sourceCitation}",
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandPurple,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MistakeInteractiveBody(
                        item = item,
                        onMarkMastered = {
                            viewModel.deleteMistake(item.questionId)
                            selectedGridItem = null
                        }
                    )
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Barcha xatolarni o‘chirish") },
            text = { Text("Barcha saqlangan xato testlar ro‘yxatdan to‘liq tozalanadi. Ishonchingiz komilmi?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMistakes()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("O‘chirish", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }
}

@Composable
private fun MistakeListCard(
    item: WrongQuestionUiModel,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandPurple.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "${item.gradeId}-sinf • ${item.sourceCitation}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${item.incorrectCount} marta xato",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Xatodan o‘chirish",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            MistakeInteractiveBody(
                item = item,
                onMarkMastered = onDelete
            )
        }
    }
}

@Composable
private fun MistakeInteractiveBody(
    item: WrongQuestionUiModel,
    onMarkMastered: () -> Unit
) {
    var selectedOptionId by remember(item.questionId) { mutableStateOf<String?>(null) }
    var revealAnswer by remember(item.questionId) { mutableStateOf(false) }

    val isRevealed = revealAnswer || selectedOptionId != null
    val pickedCorrect = selectedOptionId != null &&
        item.options.firstOrNull { it.id == selectedOptionId }?.isCorrect == true

    Text(
        text = item.text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 20.sp
    )

    if (item.userAnswer.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Avvalgi xato javobingiz: ${item.userAnswer}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // Interactive answer choices — answer stays hidden until student chooses or taps "Javobni ko'rish"
    val letters = listOf("A", "B", "C", "D")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.options.forEachIndexed { idx, opt ->
            val letter = letters.getOrElse(idx) { opt.id.uppercase() }
            val isSelected = selectedOptionId == opt.id
            val showCorrect = isRevealed && opt.isCorrect
            val showWrongSelected = isSelected && !opt.isCorrect

            val bgColor = when {
                showCorrect -> Color(0xFF16A34A).copy(alpha = 0.14f)
                showWrongSelected -> Color(0xFFDC2626).copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
            val borderColor = when {
                showCorrect -> Color(0xFF16A34A)
                showWrongSelected -> Color(0xFFDC2626)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            val textColor = when {
                showCorrect -> Color(0xFF16A34A)
                showWrongSelected -> Color(0xFFDC2626)
                else -> MaterialTheme.colorScheme.onSurface
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectedOptionId == null && !revealAnswer) {
                        selectedOptionId = opt.id
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            showCorrect -> Color(0xFF16A34A)
                            showWrongSelected -> Color(0xFFDC2626)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = letter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showCorrect || showWrongSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = opt.text,
                        fontSize = 13.sp,
                        fontWeight = if (showCorrect || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (showCorrect) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "To‘g‘ri javob",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (showWrongSelected) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Noto‘g‘ri javob",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = isRevealed) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (pickedCorrect) {
                    Color(0xFF16A34A).copy(alpha = 0.12f)
                } else {
                    BrandPurple.copy(alpha = 0.08f)
                },
                border = BorderStroke(
                    1.dp,
                    if (pickedCorrect) Color(0xFF16A34A).copy(alpha = 0.45f) else BrandPurple.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = when {
                            pickedCorrect -> "Barakalla! To‘g‘ri javobni topdingiz."
                            selectedOptionId != null -> "To‘g‘ri javob: ${item.correctOption}) ${item.correctOptionText}"
                            else -> "To‘g‘ri javob: ${item.correctOption}) ${item.correctOptionText}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pickedCorrect) Color(0xFF16A34A) else BrandPurple
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.explanation,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    if (pickedCorrect) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = onMarkMastered,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "O‘zlashtirildi — ro‘yxatdan o‘chirish",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                if (isRevealed) {
                    selectedOptionId = null
                    revealAnswer = false
                } else {
                    revealAnswer = true
                }
            },
            modifier = Modifier.testTag("mistake_toggle_answer_${item.questionId}")
        ) {
            Icon(
                if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = BrandPurple,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isRevealed) "Qayta yechish (yashirish)" else "Javob va izohni ko‘rish",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPurple
            )
        }

        Text(
            text = item.formattedDate,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MistakeGridCard(
    item: WrongQuestionUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = BrandPurple.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${item.gradeId}-sinf",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "O‘chirish",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = item.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                minLines = 3
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.incorrectCount}x xato",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )
                Text(
                    text = "Yechish →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPurple
                )
            }
        }
    }
}
