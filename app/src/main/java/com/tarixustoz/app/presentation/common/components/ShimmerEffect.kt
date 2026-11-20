package com.tarixustoz.app.presentation.common.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarixustoz.app.presentation.theme.BrandPurple

/**
 * Sweeping Shimmer brush modifier for Compose UI placeholders.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(16.dp),
    shimmerColors: List<Color>? = null
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val baseColors = shimmerColors ?: listOf(
        Color(0xFFE2E8F0),
        Color(0xFFF8FAFC),
        Color(0xFFE2E8F0)
    )

    val brush = Brush.linearGradient(
        colors = baseColors,
        start = Offset(translateAnimation - 200f, translateAnimation - 200f),
        end = Offset(translateAnimation, translateAnimation)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Shimmer skeleton card for test cards in horizontal rows.
 */
@Composable
fun ShimmerHistoryTestCard(
    modifier: Modifier = Modifier
        .width(250.dp)
        .height(200.dp)
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background shimmer animation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmerEffect(RoundedCornerShape(24.dp))
            )

            // Content skeleton overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top badge skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(24.dp)
                            .shimmerEffect(
                                RoundedCornerShape(12.dp),
                                listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.4f))
                            )
                    )
                }

                // Bottom text title & subtitle skeleton
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(20.dp)
                            .shimmerEffect(
                                RoundedCornerShape(6.dp),
                                listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.5f))
                            )
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .shimmerEffect(
                                RoundedCornerShape(6.dp),
                                listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.4f))
                            )
                    )
                }
            }
        }
    }
}

/**
 * Shimmer row component for sections loading cards.
 */
@Composable
fun ShimmerTestSectionRow(
    sectionTitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(16.dp)
                    .shimmerEffect(RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(3) {
                ShimmerHistoryTestCard()
            }
        }
    }
}

/**
 * Skeleton placeholder for Daily Question card.
 */
@Composable
fun ShimmerDailyQuestionCard(
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(20.dp)
                        .shimmerEffect(RoundedCornerShape(8.dp))
                )
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(20.dp)
                        .shimmerEffect(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(18.dp)
                    .shimmerEffect(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .shimmerEffect(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.height(16.dp))
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .shimmerEffect(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Skeleton list item for general lists (Tickets, Leaderboard, Mistakes).
 */
@Composable
fun ShimmerListItem(
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shimmerEffect(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .shimmerEffect(RoundedCornerShape(6.dp))
            )
        }
    }
}
