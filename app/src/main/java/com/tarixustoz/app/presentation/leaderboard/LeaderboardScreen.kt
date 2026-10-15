package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LeaderboardScreen() {
    // Demo data – real data comes from Firestore after auth
    val entries = listOf(
        Triple("Aziza K.", 2450L, 92),
        Triple("Jasur M.", 2180L, 88),
        Triple("Dilnoza R.", 1950L, 85),
        Triple("Siz", 320L, 70),
        Triple("Bobur T.", 280L, 65)
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Reyting", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Haftalik (demo)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries) { index, (name, xp, acc) ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(name.take(1))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text("Aniqlik: $acc%", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("$xp XP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
