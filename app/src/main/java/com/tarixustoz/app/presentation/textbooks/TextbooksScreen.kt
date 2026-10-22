package com.tarixustoz.app.presentation.textbooks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Textbook
import com.tarixustoz.app.presentation.theme.BrandPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextbooksScreen(
    onTextbookSelected: (String) -> Unit,
    viewModel: TextbooksViewModel = hiltViewModel()
) {
    val textbooks by viewModel.textbooks.collectAsState()
    var selectedGrade by remember { mutableIntStateOf(0) } // 0 = Barchasi
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) } // Default is GRID (Ozon style)
    val favoriteBooks = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val gradeFilters = listOf(0 to "Barchasi", 6 to "6-sinf", 7 to "7-sinf", 8 to "8-sinf", 9 to "9-sinf", 10 to "10-sinf", 11 to "11-sinf")

    val allSeededTextbooks = remember {
        listOf(
            Textbook("uz_6", "Tarix. Qadimgi dunyo", 6, "jahon", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", null, 12, 40, 80),
            Textbook("uz_7", "O‘zbekiston tarixi (IV–XVI asr)", 7, "uzbekiston", "A. Muhammadjonov", 2017, "3-nashr", null, 10, 35, 70),
            Textbook("jahon_7", "Jahon tarixi (V–XV asr)", 7, "jahon", "T.O‘. Salimov, F.E. Sultonov", 2017, "3-nashr", null, 12, 40, 75),
            Textbook("uz_8", "O‘zbekiston tarixi", 8, "uzbekiston", "U. Jo‘rayev va b.", 2019, "4-nashr", null, 12, 45, 90),
            Textbook("jahon_8", "Jahon tarixi (XVI–XIX asr)", 8, "jahon", "R. Farmonov, U. Jo‘rayev, Sh. Ergashev", 2019, "4-nashr", null, 10, 35, 80),
            Textbook("uz_9", "O‘zbekiston tarixi (XIX asr II yarmi – XX asr)", 9, "uzbekiston", "S. Tillaboyev, A. Zamonov", 2019, "3-nashr", null, 10, 30, 70),
            Textbook("jahon_9", "Jahon tarixi", 9, "jahon", "M. Lafasov va b.", 2019, "4-nashr", null, 12, 40, 85),
            Textbook("uz_10", "O‘zbekiston tarixi (1917–1991)", 10, "uzbekiston", "Q. Rajabov, A. Zamonov", 2017, "1-nashr", null, 10, 30, 65),
            Textbook("jahon_10", "Jahon tarixi (1918–1991)", 10, "jahon", "Sh. Ergashev, B. Xodjayev", 2017, "1-nashr", null, 10, 30, 60),
            Textbook("uz_11", "O‘zbekiston tarixi (Mustaqillik davri)", 11, "uzbekiston", "N. Jo‘rayev, A. Zamonov", 2018, "1-nashr", null, 8, 25, 55),
            Textbook("jahon_11", "Jahon tarixi (1991–2017)", 11, "jahon", "Sh. Ergashev, B. Xodjayev", 2018, "1-nashr", null, 8, 25, 50)
        )
    }

    val displayList = remember(selectedGrade, searchQuery, textbooks) {
        val baseList = if (selectedGrade == 0) allSeededTextbooks else {
            if (textbooks.isNotEmpty()) textbooks else allSeededTextbooks.filter { it.gradeId == selectedGrade }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.authors.contains(searchQuery, ignoreCase = true) ||
                        "${it.gradeId}-sinf".contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Darsliklar",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Darslik yoki muallifni qidirish...",
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
                    .testTag("textbook_search_field")
            )

            Spacer(Modifier.height(8.dp))

            // Grade filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(gradeFilters) { (grade, label) ->
                    val isSelected = selectedGrade == grade
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedGrade = grade
                            if (grade > 0) viewModel.setGrade(grade)
                        },
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

            // View Mode Toggle (Grid vs List) & Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayList.size} ta darslik topildi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Toggle Button for Grid vs List
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        IconButton(
                            onClick = { isGridView = true },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isGridView) BrandPurple.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .testTag("textbooks_view_grid")
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Grid ko‘rinishi",
                                tint = if (isGridView) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { isGridView = false },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (!isGridView) BrandPurple.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .testTag("textbooks_view_list")
                        ) {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = "Ro‘yxat ko‘rinishi",
                                tint = if (!isGridView) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (isGridView) {
                // 🛍️ OZON STYLE PRODUCT CARDS GRID
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(displayList) { textbook ->
                        val isUz = textbook.subjectId == "uzbekiston"
                        val isFav = favoriteBooks.contains(textbook.id)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 0.5.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTextbookSelected(textbook.id) }
                                .testTag("ozon_card_${textbook.id}")
                        ) {
                            Column {
                                // Product Card Header (Visual / Cover Area)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                if (isUz) listOf(Color(0xFF5D5FEF), Color(0xFF8B5CF6))
                                                else listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                            )
                                        )
                                        .padding(8.dp)
                                ) {
                                    // Grade Badge Pill
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.35f),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "${textbook.gradeId}-sinf",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Favorite Heart Icon Button
                                    IconButton(
                                        onClick = {
                                            if (isFav) favoriteBooks.remove(textbook.id)
                                            else favoriteBooks.add(textbook.id)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp)
                                            .background(Color.White.copy(alpha = 0.85f), shape = CircleShape)
                                    ) {
                                        Icon(
                                            if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Sevimli",
                                            tint = if (isFav) Color(0xFFEF4444) else Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Center Icon & Title Hint
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (isUz) "O‘zbekiston" else "Jahon",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Edition tag
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    ) {
                                        Text(
                                            text = "${textbook.year}-yil",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Product Card Body
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Rating & Review badge like Ozon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            text = "4.9",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "• ${textbook.totalQuestions} test",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    // Product Title
                                    Text(
                                        text = textbook.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )

                                    Spacer(Modifier.height(2.dp))

                                    // Authors
                                    Text(
                                        text = textbook.authors,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    // Chapters & Topics Meta
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEEF2FF)
                                        ) {
                                            Text(
                                                text = "${textbook.totalTopics} mavzu",
                                                color = BrandPurple,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFDCFCE7)
                                        ) {
                                            Text(
                                                text = "Bepul",
                                                color = Color(0xFF15803D),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Action Button
                                    Button(
                                        onClick = { onTextbookSelected(textbook.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                    ) {
                                        Text(
                                            text = "Testni yechish",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // LIST VIEW
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(displayList) { textbook ->
                        val isUz = textbook.subjectId == "uzbekiston"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTextbookSelected(textbook.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUz) Color(0xFFEEF2FF) else Color(0xFFFEF3C7),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.MenuBook,
                                                contentDescription = null,
                                                tint = if (isUz) BrandPurple else Color(0xFFD97706),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "${textbook.gradeId}-sinf",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUz) BrandPurple else Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = textbook.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${textbook.authors} (${textbook.year})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "⭐ 4.9 • ${textbook.totalTopics} mavzu • ${textbook.totalQuestions} test",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrandPurple
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
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}
