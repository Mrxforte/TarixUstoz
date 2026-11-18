package com.tarixustoz.app.presentation.leaderboard

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class CategoryAnalysisItem(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val correctPct: Int,
    val mistakePct: Int,
    val unattemptedPct: Int,
    val totalQuestions: Int,
    val timeSpentMinutes: Int,
    val growthPct: Int,
    val categoryKey: String,
    val barGradients: List<Color>,
    val adviceList: List<String>,
    val recommendedChapter: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestAnalysisScreen(
    initialCategory: String = "Barcha testlar umumiy tahlili",
    onBack: () -> Unit,
    onStartQuiz: (String) -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsState()
    val categories = uiState.analysisCategories

    var selectedIndex by remember(categories.size, initialCategory) {
        val found = categories.indexOfFirst {
            it.title.equals(initialCategory, ignoreCase = true) ||
                it.title.contains(initialCategory, ignoreCase = true) ||
                initialCategory.contains(it.title, ignoreCase = true)
        }
        mutableIntStateOf(if (found >= 0) found else 0)
    }

    if (categories.isEmpty()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("To‘liq Tahlil", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandPurple)
            }
        }
        return
    }

    val safeIndex = selectedIndex.coerceIn(0, categories.lastIndex)
    val currentCategory = categories[safeIndex]

    var startAnim by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "analysisAnim"
    )

    LaunchedEffect(safeIndex, uiState.isLoaded) {
        startAnim = false
        delay(40)
        startAnim = true
    }

    // Dynamic advice generator state
    var currentAdviceIndex by remember(selectedIndex) { mutableIntStateOf(0) }
    var isGeneratingAdvice by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "To‘liq Tahlil & Diagrammalar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentCategory.title,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("analysis_screen_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Orqaga",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${currentCategory.correctPct}% Aniqlik",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .testTag("test_analysis_screen_content")
        ) {
            // Dropdown Filter at the top
            var expandedDropdown by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedCard(
                    onClick = { expandedDropdown = true },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, BrandPurple.copy(alpha = 0.3f)),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = currentCategory.iconColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        currentCategory.icon,
                                        contentDescription = null,
                                        tint = currentCategory.iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Bo'lim / Kategoriya",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentCategory.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = BrandPurple
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    categories.forEachIndexed { idx, cat ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        cat.icon,
                                        contentDescription = null,
                                        tint = cat.iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = cat.title,
                                        fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (selectedIndex == idx) BrandPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                selectedIndex = idx
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key metrics row cards (2x2 Grid with animated values)
                val animCorrectPct = (currentCategory.correctPct * animProgress).roundToInt()
                val animMistakePct = (currentCategory.mistakePct * animProgress).roundToInt()
                val animQuestions = (currentCategory.totalQuestions * animProgress).roundToInt()
                val animTimeMin = (currentCategory.timeSpentMinutes * animProgress).roundToInt()

                val correctCount = (animQuestions * currentCategory.correctPct / 100f).roundToInt()
                val mistakeCount = (animQuestions * currentCategory.mistakePct / 100f).roundToInt()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "To‘g‘ri javob",
                            value = "$animCorrectPct%",
                            count = "$correctCount ta",
                            color = Color(0xFF10B981),
                            bg = Color(0xFF10B981).copy(alpha = 0.12f)
                        )
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "Xatolar",
                            value = "$animMistakePct%",
                            count = "$mistakeCount ta",
                            color = Color(0xFFEF4444),
                            bg = Color(0xFFEF4444).copy(alpha = 0.12f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "Sarflangan vaqt",
                            value = "$animTimeMin daq",
                            count = "vaqt tahlili",
                            color = Color(0xFF0284C7),
                            bg = Color(0xFF0284C7).copy(alpha = 0.12f)
                        )
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "Jami savol",
                            value = "$animQuestions ta",
                            count = "ishlangan test",
                            color = BrandPurple,
                            bg = BrandPurple.copy(alpha = 0.12f)
                        )
                    }
                }

                // ========================================================
                // 1. UZEX USLUBIDAGI QIRRALI DOIRA DIAGRAMMASI (PIE CHART)
                // ========================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "1. Qirrali Doira Diagrammasi (Pie Chart)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "UZEX birja uslubidagi bo‘lak-bo‘lak qirrali tahlil",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandPurple.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "UZEX STYLE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandPurple,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Uzex Faceted Segmented Pie Chart with embedded percentage and counts
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .testTag("uzex_pie_chart_canvas")
                        ) {
                            UzexFacetedPieChart(
                                correctPct = animCorrectPct,
                                mistakePct = animMistakePct,
                                unattemptedPct = (currentCategory.unattemptedPct * animProgress).roundToInt(),
                                total = animQuestions
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Segmented Legend with numbers and percentages
                        val correctNum = (animQuestions * currentCategory.correctPct / 100f).roundToInt()
                        val mistakeNum = (animQuestions * currentCategory.mistakePct / 100f).roundToInt()
                        val unattemptedNum = (animQuestions * currentCategory.unattemptedPct / 100f).roundToInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            UzexLegendItem(
                                color = Color(0xFF10B981),
                                title = "To‘g‘ri",
                                percent = "${currentCategory.correctPct}%",
                                count = "$correctNum ta"
                            )
                            UzexLegendItem(
                                color = Color(0xFFEF4444),
                                title = "Xato",
                                percent = "${currentCategory.mistakePct}%",
                                count = "$mistakeNum ta"
                            )
                            UzexLegendItem(
                                color = Color(0xFF64748B),
                                title = "Qolgan",
                                percent = "${currentCategory.unattemptedPct}%",
                                count = "$unattemptedNum ta"
                            )
                        }
                    }
                }

                // ========================================================
                // 4. DINAMIK TAFSIYALAR VA MASLAHATLAR GENERATORI
                // ========================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Pedagogik Maslahat & Tavsiyalar",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Xatolar tahlili asosida dinamik generator",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Refresh button
                            IconButton(
                                onClick = {
                                    isGeneratingAdvice = true
                                    currentAdviceIndex = (currentAdviceIndex + 1) % currentCategory.adviceList.size
                                    isGeneratingAdvice = false
                                },
                                modifier = Modifier.testTag("refresh_dynamic_advice_btn")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Yangilash",
                                    tint = BrandPurple
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Dynamic advice content card with smooth animation
                        AnimatedContent(
                            targetState = currentAdviceIndex,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "advice_animation"
                        ) { index ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = currentCategory.adviceList[index % currentCategory.adviceList.size],
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Recommended textbook chapter link
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Tavsiya etilgan darslik bobi:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentCategory.recommendedChapter,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Start Quiz button for this topic
                        Button(
                            onClick = {
                                onStartQuiz(currentCategory.categoryKey)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("analysis_screen_start_test_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${currentCategory.title.replace("umumiy tahlili", "")} bo‘yicha test ishlash",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * UZEX Faceted Segmented Pie Chart Canvas.
 * Draws faceted segmented blocks with distinct radial spacing and sharp polygon cuts,
 * and writes exact percentages AND numbers right inside/on the sectors.
 */
@Composable
private fun UzexFacetedPieChart(
    correctPct: Int,
    mistakePct: Int,
    unattemptedPct: Int,
    total: Int
) {
    val correctCount = (total * correctPct / 100f).roundToInt()
    val mistakeCount = (total * mistakePct / 100f).roundToInt()
    val unattemptedCount = (total * unattemptedPct / 100f).roundToInt()
    val holeColor = MaterialTheme.colorScheme.surface
    val titleColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val subColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = (size.minDimension / 2f) - 16.dp.toPx()
        val innerRadius = outerRadius * 0.52f

        val totalPct = (correctPct + mistakePct + unattemptedPct).coerceAtLeast(1)
        val gapAngle = 6f // UZEX style faceted block spacing

        val slices = listOf(
            Triple(correctPct, Color(0xFF10B981), "$correctPct% ($correctCount ta)"),
            Triple(mistakePct, Color(0xFFEF4444), "$mistakePct% ($mistakeCount ta)"),
            Triple(unattemptedPct, Color(0xFF64748B), "$unattemptedPct% ($unattemptedCount ta)")
        ).filter { it.first > 0 }

        var currentStartAngle = -90f

        slices.forEach { (pct, color, _) ->
            val rawSweep = (pct.toFloat() / totalPct) * 360f
            val sweep = (rawSweep - gapAngle).coerceAtLeast(1f)

            val strokeW = outerRadius - innerRadius
            val arcRadius = (outerRadius + innerRadius) / 2f

            drawArc(
                color = color,
                startAngle = currentStartAngle + (gapAngle / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, centerY - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = currentStartAngle + (gapAngle / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - outerRadius, centerY - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
            )

            val midAngleDeg = currentStartAngle + (gapAngle / 2f) + (sweep / 2f)
            val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
            val labelRadius = (outerRadius + innerRadius) / 2f

            val labelX = centerX + (labelRadius * cos(midAngleRad)).toFloat()
            val labelY = centerY + (labelRadius * sin(midAngleRad)).toFloat()

            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    this.textSize = 14.sp.toPx()
                    this.isFakeBoldText = true
                    this.textAlign = Paint.Align.CENTER
                    this.isAntiAlias = true
                }
                if (sweep > 35f) {
                    drawText("$pct%", labelX, labelY + (paint.textSize / 3f), paint)
                }
            }

            currentStartAngle += rawSweep
        }

        // Center Donut Hole content: Big overall score
        drawCircle(
            color = holeColor,
            radius = innerRadius - 4.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        drawContext.canvas.nativeCanvas.apply {
            val titlePaint = android.graphics.Paint().apply {
                this.color = titleColorArgb
                this.textSize = 22.sp.toPx()
                this.isFakeBoldText = true
                this.textAlign = android.graphics.Paint.Align.CENTER
                this.isAntiAlias = true
            }
            val subPaint = android.graphics.Paint().apply {
                this.color = subColorArgb
                this.textSize = 11.sp.toPx()
                this.textAlign = android.graphics.Paint.Align.CENTER
                this.isAntiAlias = true
            }
            drawText("$correctPct%", centerX, centerY - 2.dp.toPx(), titlePaint)
            drawText("Natija ($total ta)", centerX, centerY + 16.dp.toPx(), subPaint)
        }
    }
}



@Composable
private fun MetricPillCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    count: String,
    color: Color,
    bg: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = count,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun UzexLegendItem(
    color: Color,
    title: String,
    percent: String,
    count: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(2.dp)) // Sharp square like UZEX facet
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = percent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
