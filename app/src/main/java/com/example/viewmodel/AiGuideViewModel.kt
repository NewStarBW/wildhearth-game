package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SurvivalAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

sealed interface ImageAnalysisUiState {
    data object Idle : ImageAnalysisUiState
    data object Loading : ImageAnalysisUiState
    data class Success(val result: String) : ImageAnalysisUiState
    data class Error(val message: String) : ImageAnalysisUiState
}

class AiGuideViewModel(
    private val aiRepository: SurvivalAiRepository = SurvivalAiRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessageItem(
                isUser = false,
                text = "夜风渐寒，篝火尚温。我是营地向导艾尔，无论你想询问夜间防暗影技巧、配方合成，还是同网联机配合策略，尽管开口。"
            )
        )
    )
    val messages: StateFlow<List<ChatMessageItem>> = _messages.asStateFlow()

    private val _isChatBusy = MutableStateFlow(false)
    val isChatBusy: StateFlow<Boolean> = _isChatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _imageAnalysisState = MutableStateFlow<ImageAnalysisUiState>(ImageAnalysisUiState.Idle)
    val imageAnalysisState: StateFlow<ImageAnalysisUiState> = _imageAnalysisState.asStateFlow()

    fun sendMessage(userText: String, campContext: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isChatBusy.value) return

        val historyPairs = _messages.value
            .filter { !it.isStreaming }
            .takeLast(12)
            .map { (if (it.isUser) "user" else "model") to it.text }

        val userItem = ChatMessageItem(isUser = true, text = trimmed)
        val botPlaceholderId = UUID.randomUUID().toString()
        val botPlaceholder = ChatMessageItem(id = botPlaceholderId, isUser = false, text = "", isStreaming = true)

        _messages.update { it + userItem + botPlaceholder }
        _chatError.value = null
        _isChatBusy.value = true

        viewModelScope.launch {
            try {
                aiRepository.streamChatReply(
                    historyPairs = historyPairs,
                    userMessage = trimmed,
                    contextSummary = campContext
                ).collect { streamedText ->
                    _messages.update { list ->
                        list.map { item ->
                            if (item.id == botPlaceholderId) item.copy(text = streamedText) else item
                        }
                    }
                }
                _messages.update { list ->
                    list.map { item ->
                        if (item.id == botPlaceholderId) item.copy(isStreaming = false) else item
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.update { list -> list.filterNot { it.id == botPlaceholderId && it.text.isBlank() } }
                _chatError.value = e.localizedMessage ?: "向导响应失败，请检查网络连接"
            } finally {
                _isChatBusy.value = false
            }
        }
    }

    fun resetChat() {
        _chatError.value = null
        _messages.value = listOf(
            ChatMessageItem(
                isUser = false,
                text = "营地日志已重新翻开。有什么关于荒原生存或联机的问题吗？"
            )
        )
    }

    fun analyzePhoto(bitmap: Bitmap, prompt: String) {
        _selectedBitmap.value = bitmap
        _imageAnalysisState.value = ImageAnalysisUiState.Loading
        viewModelScope.launch {
            try {
                val result = aiRepository.analyzeCampPhoto(bitmap, prompt)
                _imageAnalysisState.value = ImageAnalysisUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageAnalysisState.value = ImageAnalysisUiState.Error(
                    e.localizedMessage ?: "图像分析失败，请重试"
                )
            }
        }
    }
}
