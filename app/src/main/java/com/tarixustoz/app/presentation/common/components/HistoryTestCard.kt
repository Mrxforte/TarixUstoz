package com.tarixustoz.app.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.sin

/**
 * Historical Test Card with Thematic Background Artwork and Liquid Battery Progress.
 * Shows title, subtitle (which grade book is used), and ticket counts.
 */
@Composable
fun HistoryTestCard(
    title: String,
    subtitle: String,
    totalTickets: Int,
    passedTickets: Int,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.MenuBook,
    gradientColors: List<Color> = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155)),
    accentColor: Color = Color(0xFF10B981),
    @DrawableRes backgroundImageRes: Int? = null,
    isSelected: Boolean = false
) {
    val progress = if (totalTickets > 0) (passedTickets.toFloat() / totalTickets).coerceIn(0f, 1f) else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onCardClick() },
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected) BorderStroke(2.5.dp, Color(0xFF10B981)) else null,
        shadowElevation = if (isSelected) 8.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors
                    )
                )
        ) {
            if (backgroundImageRes != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(backgroundImageRes, context) {
                        ImageRequest.Builder(context)
                            .data(backgroundImageRes)
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    gradientColors.first().copy(alpha = 0.58f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
            }

            // Liquid Progress Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val progressHeight = height * progress

                clipRect {
                    val path = Path()
                    path.moveTo(0f, height)
                    
                    if (progress > 0f) {
                        for (x in 0..width.toInt() step 5) {
                            val y = (height - progressHeight) + 8.dp.toPx() * sin((x / width) * 2 * Math.PI.toFloat() + waveOffset)
                            path.lineTo(x.toFloat(), y.toFloat())
                        }
                    } else {
                        path.lineTo(width, height)
                    }

                    path.lineTo(width, height)
                    path.close()

                    drawPath(
                        path = path,
                        color = accentColor.copy(alpha = 0.3f)
                    )
                }
            }

            // Top Battery Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$passedTickets / $totalTickets Bilet",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Content Container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle (Book used)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
