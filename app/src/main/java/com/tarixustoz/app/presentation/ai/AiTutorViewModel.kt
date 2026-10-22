package com.tarixustoz.app.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.tarixustoz.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean
)

@HiltViewModel
class AiTutorViewModel @Inject constructor() : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Salom! Men Tarix Ustoz AI. Sizga O'zbekiston va Jahon tarixi bo'yicha yordam beraman.\nMasalan: \"Amir Temur haqida gapir\" yoki \"Ushbu mavzudan 5 ta sana testi tuz.\"", 
                isFromUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    


    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        _messages.update { it + ChatMessage(text = userMessage, isFromUser = true) }
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val response = generativeModel.generateContent(
                    content(role = "user") { 
                        text("Sen kimsan va vazifang nima? Quyidagi savolga O'zbekiston va Jahon tarixi doirasida aniq javob ber:\n$userMessage") 
                    }
                )
                response.text?.let { reply ->
                    _messages.update { it + ChatMessage(text = reply, isFromUser = false) }
                } ?: run {
                    _messages.update { it + ChatMessage(text = "Kechirasiz, javob olib bo'lmadi.", isFromUser = false) }
                }
            } catch (e: Exception) {
                // Log full error for debugging in the UI
                _messages.update { it + ChatMessage(text = "Kechirasiz, tizimda xatolik yuz berdi (${e.javaClass.simpleName}): ${e.message}", isFromUser = false) }
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
