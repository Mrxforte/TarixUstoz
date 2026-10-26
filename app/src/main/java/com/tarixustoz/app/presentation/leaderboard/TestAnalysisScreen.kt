package com.tarixustoz.app.presentation.leaderboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
    onStartQuiz: (String) -> Unit = {}
) {
    BackHandler { onBack() }

    // Categories dataset with comprehensive stats
    val categories = remember {
        listOf(
            CategoryAnalysisItem(
                title = "Barcha testlar umumiy tahlili",
                icon = Icons.Default.Leaderboard,
                iconColor = BrandPurple,
                correctPct = 68,
                mistakePct = 18,
                unattemptedPct = 14,
                totalQuestions = 480,
                growthPct = 26,
                categoryKey = "all",
                barGradients = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                adviceList = listOf(
                    "Umumiy bilim darajangiz 68% ni tashkil qilmoqda. O‘sish sur‘ati oxirgi haftada +26% ga oshdi.",
                    "Eng zaif nuqtangiz: Sanalar va Urushlar bo‘limi. Ushbu mavzularga haftasiga kamida 2 marta e’tibor qarating.",
                    "Tavsiya: Har kuni kamida 15 ta test ishlab, xatolar ustida ishlash bo‘limini to‘liq yakunlang."
                ),
                recommendedChapter = "6-sinf Tarix: 1-bob. Tarix fani va uning manbalari"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy voqealar",
                icon = Icons.Default.Event,
                iconColor = Color(0xFF6366F1),
                correctPct = 65,
                mistakePct = 15,
                unattemptedPct = 20,
                totalQuestions = 120,
                growthPct = 22,
                categoryKey = "tarixiy_voqealar",
                barGradients = listOf(Color(0xFF4F46E5), Color(0xFF818CF8)),
                adviceList = listOf(
                    "Qadimgi dunyo davlatlarining tashkil topishi va inqirozi bo‘yicha xronologik ketma-ketlikka e’tibor bering.",
                    "Maslahat: Miloddan avvalgi va milodiy asrlarni hisoblashdagi chalkashliklarni bartaraf etish uchun vaqt o‘qi chizmasidan foydalaning.",
                    "Oxirgi 5 kunda o‘sish sur‘ati +22% ga yetdi. Xatolarni takrorlash bo‘limini muntazam ishlab boring."
                ),
                recommendedChapter = "6-sinf Qadimgi dunyo tarixi: 4-bob. Bobil va Mesopotamiya madaniyati"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy faktlar",
                icon = Icons.Default.AccountBalance,
                iconColor = Color(0xFF8B5CF6),
                correctPct = 52,
                mistakePct = 28,
                unattemptedPct = 20,
                totalQuestions = 95,
                growthPct = 18,
                categoryKey = "tarixiy_obidalar",
                barGradients = listOf(Color(0xFF7C3AED), Color(0xFFC084FC)),
                adviceList = listOf(
                    "Bibixonim madrasasi, Registon ansambli (Ulug‘bek, Sherdor, Tillakori) qurilgan asrlar va me’morlar bo‘yicha xatolar aniqlandi.",
                    "Maslahat: Har bir me’moriy yodgorlikni davr hukmdori va shahri bilan kartochka qilib takrorlang.",
                    "Me’morchilik atamalari: gumbaz, peshtoq va muqarnas uslublarini qayta ko‘rib chiqing."
                ),
                recommendedChapter = "8-sinf O‘zbekiston tarixi: Samarqand va Buxoro me’moriy durdonalari"
            ),
            CategoryAnalysisItem(
                title = "Urushlar va janglar",
                icon = Icons.Default.Security,
                iconColor = Color(0xFFDC2626),
                correctPct = 58,
                mistakePct = 24,
                unattemptedPct = 18,
                totalQuestions = 110,
                growthPct = 25,
                categoryKey = "urushlar_va_janglar",
                barGradients = listOf(Color(0xFFDC2626), Color(0xFFFB7185)),
                adviceList = listOf(
                    "Jaloliddin Manguberdi va Chingizxon o‘rtasidagi Sind daryosi bo‘yidagi jang hamda Amir Temur yurishlarida sanalar va sarkardalar nomlarini farqlashda diqqatli bo‘ling.",
                    "Taktik maslahat: Jang sanalarini xaritadagi joylashuvi bilan bog‘lab yodlasangiz, xatoliklar darhol 40% ga kamayadi.",
                    "O‘sish sur‘ati yuqori (+25%). DTM bloklarida harbiy sarkardalar bo‘yicha kamida 3 ta savol tushadi."
                ),
                recommendedChapter = "7-sinf Tarix: 14-bob. Amir Temurning To‘xtamishga qarshi yurishlari"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy shaxslar",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF059669),
                correctPct = 82,
                mistakePct = 10,
                unattemptedPct = 8,
                totalQuestions = 135,
                growthPct = 34,
                categoryKey = "tarixiy_shaxslar",
                barGradients = listOf(Color(0xFF059669), Color(0xFF34D399)),
                adviceList = listOf(
                    "Ajoyib natija! Tarixiy shaxslar bo‘yicha to‘g‘rilik ko‘rsatkichi 82% dan oshdi.",
                    "Kichik maslahat: Beruniy, Ibn Sino va Ulug‘bek asarlarining arabcha nomlarini yodda saqlashga e’tibor qarating.",
                    "O‘sish davom etmoqda. Temuriylar davri allomalari bo‘yicha bilimlaringiz juda mustahkam."
                ),
                recommendedChapter = "7-sinf Tarix: 18-bob. Mirzo Ulug‘bek va uning ilmiy akademiyasi"
            ),
            CategoryAnalysisItem(
                title = "Yillar va sanalar",
                icon = Icons.Default.DateRange,
                iconColor = Color(0xFFD97706),
                correctPct = 48,
                mistakePct = 32,
                unattemptedPct = 20,
                totalQuestions = 85,
                growthPct = 15,
                categoryKey = "yillar_boyicha",
                barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                adviceList = listOf(
                    "Sanalar va yillar bo‘limida xatoliklar 32% ni tashkil qilmoqda. Bu bo‘lim eng ko‘p diqqat talab etadi.",
                    "Pedagogik tavsiya: Asrlar va o‘n yilliklarni guruhlab yodlang: masalan, VIII asr - Somoniylar va arablar istilosi.",
                    "Kunlik 10 tadan sana testini yechish orqali 1 haftada xatoliklarni 10% ga tushirish mumkin."
                ),
                recommendedChapter = "8-sinf Tarix: Xronologik jadvallar va muhim sanalar"
            )
        )
    }

    var selectedIndex by remember {
        val found = categories.indexOfFirst {
            it.title.equals(initialCategory, ignoreCase = true) ||
                    it.title.contains(initialCategory, ignoreCase = true) ||
                    initialCategory.contains(it.title, ignoreCase = true)
        }
        mutableIntStateOf(if (found >= 0) found else 0)
    }

    val currentCategory = categories[selectedIndex]

    // Dynamic advice generator state
    var currentAdviceIndex by remember(selectedIndex) { mutableIntStateOf(0) }
    var isGeneratingAdvice by remember { mutableStateOf(false) }

    // Bitcoin style line chart timeframe filter: 24S, 7K, 1O, 1Y, Barchasi
    var bitcoinTimeframe by remember { mutableStateOf("7K") }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
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
                windowInsets = WindowInsets(0.dp),
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "+${currentCategory.growthPct}% O‘sish",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
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
            // Category selector horizontal scroll tabs
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BrandPurple,
                divider = {}
            ) {
                categories.forEachIndexed { idx, cat ->
                    Tab(
                        selected = selectedIndex == idx,
                        onClick = { selectedIndex = idx },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    cat.icon,
                                    contentDescription = null,
                                    tint = if (selectedIndex == idx) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = cat.title.replace("umumiy tahlili", "").trim(),
                                    fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key metrics row cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val correctCount = (currentCategory.totalQuestions * currentCategory.correctPct / 100f).roundToInt()
                    val mistakeCount = (currentCategory.totalQuestions * currentCategory.mistakePct / 100f).roundToInt()

                    MetricPillCard(
                        modifier = Modifier.weight(1f),
                        title = "To‘g‘ri javob",
                        value = "${currentCategory.correctPct}%",
                        count = "$correctCount ta",
                        color = Color(0xFF10B981),
                        bg = Color(0xFFECFDF5)
                    )
                    MetricPillCard(
                        modifier = Modifier.weight(1f),
                        title = "Xatolar",
                        value = "${currentCategory.mistakePct}%",
                        count = "$mistakeCount ta",
                        color = Color(0xFFEF4444),
                        bg = Color(0xFFFEF2F2)
                    )
                    MetricPillCard(
                        modifier = Modifier.weight(1f),
                        title = "Jami savol",
                        value = "${currentCategory.totalQuestions}",
                        count = "savol",
                        color = BrandPurple,
                        bg = Color(0xFFF5F3FF)
                    )
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
                                correctPct = currentCategory.correctPct,
                                mistakePct = currentCategory.mistakePct,
                                unattemptedPct = currentCategory.unattemptedPct,
                                total = currentCategory.totalQuestions
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Segmented Legend with numbers and percentages
                        val correctNum = (currentCategory.totalQuestions * currentCategory.correctPct / 100f).roundToInt()
                        val mistakeNum = (currentCategory.totalQuestions * currentCategory.mistakePct / 100f).roundToInt()
                        val unattemptedNum = (currentCategory.totalQuestions * currentCategory.unattemptedPct / 100f).roundToInt()

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
                // 2. BITCOIN / KRIPTO USLUBIDAGI MAYDA CHIZIQLI LINE CHART
                // ========================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A), // Crypto Dark aesthetic
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "₿",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "2. Bitcoin Uslubidagi Mayda Line Chart",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Mayda va o‘ta aniq chiziqli dinamika grafigi",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            // Live Rate Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${currentCategory.correctPct}.4% ▲ LIVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Crypto metrics ticker bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Joriy Natija",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${currentCategory.correctPct}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }
                            Column {
                                Text(
                                    text = "Eng Yuqori (ATH)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${(currentCategory.correctPct + 14).coerceAtMost(99)}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )
                            }
                            Column {
                                Text(
                                    text = "Hajm (24s)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${currentCategory.totalQuestions} savol",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            Column {
                                Text(
                                    text = "O‘sish sur‘ati",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "+${currentCategory.growthPct}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Timeframe selection buttons like Binance / TradingView
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("24S", "7K", "1O", "1Y", "Barchasi").forEach { tf ->
                                val isSelected = bitcoinTimeframe == tf
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF334155) else Color.Transparent)
                                        .clickable { bitcoinTimeframe = tf }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tf,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Bitcoin Fine Line Chart Canvas with numbers & percents embedded
                        BitcoinFineLineChart(
                            timeframe = bitcoinTimeframe,
                            basePercent = currentCategory.correctPct,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .testTag("bitcoin_line_chart_canvas")
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Dush",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Sesh",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Chor",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Pay",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Juma",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Shan",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Bugun (Yak)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                // ========================================================
                // 3. MULTI-COLOR USTUNLI DIAGRAMMA (BAR / COLUMN CHART)
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
                            Column {
                                Text(
                                    text = "3. Mavzular bo‘yicha Ustunli Diagramma",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rang-barang ustunlar (Column Bar Chart) va aniq foizlar",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "MULTI-COLOR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6366F1),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Multi-color column bar chart canvas
                        MultiColorColumnBarChart(
                            categories = categories.drop(1), // show subcategories as columns
                            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else -1,
                            onSelect = { colIdx ->
                                selectedIndex = colIdx + 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("multicolor_bar_chart_canvas")
                        )

                        Spacer(Modifier.height(14.dp))

                        // Reference benchmark indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp, 2.dp)
                                        .background(Color(0xFFE11D48), RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "70% O‘tish me’yori",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "Ustun ustiga bosib almashtiring",
                                fontSize = 11.sp,
                                color = BrandPurple,
                                fontWeight = FontWeight.SemiBold
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
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = currentCategory.adviceList[index % currentCategory.adviceList.size],
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = Color(0xFF78350F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Recommended textbook chapter link
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Tavsiya etilgan darslik bobi:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = currentCategory.recommendedChapter,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
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
        )

        var currentStartAngle = -90f

        slices.forEach { (pct, color, label) ->
            val rawSweep = (pct.toFloat() / totalPct) * 360f
            val sweep = (rawSweep - gapAngle).coerceAtLeast(1f)

            // Draw Faceted Segmented Ring Arc (Donut slice with sharp bevels)
            val strokeW = outerRadius - innerRadius
            val arcRadius = (outerRadius + innerRadius) / 2f

            drawArc(
                color = color,
                startAngle = currentStartAngle + (gapAngle / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, centerY - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Butt) // Butt cap for UZEX sharp facets!
            )

            // Subtle faceted inner highlight border for UZEX block feel
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = currentStartAngle + (gapAngle / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - outerRadius, centerY - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
            )

            // Calculate radial center of this slice for drawing text inside the slice
            val midAngleDeg = currentStartAngle + (gapAngle / 2f) + (sweep / 2f)
            val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
            val labelRadius = (outerRadius + innerRadius) / 2f

            val labelX = centerX + (labelRadius * cos(midAngleRad)).toFloat()
            val labelY = centerY + (labelRadius * sin(midAngleRad)).toFloat()

            // Draw text directly inside the slice using nativeCanvas
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    this.textSize = 12.sp.toPx()
                    this.isFakeBoldText = true
                    this.textAlign = android.graphics.Paint.Align.CENTER
                    this.isAntiAlias = true
                    setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
                }

                // If sweep is large enough, write full text, otherwise short percent
                val textToDraw = if (sweep > 35f) label else "$pct%"
                drawText(textToDraw, labelX, labelY + (paint.textSize / 3f), paint)
            }

            currentStartAngle += rawSweep
        }

        // Center Donut Hole content: Big overall score
        drawCircle(
            color = Color(0xFFF8FAFC),
            radius = innerRadius - 4.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        drawContext.canvas.nativeCanvas.apply {
            val titlePaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.parseColor("#1E293B")
                this.textSize = 22.sp.toPx()
                this.isFakeBoldText = true
                this.textAlign = android.graphics.Paint.Align.CENTER
                this.isAntiAlias = true
            }
            val subPaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.parseColor("#64748B")
                this.textSize = 11.sp.toPx()
                this.textAlign = android.graphics.Paint.Align.CENTER
                this.isAntiAlias = true
            }
            drawText("$correctPct%", centerX, centerY - 2.dp.toPx(), titlePaint)
            drawText("Natija ($total ta)", centerX, centerY + 16.dp.toPx(), subPaint)
        }
    }
}

/**
 * Bitcoin / Crypto-style fine Line Chart Canvas.
 * Fine, slender curve with micro-fluctuations, dotted micro-grid, glowing gradient fill,
 * and numbers/percentages written directly on key points.
 */
@Composable
private fun BitcoinFineLineChart(
    timeframe: String,
    basePercent: Int,
    modifier: Modifier = Modifier
) {
    // Generate fine crypto-like micro points based on timeframe
    val dataPoints = remember(timeframe, basePercent) {
        val count = 20
        val points = mutableListOf<Float>()
        var current = (basePercent - 18).coerceAtLeast(25).toFloat()
        val randomDeltas = listOf(-1.2f, 2.5f, -0.8f, 3.1f, 1.4f, -1.9f, 2.8f, 4.2f, -1.0f, 3.5f, 2.0f, -0.5f, 4.0f, 1.5f, -2.1f, 3.8f, 2.2f, 1.9f, 3.4f, 2.6f)

        points.add(current)
        for (i in 1 until count) {
            val delta = randomDeltas[(i + timeframe.hashCode().mod(5)) % randomDeltas.size]
            current = (current + delta).coerceIn(20f, 96f)
            points.add(current)
        }
        // Ensure last point aligns with current score
        points[points.size - 1] = basePercent.toFloat()
        points
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 32.dp.toPx()
        val paddingRight = 44.dp.toPx()
        val paddingTop = 26.dp.toPx()
        val paddingBottom = 22.dp.toPx()

        val chartW = width - paddingLeft - paddingRight
        val chartH = height - paddingTop - paddingBottom

        val minVal = 0f
        val maxVal = 100f

        // Draw fine dotted crypto grid lines (horizontal 20%, 40%, 60%, 80%, 100%)
        val gridRatios = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)

        gridRatios.forEach { r ->
            val y = paddingTop + chartH * (1f - r)
            drawLine(
                color = Color(0xFF334155),
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = dashEffect
            )

            // Right-side crypto level text label
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.parseColor("#64748B")
                    this.textSize = 9.sp.toPx()
                    this.textAlign = android.graphics.Paint.Align.LEFT
                    this.isAntiAlias = true
                }
                drawText("${(r * 100).toInt()}%", width - paddingRight + 6.dp.toPx(), y + 3.dp.toPx(), p)
            }
        }

        // Calculate points coordinates
        val stepX = chartW / (dataPoints.size - 1).coerceAtLeast(1)
        val points = dataPoints.mapIndexed { i, v ->
            val x = paddingLeft + (i * stepX)
            val normalized = (v - minVal) / (maxVal - minVal)
            val y = paddingTop + chartH * (1f - normalized)
            Offset(x, y)
        }

        // 1. Fill gradient path underneath the curve
        val fillPath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, paddingTop + chartH)
                lineTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    val pPrev = points[i - 1]
                    val pCur = points[i]
                    val controlX = (pPrev.x + pCur.x) / 2f
                    cubicTo(controlX, pPrev.y, controlX, pCur.y, pCur.x, pCur.y)
                }

                lineTo(points.last().x, paddingTop + chartH)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF10B981).copy(alpha = 0.32f),
                    Color(0xFF10B981).copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = paddingTop,
                endY = paddingTop + chartH
            )
        )

        // 2. Mayda, o‘ta nozik chiziq (Fine bitcoin stroke: 1.8dp width)
        val strokePath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val pPrev = points[i - 1]
                    val pCur = points[i]
                    val controlX = (pPrev.x + pCur.x) / 2f
                    cubicTo(controlX, pPrev.y, controlX, pCur.y, pCur.x, pCur.y)
                }
            }
        }

        // Fine Bitcoin crypto line stroke
        drawPath(
            path = strokePath,
            color = Color(0xFF10B981),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw numbers & percentages on key points: Min, Mid, and Latest
        val minPointIdx = dataPoints.indices.minByOrNull { dataPoints[it] } ?: 0
        val maxPointIdx = dataPoints.indices.maxByOrNull { dataPoints[it] } ?: (dataPoints.size - 1)
        val lastPointIdx = dataPoints.size - 1

        val highlighted = listOf(minPointIdx, maxPointIdx, lastPointIdx).distinct()

        highlighted.forEach { idx ->
            val pt = points[idx]
            val value = dataPoints[idx].roundToInt()
            val isLast = idx == lastPointIdx

            // Circle marker
            drawCircle(
                color = if (isLast) Color(0xFF34D399) else Color(0xFF10B981),
                radius = if (isLast) 5.dp.toPx() else 3.5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = Color(0xFF0F172A),
                radius = if (isLast) 2.5.dp.toPx() else 1.8.dp.toPx(),
                center = pt
            )

            // Pill tag with numbers and percentages written on canvas
            val tagText = if (isLast) "$value% (Hozir)" else "$value%"
            drawContext.canvas.nativeCanvas.apply {
                val textPaint = android.graphics.Paint().apply {
                    this.color = if (isLast) android.graphics.Color.parseColor("#34D399") else android.graphics.Color.WHITE
                    this.textSize = if (isLast) 11.sp.toPx() else 9.sp.toPx()
                    this.isFakeBoldText = true
                    this.textAlign = if (isLast) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.CENTER
                    this.isAntiAlias = true
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
                }

                val tagY = (pt.y - 8.dp.toPx()).coerceAtLeast(paddingTop + 8.dp.toPx())
                val tagX = if (isLast) pt.x else pt.x
                drawText(tagText, tagX, tagY, textPaint)
            }
        }
    }
}

/**
 * Multi-color Column Bar Chart Canvas.
 * Each category gets a vibrant custom gradient column with rounded top corners,
 * and exact percentages AND numbers clearly written on top of each bar.
 */
@Composable
private fun MultiColorColumnBarChart(
    categories: List<CategoryAnalysisItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.clickable {
            // Can be clicked to select
        }
    ) {
        val width = size.width
        val height = size.height
        val bottomLabelH = 46.dp.toPx()
        val topLabelH = 28.dp.toPx()
        val chartH = height - bottomLabelH - topLabelH

        val columnCount = categories.size
        val colSpacing = 12.dp.toPx()
        val availableW = width - (colSpacing * (columnCount + 1))
        val colWidth = (availableW / columnCount).coerceAtLeast(18.dp.toPx())

        // Draw 70% benchmark line
        val benchmarkY = topLabelH + chartH * (1f - 0.70f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
        drawLine(
            color = Color(0xFFE11D48).copy(alpha = 0.6f),
            start = Offset(colSpacing, benchmarkY),
            end = Offset(width - colSpacing, benchmarkY),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = dashEffect
        )

        categories.forEachIndexed { idx, item ->
            val x = colSpacing + idx * (colWidth + colSpacing)
            val barH = chartH * (item.correctPct / 100f)
            val barY = topLabelH + chartH - barH

            val isSelected = selectedIndex == idx
            val gradColors = if (isSelected) {
                listOf(item.barGradients.first(), item.barGradients.last())
            } else {
                listOf(
                    item.barGradients.first().copy(alpha = 0.85f),
                    item.barGradients.last().copy(alpha = 0.85f)
                )
            }

            // Draw Column Bar with rounded top corners
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = gradColors,
                    startY = barY,
                    endY = barY + barH
                ),
                topLeft = Offset(x, barY),
                size = Size(colWidth, barH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // If selected, draw highlight border
            if (isSelected) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(x - 1.5.dp.toPx(), barY - 1.5.dp.toPx()),
                    size = Size(colWidth + 3.dp.toPx(), barH + 3.dp.toPx()),
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Write percentage AND count on top of the column
            val countNum = (item.totalQuestions * item.correctPct / 100f).roundToInt()
            drawContext.canvas.nativeCanvas.apply {
                val pctPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.parseColor("#1E293B")
                    this.textSize = 11.sp.toPx()
                    this.isFakeBoldText = true
                    this.textAlign = android.graphics.Paint.Align.CENTER
                    this.isAntiAlias = true
                }
                val countPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.parseColor("#64748B")
                    this.textSize = 9.sp.toPx()
                    this.textAlign = android.graphics.Paint.Align.CENTER
                    this.isAntiAlias = true
                }

                val centerX = x + (colWidth / 2f)
                drawText("${item.correctPct}%", centerX, barY - 10.dp.toPx(), pctPaint)
                drawText("$countNum ta", centerX, barY - 1.dp.toPx(), countPaint)

                // Bottom category label
                val labelPaint = android.graphics.Paint().apply {
                    this.color = if (isSelected) android.graphics.Color.parseColor("#6366F1") else android.graphics.Color.parseColor("#475569")
                    this.textSize = 9.sp.toPx()
                    this.isFakeBoldText = isSelected
                    this.textAlign = android.graphics.Paint.Align.CENTER
                    this.isAntiAlias = true
                }
                val shortName = when {
                    item.title.contains("voqea", ignoreCase = true) -> "Voqealar"
                    item.title.contains("obida", ignoreCase = true) -> "Obidalar"
                    item.title.contains("urush", ignoreCase = true) -> "Janglar"
                    item.title.contains("shaxs", ignoreCase = true) -> "Shaxslar"
                    item.title.contains("yil", ignoreCase = true) -> "Sanalar"
                    else -> "Mavzu"
                }
                drawText(shortName, centerX, height - 12.dp.toPx(), labelPaint)
            }
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
