package com.hydra.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class VoiceActionActivity : Activity() {

    private val SPEECH_REQUEST_CODE = 100
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            
            if (!spokenText.isNullOrEmpty()) {
                sendToBackend(spokenText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun sendToBackend(text: String) {
        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = "{\"source\": \"voice\", \"text\": \"$text\"}"
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                
                // Replace with actual server IP/domain
                val request = Request.Builder()
                    .url("https://hydra-station.duckdns.org/api/client/execute")
                    .post(body)
                    // .addHeader("Authorization", "Bearer YOUR_TOKEN")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(this@VoiceActionActivity, "Sent successfully", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@VoiceActionActivity, "Failed to send", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@VoiceActionActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            } finally {
                runOnUiThread { finish() }
            }
        }
    }
}