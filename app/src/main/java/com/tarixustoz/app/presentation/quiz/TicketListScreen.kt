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
    onBack: () -> Unit,
    onStartTicket: (categoryKey: String, ticketIndex: Int) -> Unit,
    onStartActiveQuiz: (String) -> Unit,
    viewModel: TicketListViewModel = hiltViewModel()
) {
    LaunchedEffect(categoryKey) {
        viewModel.loadTickets(categoryKey)
    }

    val state by viewModel.state.collectAsState()

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
                ),
                windowInsets = WindowInsets(0.dp)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            onStartTicket(categoryKey, ticket.index)
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun TicketCardItem(
    ticket: TicketItemUiState,
    onStart: () -> Unit
) {
    val cardBg = when {
        ticket.isPassed -> Color(0xFFF0FDF4)
        ticket.isUnlocked -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = when {
        ticket.isPassed -> Color(0xFF86EFAC)
        ticket.isUnlocked -> BrandPurple.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = ticket.isUnlocked, onClick = onStart)
            .testTag("ticket_card_${ticket.index}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ticket Number Badge
            Surface(
                shape = CircleShape,
                color = when {
                    ticket.isPassed -> Color(0xFF16A34A)
                    ticket.isUnlocked -> BrandPurple
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (ticket.isUnlocked) {
                        Text(
                            text = "${ticket.index}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Qulflangan",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${ticket.index}-Bilet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    if (ticket.isPassed) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "O‘tildi",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                val startQ = (ticket.index - 1) * 30 + 1
                val endQ = ticket.index * 30
                Text(
                    text = "$startQ–$endQ-savollar • 30 ta savol",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                if (ticket.isUnlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { (ticket.bestScore.toFloat() / 30f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (ticket.isPassed) Color(0xFF16A34A) else BrandPurple,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${ticket.bestScore}/30",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ticket.isPassed) Color(0xFF15803D) else BrandPurple
                        )
                    }
                } else {
                    Text(
                        text = "🔒 ${ticket.index - 1}-biletda kamida 10 ta to'g'ri toping",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Button(
                onClick = onStart,
                enabled = ticket.isUnlocked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ticket.isPassed) Color(0xFF16A34A) else BrandPurple
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (ticket.isPassed) "Qayta" else if (ticket.isUnlocked) "Boshlash" else "Qulf",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
