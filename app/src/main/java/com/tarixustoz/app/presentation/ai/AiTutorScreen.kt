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
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AiTutorScreen(viewModel: AiTutorViewModel = hiltViewModel()) {
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tarix Ustoz AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Gemini AI yordamida tarixiy savollarga javob",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { msg ->
                val alignment = if (msg.isFromUser) Alignment.End else Alignment.Start
                val color = if (msg.isFromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = color),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(msg.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Savolingizni yozing...") },
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                },
                enabled = messageText.isNotBlank() && !isLoading
            ) {
                Icon(Icons.Default.Send, contentDescription = "Yuborish")
            }
        }
    }
}
