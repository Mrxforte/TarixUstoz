package com.tarixustoz.app.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.ThemeMode
import com.tarixustoz.app.presentation.theme.ThemeViewModel

@Composable
fun ProfileScreen(
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeMode by themeViewModel.themeMode.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("O", style = MaterialTheme.typography.displaySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "O‘quvchi",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Mehmon rejimi (Firebase Auth ulash mumkin)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(32.dp))

        Text("Mavzu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) },
                label = { Text("Tizim") },
                leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) },
                label = { Text("Yorug‘") },
                leadingIcon = { Icon(Icons.Default.LightMode, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = themeMode == ThemeMode.DARK,
                onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) },
                label = { Text("Qorong‘u") },
                leadingIcon = { Icon(Icons.Default.DarkMode, null, Modifier.size(18.dp)) }
            )
        }

        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Firebase sozlamalari", style = MaterialTheme.typography.titleSmall)
                Text(
                    "google-services.json faylini app/ papkasiga qo‘ying va Firebase Auth + Firestore ni yoqing. AI uchun Gemini API ni Cloud Functions orqali ulang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
