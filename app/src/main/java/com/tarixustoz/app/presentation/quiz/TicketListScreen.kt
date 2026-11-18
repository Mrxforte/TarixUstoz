package com.tarixustoz.app.presentation.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.theme.BrandPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    categoryKey: String,
    categoryTitle: String,
    selectedGrade: Int = 0,
    onBack: () -> Unit,
    onStartTicket: (categoryKey: String, ticketIndex: Int) -> Unit,
    onStartActiveQuiz: (String) -> Unit,
    viewModel: TicketListViewModel = hiltViewModel()
) {
    LaunchedEffect(categoryKey, selectedGrade) {
        viewModel.loadTickets(categoryKey, selectedGrade)
    }

    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

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
                        Text(
                            text = "${state.totalQuestions} ta savol • ${state.tickets.size} ta bilet",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!state.activeQuizRoute.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = BrandPurple,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable {
                            onStartActiveQuiz(state.activeQuizRoute!!)
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
                                text = "Davom etish",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = state.activeQuizTitle ?: "Testni davom ettirish",
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.loadTickets(categoryKey)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Duolingo Passing Rule Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bilet topshirish qoidasi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E40AF)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Har bir bilet 30 ta savoldan iborat. Kamida 10 ta to‘g‘ri javob topsangiz, keyingi bilet ochiladi!",
                                fontSize = 12.sp,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }
                }
            }

            // Overall Progress Summary
            item {
                val passedCount = state.tickets.count { it.isPassed }
                val totalTickets = state.tickets.size.coerceAtLeast(1)
                val progressFraction = passedCount.toFloat() / totalTickets

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                            Text(
                                text = "$passedCount / $totalTickets bilet o‘tildi",
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BrandPurple,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
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
            itemsIndexed(state.tickets) { index, ticket ->
                TicketCardItem(
                    ticket = ticket,
                    onStart = {
                        if (ticket.isUnlocked) {
                            viewModel.prefetchNextTicketsIfNeeded(categoryKey, ticket.index)
                            onStartTicket(categoryKey, ticket.index)
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
        }
    }
}

@Composable
private fun TicketCardItem(
    ticket: TicketItemUiState,
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
            // 1. Top Header Row: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${ticket.index}-Bilet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    val startQ = (ticket.index - 1) * 30 + 1
                    val endQ = ticket.index * 30
                    Text(
                        text = "$startQ–$endQ-savollar",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        ticket.isPassed -> Color(0xFFDCFCE7)
                        ticket.isUnlocked -> Color(0xFFEFF6FF)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            ticket.isPassed -> "O‘tildi 🎉"
                            ticket.isUnlocked -> "Ochiq"
                            else -> "Yopilgan 🔒"
                        },
                        color = when {
                            ticket.isPassed -> Color(0xFF15803D)
                            ticket.isUnlocked -> BrandPurple
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Davomiyligi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("20 daqiqa", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Questions Count
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
                        Text("Savollar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("30 ta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                "${ticket.bestScore}/30",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ticket.isPassed) Color(0xFF15803D) else BrandPurple
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

            // 3. Banner Mode Card (Gold/Yellow Card - "Bepul" badge removed!)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (ticket.isUnlocked) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (ticket.isUnlocked) Icons.Default.FitnessCenter else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (ticket.isUnlocked) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (ticket.isUnlocked) "Mashq rejimi ochiq" else "Kamida 10 ta to'g'ri topsangiz ochiladi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ticket.isUnlocked) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Primary Button: "Testni boshlash" or "Qayta topshirish"
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
                        imageVector = if (ticket.isPassed) Icons.Default.Refresh else Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (ticket.isPassed) "Qayta topshirish" else if (ticket.isUnlocked) "Testni boshlash" else "Qulflangan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
