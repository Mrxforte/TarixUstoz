package com.tarixustoz.app.presentation.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarixustoz.app.presentation.theme.BrandPurple

data class HistoricalPeriod(
    val id: String,
    val title: String,
    val subtitle: String,
    val startYear: Int,
    val endYear: Int,
    val iconBg: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectionScreen(
    onBack: () -> Unit,
    onStartPeriodQuiz: (startYear: Int, endYear: Int, periodTitle: String) -> Unit
) {
    var selectedPeriodId by remember { mutableStateOf<String?>("p4") } // Default Temuriylar
    var customStartYear by remember { mutableStateOf("1900") }
    var customEndYear by remember { mutableStateOf("1955") }
    var isCustomRangeActive by remember { mutableStateOf(false) }

    val periods = remember {
        listOf(
            HistoricalPeriod("p1", "Qadimgi davr va Ilk davlatlar", "Mil.avv. IV asr — Mil. V asr • Baqtriya, Kushonlar", -400, 500, Color(0xFFEEF2FF)),
            HistoricalPeriod("p2", "Eftallar va Turk Xoqonligi", "VI — VIII asrlar • Eftallar, Sosoniylar, Turk Xoqonligi", 500, 800, Color(0xFFF5F3FF)),
            HistoricalPeriod("p3", "Somoniylar va Qoraxoniylar", "IX — XII asrlar • Uyg'onish davri madaniyati", 800, 1200, Color(0xFFFEF3C7)),
            HistoricalPeriod("p4", "Chag'atoy va Temuriylar davri", "1370 — 1500-yillar • Amir Temur, Ulug'bek, Bobur", 1370, 1500, Color(0xFFEFF6FF)),
            HistoricalPeriod("p5", "Xonliklar davri", "XVI — XIX asr I yarmi • Buxoro, Xiva, Qo'qon xonligi", 1500, 1865, Color(0xFFF0FDF4)),
            HistoricalPeriod("p6", "Jadidchilik va Mustamlakachilik", "1860 — 1917-yillar • Behbudiy, Avloniy, Jadidlar", 1860, 1917, Color(0xFFFFF7ED)),
            HistoricalPeriod("p7", "Sho'rolar davri va Qatag'on", "1917 — 1991-yillar • Turkiston Muxtoriyati, II Jahon urushi", 1917, 1991, Color(0xFFFEF2F2)),
            HistoricalPeriod("p8", "Mustaqillik davri", "1991-yildan hozirgacha • Zamonaviy O'zbekiston tarixi", 1991, 2026, Color(0xFFF5F3FF))
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yillar va Davrlar bo‘yicha test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Custom Range Selection Box
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCustomRangeActive) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.5.dp,
                        if (isCustomRangeActive) BrandPurple else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isCustomRangeActive = true
                            selectedPeriodId = null
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isCustomRangeActive,
                                onClick = {
                                    isCustomRangeActive = true
                                    selectedPeriodId = null
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Aynan yillar oralig‘ini kiritish (Masalan: 1900–1955)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCustomRangeActive) BrandPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isCustomRangeActive) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = customStartYear,
                                    onValueChange = { customStartYear = it },
                                    label = { Text("Boshlanish yili") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = customEndYear,
                                    onValueChange = { customEndYear = it },
                                    label = { Text("Tugash yili") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Tarixiy Davrlar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(periods) { period ->
                val isSelected = !isCustomRangeActive && selectedPeriodId == period.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) BrandPurple else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isCustomRangeActive = false
                            selectedPeriodId = period.id
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = period.iconBg,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = period.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = period.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (isCustomRangeActive) {
                            val start = customStartYear.toIntOrNull() ?: 1900
                            val end = customEndYear.toIntOrNull() ?: 1955
                            onStartPeriodQuiz(start, end, "$start–$end-yillar davri")
                        } else {
                            val period = periods.find { it.id == selectedPeriodId } ?: periods[3]
                            onStartPeriodQuiz(period.startYear, period.endYear, period.title)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text(
                        text = "Ushbu davrdan testni boshlash",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
