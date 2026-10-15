package com.tarixustoz.app.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiTutorScreen() {
    var message by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            "Salom! Men Tarix Ustoz AI. Sizga O‘zbekiston va Jahon tarixi bo‘yicha yordam beraman.",
            "Masalan: “Amir Temur haqida gapir” yoki “Ushbu mavzudan 5 ta sana testi tuz.”"
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tarix Ustoz AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Manbaga asoslangan tarixiy yordamchi (Gemini integratsiyasi uchun backend kerak)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(msg, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Savolingizni yozing...") },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (message.isNotBlank()) {
                    messages.add("Siz: $message")
                    messages.add("AI: Bu savol uchun darslik manbalari va Gemini API ulangan bo‘lganda to‘liq javob beriladi. Hozircha demo rejim.")
                    message = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Yuborish")
            }
        }
    }
}
