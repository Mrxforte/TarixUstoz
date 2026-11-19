package com.tarixustoz.app.presentation.quiz

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlin.math.roundToInt

private const val TICKET_LIST_HEADER_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    categoryKey: String,
    categoryTitle: String,
    selectedGrade: Int = 0,
    onBack: () -> Unit,
    onStartTicket: (categoryKey: String, ticketIndex: Int, timerMinutes: Int, isRetry: Boolean) -> Unit,
    onStartActiveQuiz: (String) -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    viewModel: TicketListViewModel = hiltViewModel()
) {
    LaunchedEffect(categoryKey, selectedGrade) {
        viewModel.loadTickets(categoryKey, selectedGrade)
    }

    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var hasAutoScrolled by remember(categoryKey) { mutableStateOf(false) }

    var pendingTicketToStart by remember { mutableStateOf<Int?>(null) }
    var pendingIsRetry by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    var isShowingRewardedAd by remember { mutableStateOf(false) }

    fun processTicketStart(ticketIndex: Int, isRetry: Boolean, isUnlocked: Boolean, isPassed: Boolean) {
        if (!isUnlocked) return
        val isNewTicket = !isPassed
        if (isNewTicket && state.ticketAdCounter >= 5) {
            pendingTicketToStart = ticketIndex
            pendingIsRetry = isRetry
        } else {
            viewModel.prepareTicketForStart(categoryKey, ticketIndex, isRetry, selectedGrade)
            viewModel.prefetchNextTicketsIfNeeded(categoryKey, ticketIndex, selectedGrade)
            onStartTicket(categoryKey, ticketIndex, state.timerMinutes, isRetry)
        }
    }

    LaunchedEffect(state.isLoading, state.lastOpenedTicket) {
        if (!state.isLoading && !hasAutoScrolled && state.lastOpenedTicket > 1 && state.tickets.isNotEmpty()) {
            hasAutoScrolled = true
            val targetItem = (state.lastOpenedTicket - 1 + TICKET_LIST_HEADER_COUNT)
                .coerceAtMost(state.tickets.size + TICKET_LIST_HEADER_COUNT - 1)
            listState.animateScrollToItem(targetItem)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = categoryTitle.ifBlank { "Test Biletlari" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val formattedQ = "%,d".format(state.totalQuestions).replace(',', ' ')
                        val formattedT = "%,d".format(state.tickets.size).replace(',', ' ')
                        Text(
                            text = "$formattedQ ta savol • $formattedT ta bilet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ticket_list_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    Surface(
                        onClick = onNavigateToFavorites,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(38.dp)
                            .testTag("ticket_list_favorites_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Sevimlilar",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "${state.totalFavoritesCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (state.tickets.isNotEmpty()) {
                val targetTicketIdx = state.continueTicketIndex.coerceIn(1, state.tickets.size)
                val targetTicketItem = state.tickets.getOrNull(targetTicketIdx - 1)
                val isRetry = !state.isContinueInProgress && (targetTicketItem?.isPassed == true)
                val subtitleText = if (state.isContinueInProgress && state.continueAnsweredCount != null) {
                    "$targetTicketIdx-Bilet • ${state.continueAnsweredCount}/30 savol"
                } else {
                    "$targetTicketIdx-Biletdan davom etish"
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = BrandPurple,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("ticket_list_continue_fab")
                        .clickable {
                            processTicketStart(
                                ticketIndex = targetTicketIdx,
                                isRetry = isRetry,
                                isUnlocked = targetTicketItem?.isUnlocked == true,
                                isPassed = targetTicketItem?.isPassed == true
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Davom ettirish • ${categoryTitle.ifBlank { "Shu bo‘lim" }}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = subtitleText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        var ticketJumpQuery by remember(categoryKey) { mutableStateOf("") }
        var ticketJumpError by remember(categoryKey) { mutableStateOf<String?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadTickets(categoryKey, selectedGrade, forceRefresh = true)
                        isRefreshing = false
                    }
                },
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        color = BrandPurple,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // 1. Prominent & Beautiful Timer Settings Card ("Taymer va Yulduz Qoidasi")
                    item {
                        TicketTimerSettingsCard(
                            selectedMinutes = state.timerMinutes,
                            onSelectMinutes = { mins -> viewModel.setTimerMinutes(mins) }
                        )
                    }

                    // 2. Star Rating & Passing Rule Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Yulduz reytingi va Bilet qoidasi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Har 10 ta to‘g‘ri javob = 1 ⭐ (maks. 3 ⭐⭐⭐). Belgilangan vaqt ichida tugatmasangiz 0 yulduz beriladi va bilet qayta boshlanadi!",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Overall Progress Summary + Quick Jump to Last Opened Ticket + Bilet raqami bo'yicha o'tish
                    item {
                        val passedCount = state.tickets.count { it.isPassed }
                        val totalStarsEarned = state.tickets.sumOf { it.stars }
                        val totalTickets = state.tickets.size.coerceAtLeast(1)
                        val progressFraction = passedCount.toFloat() / totalTickets

                        fun jumpToTicketNumber(targetTicketNum: Int) {
                            if (state.tickets.isEmpty()) return
                            val clamped = targetTicketNum.coerceIn(1, state.tickets.size)
                            ticketJumpError = null
                            focusManager.clearFocus()
                            coroutineScope.launch {
                                val itemPos = (clamped - 1 + TICKET_LIST_HEADER_COUNT)
                                    .coerceAtMost(state.tickets.size + TICKET_LIST_HEADER_COUNT - 1)
                                listState.animateScrollToItem(itemPos)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Umumiy progress",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = com.tarixustoz.app.presentation.home.formatCompactStarsCount(totalStarsEarned),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFD97706),
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            text = "$passedCount / $totalTickets bilet",
                                            fontWeight = FontWeight.Bold,
                                            color = BrandPurple,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = BrandPurple,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                if (state.tickets.isNotEmpty()) {
                                    val targetIdx = state.lastOpenedTicket.coerceIn(1, state.tickets.size)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = BrandPurple.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, BrandPurple.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                jumpToTicketNumber(targetIdx)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.BookmarkAdded,
                                                    contentDescription = null,
                                                    tint = BrandPurple,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "Oxirgi ochilgan: $targetIdx-Bilet",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandPurple
                                                    )
                                                    Text(
                                                        text = "Ro‘yxatdan darhol topish uchun bosing",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Default.South,
                                                contentDescription = "Biletga o‘tish",
                                                tint = BrandPurple,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Bilet raqami bo'yicha o'tish (Input + O'tish tugmasi)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Text(
                                        text = "Bilet raqami bo‘yicha o‘tish",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = ticketJumpQuery,
                                            onValueChange = { raw ->
                                                val digitsOnly = raw.filter { it.isDigit() }.take(5)
                                                ticketJumpQuery = digitsOnly
                                                ticketJumpError = null
                                            },
                                            placeholder = {
                                                Text(
                                                    text = "Bilet № (1–${state.tickets.size})",
                                                    fontSize = 13.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Tag,
                                                    contentDescription = null,
                                                    tint = BrandPurple,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Go
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onGo = {
                                                    val num = ticketJumpQuery.toIntOrNull()
                                                    if (num == null || num !in 1..state.tickets.size) {
                                                        ticketJumpError = "1 dan ${state.tickets.size} gacha bilet raqamini kiriting"
                                                    } else {
                                                        jumpToTicketNumber(num)
                                                    }
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("ticket_jump_number_input")
                                        )

                                        Button(
                                            onClick = {
                                                val num = ticketJumpQuery.toIntOrNull()
                                                if (num == null || num !in 1..state.tickets.size) {
                                                    ticketJumpError = "1 dan ${state.tickets.size} gacha bilet raqamini kiriting"
                                                } else {
                                                    jumpToTicketNumber(num)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(54.dp)
                                                .testTag("ticket_jump_go_btn")
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "O‘tish",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (ticketJumpError != null) {
                                        Text(
                                            text = ticketJumpError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Test Biletlari",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Ticket Cards List
                    itemsIndexed(state.tickets, key = { _, ticket -> ticket.index }) { _, ticket ->
                        TicketCardItem(
                            ticket = ticket,
                            timerMinutes = state.timerMinutes,
                            onToggleFavorite = {
                                if (ticket.isUnlocked) {
                                    viewModel.toggleTicketFavorite(
                                        categoryKey = categoryKey,
                                        ticketIndex = ticket.index,
                                        categoryTitle = categoryTitle.ifBlank { "Test Biletlari" },
                                        grade = selectedGrade
                                    )
                                }
                            },
                            onStart = {
                                val isRetry = ticket.isPassed
                                processTicketStart(
                                    ticketIndex = ticket.index,
                                    isRetry = isRetry,
                                    isUnlocked = ticket.isUnlocked,
                                    isPassed = ticket.isPassed
                                )
                            }
                        )
                    }

                    item { Spacer(Modifier.height(96.dp)) }
                }
            }

            // O'ng tarafda scroll boshlanganda chiqadigan va qo'l olingach bir necha soniyadan so'ng yo'qoladigan
            // ixcham Bilet raqamli Fast-Scroller tugmasi (PullToRefreshBox tashqarisida — vertikal surishni to'smaydi)
            if (state.tickets.size > 1) {
                TicketFastScrollerOverlay(
                    listState = listState,
                    totalTickets = state.tickets.size,
                    headerItemsCount = TICKET_LIST_HEADER_COUNT,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 16.dp, bottom = 24.dp, end = 4.dp)
                )
            }
        }
    }

    if (pendingTicketToStart != null) {
        AlertDialog(
            onDismissRequest = {
                pendingTicketToStart = null
            },
            title = {
                Text(text = "Reklama tomosha qiling 🎁", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Ushbu ilova siz uchun mutlaqo bepul! Biz ilovani rivojlantirishda davom etishimiz va yangi savollar qo'shishimiz uchun reklamalar juda muhim.\n\nKeyingi testni ochish uchun qisqacha videoreklama tomosha qiling va bizni qo'llab-quvvatlang. Rahmat!",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ticketIndex = pendingTicketToStart!!
                        val isRetry = pendingIsRetry
                        // Show Rewarded Ad logic here
                        isShowingRewardedAd = true
                        
                        val rewardedAdUnitId = if (AdMobManager.diagnostics.value.isTestMode) {
                            AdMobManager.TEST_REWARDED_AD_UNIT_ID
                        } else {
                            AdMobManager.REWARDED_AD_UNIT_ID
                        }

                        if (activity != null) {
                            val adRequest = AdRequest.Builder().build()
                            RewardedAd.load(
                                context,
                                rewardedAdUnitId,
                                adRequest,
                                object : RewardedAdLoadCallback() {
                                    override fun onAdLoaded(ad: RewardedAd) {
                                        ad.show(activity) { _ ->
                                            // On user earned reward
                                            viewModel.resetTicketAdCounter()
                                            viewModel.prepareTicketForStart(categoryKey, ticketIndex, isRetry, selectedGrade)
                                            viewModel.prefetchNextTicketsIfNeeded(categoryKey, ticketIndex, selectedGrade)
                                            onStartTicket(categoryKey, ticketIndex, state.timerMinutes, isRetry)
                                            pendingTicketToStart = null
                                            isShowingRewardedAd = false
                                        }
                                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                            override fun onAdDismissedFullScreenContent() {
                                                isShowingRewardedAd = false
                                            }
                                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                                isShowingRewardedAd = false
                                            }
                                        }
                                    }

                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        // Reklama yuklanmadi, shunchaki o'tkazib yuboramiz
                                        viewModel.resetTicketAdCounter()
                                        viewModel.prepareTicketForStart(categoryKey, ticketIndex, isRetry, selectedGrade)
                                        viewModel.prefetchNextTicketsIfNeeded(categoryKey, ticketIndex, selectedGrade)
                                        onStartTicket(categoryKey, ticketIndex, state.timerMinutes, isRetry)
                                        pendingTicketToStart = null
                                        isShowingRewardedAd = false
                                    }
                                }
                            )
                        } else {
                            pendingTicketToStart = null
                            isShowingRewardedAd = false
                        }
                    },
                    enabled = !isShowingRewardedAd
                ) {
                    Text(if (isShowingRewardedAd) "Yuklanmoqda..." else "Videoni ko'rish")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingTicketToStart = null }, enabled = !isShowingRewardedAd) {
                    Text("Bekor qilish")
                }
            }
        )
    }
}

@Composable
private fun TicketFastScrollerOverlay(
    listState: LazyListState,
    totalTickets: Int,
    headerItemsCount: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isScrollerVisible by remember { mutableStateOf(false) }
    var isDraggingThumb by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableIntStateOf(1) }

    val thumbHeightDp = 44.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    // Ro'yxatning joriy scroll pozitsiyasidan bilet raqami (1..totalTickets) va ulushini (0f..1f) aniqlash
    val derivedTicketNumber by remember(totalTickets, headerItemsCount) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || totalTickets <= 0) {
                1
            } else {
                val ticketItems = visibleItems.filter {
                    it.index in headerItemsCount until (headerItemsCount + totalTickets)
                }
                if (ticketItems.isNotEmpty()) {
                    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val closestTicketItem = ticketItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(itemCenter - viewportCenter)
                    } ?: ticketItems.first()
                    (closestTicketItem.index - headerItemsCount + 1).coerceIn(1, totalTickets)
                } else {
                    val firstIdx = listState.firstVisibleItemIndex
                    if (firstIdx < headerItemsCount) 1 else totalTickets
                }
            }
        }
    }

    val currentTicketNumber = if (isDraggingThumb) {
        (1 + (dragFraction * (totalTickets - 1)).roundToInt()).coerceIn(1, totalTickets)
    } else {
        derivedTicketNumber
    }

    val currentFraction = if (isDraggingThumb) {
        dragFraction.coerceIn(0f, 1f)
    } else {
        if (totalTickets > 1) {
            ((currentTicketNumber - 1).toFloat() / (totalTickets - 1).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // Scroll boshlanganda yoki scroller tugmasi ushlanganda darhol ko'rsatish,
    // qo'l olingandan (scroll to'xtagandan) 1.8 soniya keyin silliq yashirish
    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(isScrolling, isDraggingThumb, currentTicketNumber) {
        if (isScrolling || isDraggingThumb) {
            isScrollerVisible = true
        } else if (isScrollerVisible) {
            delay(1800L)
            if (!listState.isScrollInProgress && !isDraggingThumb) {
                isScrollerVisible = false
            }
        }
    }

    Box(
        modifier = modifier
            .width(156.dp)
            .onSizeChanged { size ->
                trackHeightPx = size.height.coerceAtLeast(1)
            },
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = isScrollerVisible,
            enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(320)) + slideOutHorizontally(
                animationSpec = tween(320),
                targetOffsetX = { it / 2 }
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            val usableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
            val thumbOffsetY = (currentFraction * usableTrackPx).roundToInt()
            val pillShape = RoundedCornerShape(
                topStart = 22.dp,
                bottomStart = 22.dp,
                topEnd = 12.dp,
                bottomEnd = 12.dp
            )
            val elevationDp by animateDpAsState(
                targetValue = if (isDraggingThumb) 10.dp else 5.dp,
                label = "scrollerThumbElevation"
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                // 1. O'ng chetdagi ingichka yo'lakcha (Scroll Track)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(BrandPurple.copy(alpha = if (isDraggingThumb) 0.38f else 0.16f))
                )

                // 2. Vizual Scroller Button (ixcham bilet raqami va ko'rsatkichi bilan)
                Row(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = thumbOffsetY) }
                        .align(Alignment.TopEnd)
                        .testTag("ticket_fast_scroller_thumb"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Barmoq bilan scroller surilayotganda chap tomonda qo'shimcha yorqin "Bilet №" qalqib chiquvchi badge
                    AnimatedVisibility(
                        visible = isDraggingThumb,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(180))
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .shadow(6.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1B4B))
                                .border(1.dp, Color(0xFF818CF8), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "$currentTicketNumber-Bilet",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Asosiy ixcham tugmaga o'xshash Scroller Pill ("#12 / 40 ⇅")
                    Box(
                        modifier = Modifier
                            .height(thumbHeightDp)
                            .shadow(elevationDp, pillShape)
                            .clip(pillShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isDraggingThumb) {
                                        listOf(Color(0xFF4F46E5), Color(0xFF312E81))
                                    } else {
                                        listOf(BrandPurple, Color(0xFF3730A3))
                                    }
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (isDraggingThumb) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.35f),
                                shape = pillShape
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = 11.dp, end = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "#$currentTicketNumber",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp,
                                    lineHeight = 13.sp
                                )
                                Text(
                                    text = "$totalTickets ta",
                                    color = Color(0xFFC7D2FE),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.5.sp,
                                    lineHeight = 10.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Tezkor aylantirish",
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 3. Statsionar Ustki Sensor Qatlam (Scroller tugmasi USTIDA joylashgan va .offset qo'llanmagan!)
                // Shu sababli scroller tugmasining aynan ustidan bosib tepaga-pastga surganda koordinatalar siljimaydi va 100% silliq yuradi.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(88.dp)
                        .fillMaxHeight()
                        .pointerInput(totalTickets, usableTrackPx) {
                            detectTapGestures { tapOffset ->
                                val newFrac = ((tapOffset.y - thumbHeightPx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                dragFraction = newFrac
                                isScrollerVisible = true
                                val targetTicket = (1 + (newFrac * (totalTickets - 1)).roundToInt()).coerceIn(1, totalTickets)
                                val targetItemIndex = headerItemsCount + targetTicket - 1
                                coroutineScope.launch {
                                    listState.scrollToItem(targetItemIndex)
                                }
                            }
                        }
                        .pointerInput(totalTickets, usableTrackPx) {
                            detectVerticalDragGestures(
                                onDragStart = { startOffset ->
                                    isDraggingThumb = true
                                    isScrollerVisible = true
                                    val newFrac = ((startOffset.y - thumbHeightPx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                    dragFraction = newFrac
                                    val targetTicket = (1 + (newFrac * (totalTickets - 1)).roundToInt()).coerceIn(1, totalTickets)
                                    val targetItemIndex = headerItemsCount + targetTicket - 1
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetItemIndex)
                                    }
                                },
                                onDragEnd = {
                                    isDraggingThumb = false
                                },
                                onDragCancel = {
                                    isDraggingThumb = false
                                },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val newFrac = ((change.position.y - thumbHeightPx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                    dragFraction = newFrac
                                    val targetTicket = (1 + (newFrac * (totalTickets - 1)).roundToInt()).coerceIn(1, totalTickets)
                                    val targetItemIndex = headerItemsCount + targetTicket - 1
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetItemIndex)
                                    }
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun TicketTimerSettingsCard(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(
        10 to "Tezkor",
        15 to "Standart",
        20 to "Imtihon",
        30 to "Keng"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1B4B),
        border = BorderStroke(1.2.dp, Color(0xFF6366F1).copy(alpha = 0.65f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ticket_timer_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Taymer sozlamasi",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Test vaqti (Taymer)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Biletni yechish uchun belgilangan vaqt",
                            color = Color(0xFFC7D2FE),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF10B981).copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color(0xFF34D399))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$selectedMinutes daq",
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (mins, label) ->
                    val isSelected = selectedMinutes == mins
                    Surface(
                        onClick = { onSelectMinutes(mins) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF4F46E5) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("timer_option_${mins}m")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$mins daq",
                                color = if (isSelected) Color.White else Color(0xFFE0E7FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFFC7D2FE) else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCardItem(
    ticket: TicketItemUiState,
    timerMinutes: Int,
    onToggleFavorite: () -> Unit,
    onStart: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val borderColor = when {
        ticket.isPassed -> Color(0xFF86EFAC)
        ticket.isUnlocked -> BrandPurple.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = ticket.isUnlocked, onClick = onStart)
            .testTag("ticket_card_${ticket.index}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Header Row: Title, 3-Star Rating, Favorite Button & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${ticket.index}-Bilet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        // 3-Star Rating Display (10 correct = 1 star, 20 = 2 stars, 30 = 3 stars)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (s in 1..3) {
                                val earned = s <= ticket.stars
                                Icon(
                                    imageVector = if (earned) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Yulduz $s",
                                    tint = if (earned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    val startQ = (ticket.index - 1) * 30 + 1
                    val endQ = ticket.index * 30
                    Text(
                        text = "$startQ–$endQ-savollar",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Add Ticket to Global Favorites Button (faqat ochiq biletlar uchun)
                    if (ticket.isUnlocked) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("ticket_fav_btn_${ticket.index}")
                        ) {
                            Icon(
                                imageVector = if (ticket.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (ticket.isFavorite) "Sevimlilardan olish" else "Biletni sevimlilarga qo‘shish",
                                tint = if (ticket.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            ticket.isPassed -> Color(0xFF16A34A).copy(alpha = 0.14f)
                            ticket.isLastOpened -> BrandPurple.copy(alpha = 0.16f)
                            ticket.isUnlocked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when {
                                ticket.isPassed -> "O‘tildi 🎉"
                                ticket.isLastOpened -> "Oxirgi ochilgan"
                                ticket.isUnlocked -> "Ochiq"
                                else -> "Yopilgan 🔒"
                            },
                            color = when {
                                ticket.isPassed -> Color(0xFF16A34A)
                                ticket.isUnlocked -> BrandPurple
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 2. Middle Stats Grid: Duration & Questions Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Duration
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = BrandPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Belgilangan vaqt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$timerMinutes daqiqa", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Questions Count & Stars
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Savollar • Reyting", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("30 ta • ${ticket.stars}/3 ⭐", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Progress Bar (if unlocked)
            if (ticket.isUnlocked) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Natija",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (ticket.ongoingAnsweredCount != null && ticket.ongoingAnsweredCount > 0)
                                "${ticket.ongoingScore ?: 0}/30 (${ticket.ongoingAnsweredCount} ta ishlangan)"
                            else
                                "${ticket.bestScore}/30 (${ticket.stars} ⭐)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ticket.isPassed) Color(0xFF16A34A) else BrandPurple
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val progressFraction = ((ticket.ongoingAnsweredCount ?: ticket.bestScore).toFloat() / 30f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (ticket.isPassed) Color(0xFF16A34A) else BrandPurple,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // 3. Banner Mode Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (ticket.isUnlocked) Color(0xFFF59E0B).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (ticket.isPassed) Icons.Default.Shuffle else if (ticket.isUnlocked) Icons.Default.FitnessCenter else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (ticket.isUnlocked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (ticket.isPassed) "Qayta ishlashda savollar va A/B/C/D aralashtiriladi"
                        else if (ticket.isUnlocked) "Vaqtida kamida 10 ta to‘g‘ri javob = 1 ⭐ va keyingi bilet"
                        else "Avvalgi biletdan kamida 1 ⭐ olinganda ochiladi",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ticket.isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Primary Button: "Testni boshlash" or "Qayta topshirish (Shuffle)"
            Button(
                onClick = onStart,
                enabled = ticket.isUnlocked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ticket.isPassed) Color(0xFF16A34A) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ticket.isPassed) Icons.Default.Shuffle else Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (ticket.isPassed) "Qayta topshirish (Shuffle)" else if (ticket.isUnlocked) "Testni boshlash" else "Qulflangan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
