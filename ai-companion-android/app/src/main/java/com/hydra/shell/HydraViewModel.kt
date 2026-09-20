package com.hydra.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

class HydraViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val baseUrl = "http://hydra-station.duckdns.org:8010"
    private var authToken: String? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            authenticate()
        }
    }

    private suspend fun authenticate() {
        try {
            val requestBody = okhttp3.FormBody.Builder()
                .add("username", "JD")
                .add("password", "changeme123")
                .build()

            val request = Request.Builder()
                .url("$baseUrl/auth/login")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                authToken = jsonResponse.optString("access_token")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text = text, isUser = true)
        val loadingMsg = ChatMessage(text = "...", isUser = false, isLoading = true)
        
        _messages.value = _messages.value + listOf(userMsg, loadingMsg)

        viewModelScope.launch(Dispatchers.IO) {
            if (authToken == null) {
                authenticate() // try again if not authenticated
            }
            
            if (authToken == null) {
                replaceMessage(loadingMsg.id, "Error: Not authenticated.")
                return@launch
            }

            try {
                val json = JSONObject().apply {
                    put("source", "text")
                    put("text", text)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url("$baseUrl/api/client/execute")
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val runId = jsonResponse.getString("run_id")
                    
                    pollForResult(runId, loadingMsg.id)
                } else {
                    replaceMessage(loadingMsg.id, "Error: Failed to contact backend (${response.code}).")
                }
            } catch (e: Exception) {
                replaceMessage(loadingMsg.id, "Error: ${e.message}")
            }
        }
    }

    private suspend fun pollForResult(runId: String, loadingMsgId: String) {
        var attempts = 0
        val maxAttempts = 30
        
        while (attempts < maxAttempts) {
            delay(1000)
            attempts++
            
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/client/run/$runId")
                    .addHeader("Authorization", "Bearer $authToken")
                    .get()
                    .build()
                    
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.optString("status", "")
                    
                    if (status == "completed") {
                        val responseText = jsonResponse.optString("message", jsonResponse.toString())
                        replaceMessage(loadingMsgId, responseText)
                        return
                    } else if (status == "failed" || status == "error") {
                        replaceMessage(loadingMsgId, "Agent execution failed.")
                        return
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors during polling
            }
        }
        
        replaceMessage(loadingMsgId, "Error: Timeout waiting for response.")
    }

    private fun replaceMessage(id: String, newText: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(text = newText, isLoading = false)
            } else {
                msg
            }
        }
    }
}
