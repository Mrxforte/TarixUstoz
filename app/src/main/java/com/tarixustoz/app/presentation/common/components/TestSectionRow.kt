package com.tarixustoz.app.presentation.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarixustoz.app.presentation.tests.TestCardItem
import com.tarixustoz.app.presentation.theme.BrandPurple

@Composable
fun TestSectionRow(
    sectionTitle: String,
    items: List<TestCardItem>,
    onSeeAllClick: () -> Unit,
    onCardClick: (itemId: String, categoryType: String, itemTitle: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Header Row: Title + "Hammasini ko'rish >"
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

            Row(
                modifier = Modifier
                    .clickable { onSeeAllClick() }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hammasini ko'rish",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandPurple,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Hammasini ko'rish",
                    tint = BrandPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Row of Cards (Image #2 style)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { item ->
                HistoryTestCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    totalTickets = item.totalTickets,
                    passedTickets = item.passedTickets,
                    icon = item.icon,
                    gradientColors = item.gradientColors,
                    accentColor = item.accentColor,
                    backgroundImageRes = item.backgroundImageRes,
                    onCardClick = { onCardClick(item.id, item.categoryType, item.title) },
                    modifier = Modifier
                        .width(250.dp)
                        .height(200.dp)
                )
            }
        }
    }
}
