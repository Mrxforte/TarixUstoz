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
                timeSpentMinutes = 160,
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
                timeSpentMinutes = 42,
                growthPct = 22,
                categoryKey = "EVENT",
                barGradients = listOf(Color(0xFF4F46E5), Color(0xFF818CF8)),
                adviceList = listOf(
                    "Qadimgi dunyo davlatlarining tashkil topishi va inqirozi bo‘yicha xronologik ketma-ketlikka e’tibor bering.",
                    "Maslahat: Miloddan avvalgi va milodiy asrlarni hisoblashdagi chalkashliklarni bartaraf etish uchun vaqt o‘qi chizmasidan foydalaning.",
                    "Oxirgi 5 kunda o‘sish sur‘ati +22% ga yetdi. Xatolarni takrorlash bo‘limini muntazam ishlab boring."
                ),
                recommendedChapter = "6-sinf Qadimgi dunyo tarixi: 4-bob. Bobil va Mesopotamiya madaniyati"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy shaxslar",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF059669),
                correctPct = 82,
                mistakePct = 10,
                unattemptedPct = 8,
                totalQuestions = 135,
                timeSpentMinutes = 48,
                growthPct = 34,
                categoryKey = "PERSON",
                barGradients = listOf(Color(0xFF059669), Color(0xFF34D399)),
                adviceList = listOf(
                    "Ajoyib natija! Tarixiy shaxslar bo‘yicha to‘g‘rilik ko‘rsatkichi 82% dan oshdi.",
                    "Kichik maslahat: Beruniy, Ibn Sino va Ulug‘bek asarlarining arabcha nomlarini yodda saqlashga e’tibor qarating.",
                    "O‘sish davom etmoqda. Temuriylar davri allomalari bo‘yicha bilimlaringiz juda mustahkam."
                ),
                recommendedChapter = "7-sinf Tarix: 18-bob. Mirzo Ulug‘bek va uning ilmiy akademiyasi"
            ),
            CategoryAnalysisItem(
                title = "Urushlar va janglar",
                icon = Icons.Default.Security,
                iconColor = Color(0xFFDC2626),
                correctPct = 58,
                mistakePct = 24,
                unattemptedPct = 18,
                totalQuestions = 110,
                timeSpentMinutes = 38,
                growthPct = 25,
                categoryKey = "WAR",
                barGradients = listOf(Color(0xFFDC2626), Color(0xFFFB7185)),
                adviceList = listOf(
                    "Jaloliddin Manguberdi va Chingizxon o‘rtasidagi Sind daryosi bo‘yidagi jang hamda Amir Temur yurishlarida sanalar va sarkardalar nomlarini farqlashda diqqatli bo‘ling.",
                    "Taktik maslahat: Jang sanalarini xaritadagi joylashuvi bilan bog‘lab yodlasangiz, xatoliklar darhol 40% ga kamayadi.",
                    "O‘sish sur‘ati yuqori (+25%). DTM bloklarida harbiy sarkardalar bo‘yicha kamida 3 ta savol tushadi."
                ),
                recommendedChapter = "7-sinf Tarix: 14-bob. Amir Temurning To‘xtamishga qarshi yurishlari"
            ),
            CategoryAnalysisItem(
                title = "Yillar va sanalar",
                icon = Icons.Default.DateRange,
                iconColor = Color(0xFFD97706),
                correctPct = 48,
                mistakePct = 32,
                unattemptedPct = 20,
                totalQuestions = 85,
                timeSpentMinutes = 30,
                growthPct = 15,
                categoryKey = "YEAR",
                barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                adviceList = listOf(
                    "Sanalar va yillar bo‘limida xatoliklar 32% ni tashkil qilmoqda. Bu bo‘lim eng ko‘p diqqat talab etadi.",
                    "Pedagogik tavsiya: Asrlar va o‘n yilliklarni guruhlab yodlang: masalan, VIII asr - Somoniylar va arablar istilosi.",
                    "Kunlik 10 tadan sana testini yechish orqali 1 haftada xatoliklarni 10% ga tushirish mumkin."
                ),
                recommendedChapter = "8-sinf Tarix: Xronologik jadvallar va muhim sanalar"
            ),
            CategoryAnalysisItem(
                title = "Shahar va hududlar",
                icon = Icons.Default.LocationOn,
                iconColor = Color(0xFFF97316),
                correctPct = 72,
                mistakePct = 16,
                unattemptedPct = 12,
                totalQuestions = 90,
                timeSpentMinutes = 28,
                growthPct = 20,
                categoryKey = "CITY",
                barGradients = listOf(Color(0xFFF97316), Color(0xFFFDBA74)),
                adviceList = listOf(
                    "Qadimgi shaharlar va xaritalar bo‘yicha bilimingiz yuqori darajada.",
                    "Buxoro, Samarqand va Xiva me'morchiligini taqqoslab takrorlang."
                ),
                recommendedChapter = "7-sinf Tarix: Qadimgi shaharlar va karvon yo'llari"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy faktlar",
                icon = Icons.Default.FactCheck,
                iconColor = Color(0xFF8B5CF6),
                correctPct = 68,
                mistakePct = 18,
                unattemptedPct = 14,
                totalQuestions = 100,
                timeSpentMinutes = 32,
                growthPct = 19,
                categoryKey = "FACT",
                barGradients = listOf(Color(0xFF8B5CF6), Color(0xFFC084FC)),
                adviceList = listOf(
                    "Manbalar va yozma yodgorliklar bo'yicha faktlar bilan tanishib chiqing.",
                    "O'rta asrlar tarixiga oid faktik ma'lumotlar qayta ko'rib chiqilsin."
                ),
                recommendedChapter = "8-sinf Tarix: Yozma manbalar va arxeologik faktlar"
            ),
            CategoryAnalysisItem(
                title = "Tarixiy obidalar",
                icon = Icons.Default.AccountBalance,
                iconColor = Color(0xFFD97706),
                correctPct = 62,
                mistakePct = 22,
                unattemptedPct = 16,
                totalQuestions = 80,
                timeSpentMinutes = 26,
                growthPct = 17,
                categoryKey = "MONUMENT",
                barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                adviceList = listOf(
                    "Me'moriy obidalar qurilgan yillar va hukmdorlar nomlarini takrorlang."
                ),
                recommendedChapter = "8-sinf Tarix: O'zbekiston me'moriy obidalari"
            ),
            CategoryAnalysisItem(
                title = "Mantiqiy va DTM",
                icon = Icons.Default.Psychology,
                iconColor = Color(0xFF0284C7),
                correctPct = 78,
                mistakePct = 12,
                unattemptedPct = 10,
                totalQuestions = 150,
                timeSpentMinutes = 55,
                growthPct = 28,
                categoryKey = "LOGICAL",
                barGradients = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                adviceList = listOf(
                    "DTM darajasidagi murakkab savollarga tayyorgarlik darajangiz a'lo.",
                    "Aralash va mantiqiy savollarga ko'proq vaqt ajrating."
                ),
                recommendedChapter = "11-sinf Tarix: DTM va Blok Test topshiriqlari"
            ),
            CategoryAnalysisItem(
                title = "6-sinf Tarix (Qadimgi dunyo)",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF16A34A),
                correctPct = 85,
                mistakePct = 10,
                unattemptedPct = 5,
                totalQuestions = 150,
                timeSpentMinutes = 50,
                growthPct = 30,
                categoryKey = "uz_6",
                barGradients = listOf(Color(0xFF16A34A), Color(0xFF4ADE80)),
                adviceList = listOf("Qadimgi Misr, Mesopotamiya va Gretsiya bo'yicha bilimlaringiz juda yaxshi."),
                recommendedChapter = "6-sinf Tarix: Qadimgi dunyo sivilizatsiyalari"
            ),
            CategoryAnalysisItem(
                title = "7-sinf O‘zbekiston tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF6366F1),
                correctPct = 76,
                mistakePct = 14,
                unattemptedPct = 10,
                totalQuestions = 110,
                timeSpentMinutes = 36,
                growthPct = 21,
                categoryKey = "uz_7",
                barGradients = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
                adviceList = listOf("Ilk o'rta asrlar va Somoniylar davrini takrorlang."),
                recommendedChapter = "7-sinf O'zbekiston tarixi: Somoniylar va Qoraxoniylar"
            ),
            CategoryAnalysisItem(
                title = "7-sinf Jahon tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF0284C7),
                correctPct = 70,
                mistakePct = 18,
                unattemptedPct = 12,
                totalQuestions = 95,
                timeSpentMinutes = 30,
                growthPct = 19,
                categoryKey = "jahon_7",
                barGradients = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                adviceList = listOf("V-XV asrlar Yevropa va Osiyo tarixiga e'tibor bering."),
                recommendedChapter = "7-sinf Jahon tarixi: O'rta asrlar Yevropasi"
            ),
            CategoryAnalysisItem(
                title = "8-sinf O‘zbekiston tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFFD97706),
                correctPct = 74,
                mistakePct = 16,
                unattemptedPct = 10,
                totalQuestions = 105,
                timeSpentMinutes = 35,
                growthPct = 23,
                categoryKey = "uz_8",
                barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                adviceList = listOf("XVI-XIX asr I yarmi Xonliklar davri tahlili."),
                recommendedChapter = "8-sinf O'zbekiston tarixi: Buxoro, Xiva va Qo'qon xonliklari"
            ),
            CategoryAnalysisItem(
                title = "8-sinf Jahon tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF7C3AED),
                correctPct = 72,
                mistakePct = 18,
                unattemptedPct = 10,
                totalQuestions = 90,
                timeSpentMinutes = 29,
                growthPct = 20,
                categoryKey = "jahon_8",
                barGradients = listOf(Color(0xFF7C3AED), Color(0xFFC084FC)),
                adviceList = listOf("XVI-XIX asrlardagi Buyuk geografik kashfiyotlar."),
                recommendedChapter = "8-sinf Jahon tarixi: Yangi davr tarixi"
            ),
            CategoryAnalysisItem(
                title = "9-sinf O‘zbekiston tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFFDC2626),
                correctPct = 68,
                mistakePct = 20,
                unattemptedPct = 12,
                totalQuestions = 100,
                timeSpentMinutes = 33,
                growthPct = 18,
                categoryKey = "uz_9",
                barGradients = listOf(Color(0xFFDC2626), Color(0xFFFCA5A5)),
                adviceList = listOf("XIX asr II yarmi - Rossiya imperiyasi istilosi va Jadidchilik."),
                recommendedChapter = "9-sinf O'zbekiston tarixi: Jadidchilik harakati"
            ),
            CategoryAnalysisItem(
                title = "9-sinf Jahon tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF2563EB),
                correctPct = 66,
                mistakePct = 22,
                unattemptedPct = 12,
                totalQuestions = 85,
                timeSpentMinutes = 28,
                growthPct = 16,
                categoryKey = "jahon_9",
                barGradients = listOf(Color(0xFF2563EB), Color(0xFF93C5FD)),
                adviceList = listOf("XX asr boshlaridagi xalqaro munosabatlar va Birinchi jahon urushi."),
                recommendedChapter = "9-sinf Jahon tarixi: Birinchi jahon urushi"
            ),
            CategoryAnalysisItem(
                title = "10-sinf O‘zbekiston tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF059669),
                correctPct = 80,
                mistakePct = 12,
                unattemptedPct = 8,
                totalQuestions = 115,
                timeSpentMinutes = 38,
                growthPct = 25,
                categoryKey = "uz_10",
                barGradients = listOf(Color(0xFF059669), Color(0xFF6EE7B7)),
                adviceList = listOf("1917-1991 yillar sho'rolar davri tarixi."),
                recommendedChapter = "10-sinf O'zbekiston tarixi: Ikkinchi jahon urushi yillari"
            ),
            CategoryAnalysisItem(
                title = "10-sinf Jahon tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF9333EA),
                correctPct = 75,
                mistakePct = 15,
                unattemptedPct = 10,
                totalQuestions = 95,
                timeSpentMinutes = 31,
                growthPct = 22,
                categoryKey = "jahon_10",
                barGradients = listOf(Color(0xFF9333EA), Color(0xFFE9D5FF)),
                adviceList = listOf("1918-1991 yillar Ikkinchi jahon urushi va Sovuq urush davri."),
                recommendedChapter = "10-sinf Jahon tarixi: Ikkinchi jahon urushi va uning oqibatlari"
            ),
            CategoryAnalysisItem(
                title = "11-sinf O‘zbekiston tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF16A34A),
                correctPct = 88,
                mistakePct = 8,
                unattemptedPct = 4,
                totalQuestions = 125,
                timeSpentMinutes = 42,
                growthPct = 32,
                categoryKey = "uz_11",
                barGradients = listOf(Color(0xFF16A34A), Color(0xFF86EFAC)),
                adviceList = listOf("Mustaqillik davri O'zbekiston Respublikasi islohotlari."),
                recommendedChapter = "11-sinf O'zbekiston tarixi: Mustaqil O'zbekiston taraqqiyoti"
            ),
            CategoryAnalysisItem(
                title = "11-sinf Jahon tarixi",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF0284C7),
                correctPct = 82,
                mistakePct = 10,
                unattemptedPct = 8,
                totalQuestions = 100,
                timeSpentMinutes = 34,
                growthPct = 27,
                categoryKey = "jahon_11",
                barGradients = listOf(Color(0xFF0284C7), Color(0xFF7DD3FC)),
                adviceList = listOf("1991-2017 yillardagi zamonaviy xalqaro munosabatlar."),
                recommendedChapter = "11-sinf Jahon tarixi: Zamonaviy dunyo va globallashuv"
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

    // Animation progress from 0 to 1 every time category or screen opens
    var startAnim by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "analysisAnim"
    )

    LaunchedEffect(selectedIndex) {
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
                            bg = Color(0xFFECFDF5)
                        )
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "Xatolar",
                            value = "$animMistakePct%",
                            count = "$mistakeCount ta",
                            color = Color(0xFFEF4444),
                            bg = Color(0xFFFEF2F2)
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
                            bg = Color(0xFFEFF6FF)
                        )
                        MetricPillCard(
                            modifier = Modifier.weight(1f),
                            title = "Jami savol",
                            value = "$animQuestions ta",
                            count = "ishlangan test",
                            color = BrandPurple,
                            bg = Color(0xFFF5F3FF)
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
