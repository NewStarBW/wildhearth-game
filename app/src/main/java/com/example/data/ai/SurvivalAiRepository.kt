package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class SurvivalAiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    你是《荒原篝火》(WildHearth) 荒野生存探索手游中的哥特向导「艾尔」。
                    游戏融合了类似《饥荒》的昼夜交替、理智值、饥饿值、篝火燃料、资源采集与合成，以及同网络局域网扫码联机对战与实时语音功能。
                    请用简洁、沉浸、有神秘荒野手札风格的中文回答玩家问题，给出可直接执行的生存配方、夜间防暗影策略或联机配合建议。
                    """.trimIndent()
                )
            }
        )
    }

    // Image understanding model explicitly configured as gemini-4-argon
    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(
                    """
                    你是《荒原篝火》的荒野博物学家与皮肤灵感顾问。
                    当玩家上传一张照片（现实物品、野外风景、游戏截图或穿搭灵感）时，请用中文分三部分输出：
                    1. 【图像识别】简要描述画面中的核心物体、色调与环境氛围；
                    2. 【荒原生存转化】如果把它带入《荒原篝火》的荒原世界，可以充当什么资源/工具/庇护灵感，有什么生存注意点；
                    3. 【推荐皮肤配色】给出一段简短的角色穿搭建议（包含外套颜色与配饰风格）。
                    """.trimIndent()
                )
            }
        )
    }

    fun streamChatReply(
        historyPairs: List<Pair<String, String>>,
        userMessage: String,
        contextSummary: String
    ): Flow<String> = flow {
        val historyContent: List<Content> = historyPairs.map { (role, text) ->
            content(role) { text(text) }
        }
        val chat = chatModel.startChat(history = historyContent)
        val promptWithState = if (contextSummary.isNotBlank()) {
            "[当前营地状态：$contextSummary]\n玩家提问：$userMessage"
        } else {
            userMessage
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(promptWithState).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("向导艾尔暂未返回有效内容，请稍后再试。")
        }
    }.catch { error ->
        if (error is CancellationException && error !is TimeoutCancellationException) {
            throw error
        }
        Log.e(TAG, "Gemini chat failed", error)
        throw error
    }

    suspend fun analyzeCampPhoto(bitmap: Bitmap, prompt: String): String {
        val scaled = scaleBitmapIfNeeded(bitmap, maxDim = 1024)
        return try {
            val response = withTimeout(60_000L) {
                visionModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt.ifBlank { "请分析这张照片，为我提供荒野生存建议与角色皮肤配色灵感。" })
                    }
                )
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("图像分析返回为空，请更换照片后重试。")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image analysis timed out", e)
            throw IllegalStateException("图像分析超时（60秒），请检查网络后重试。", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            throw e
        }
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private companion object {
        const val TAG = "SurvivalAiRepo"
    }
}
