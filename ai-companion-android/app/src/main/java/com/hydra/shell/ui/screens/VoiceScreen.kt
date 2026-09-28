package com.hydra.shell.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydra.shell.HydraViewModel
import java.util.regex.Pattern

enum class VoiceState {
    Processing,
    ReadyToSave
}

// Simple heuristic parser for demo purposes
data class ParsedIntent(
    val type: IntentType,
    val amount: String? = null,
    val note: String? = null
)

enum class IntentType { EXPENSE, GENERIC, ERROR }

fun parseRecognizedText(text: String): ParsedIntent {
    if (text.startsWith("Error", ignoreCase = true)) {
        return ParsedIntent(IntentType.ERROR)
    }
    
    val lowerText = text.lowercase()
    val isExpense = lowerText.contains("spent") || lowerText.contains("paid") || lowerText.contains("rupee") || lowerText.contains("₹") || lowerText.contains("cost") || lowerText.contains("bought")
    
    if (isExpense) {
        // Try to extract amount
        val matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(lowerText)
        val amount = if (matcher.find()) matcher.group(1) else "0.0"
        
        // Simple note extraction (everything after the amount or keywords)
        val note = lowerText.replace(Regex("(spent|paid|rupees|₹|cost|bought|\\d+(?:\\.\\d+)?)"), "").trim()
        
        return ParsedIntent(
            type = IntentType.EXPENSE,
            amount = amount,
            note = if (note.isEmpty()) "General Expense" else note
        )
    }
    
    return ParsedIntent(IntentType.GENERIC)
}

@Composable
fun VoiceScreen(viewModel: HydraViewModel) {
    val context = LocalContext.current
    var recognizedText by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(VoiceState.Processing) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    
    val startListening = {
        recognizedText = ""
        state = VoiceState.Processing
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                recognizedText = "Error recognizing speech (code: $error)"
                state = VoiceState.ReadyToSave
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
                state = VoiceState.ReadyToSave
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startListening()
            } else {
                recognizedText = "Microphone permission denied"
                state = VoiceState.ReadyToSave
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state) {
            VoiceState.Processing -> ProcessingScreen(
                text = recognizedText,
                onClose = { /* TODO */ }
            )
            VoiceState.ReadyToSave -> ReadyToSaveScreen(
                recognizedText = recognizedText,
                onClose = { /* TODO */ },
                onSave = { 
                    if (recognizedText.isNotBlank() && !recognizedText.startsWith("Error")) {
                        viewModel.sendMessage(recognizedText)
                    }
                    startListening() 
                },
                onRetry = { startListening() }
            )
        }
    }
}

@Composable
fun ProcessingScreen(text: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "OrbScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(150.dp)
                .scale(scale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Text(
            text = if (text.isEmpty()) "Listening..." else text,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 32.dp, end = 32.dp),
            fontSize = 18.sp,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
fun ReadyToSaveScreen(recognizedText: String, onClose: () -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    val parsedIntent = parseRecognizedText(recognizedText)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (parsedIntent.type == IntentType.ERROR) "Oops" else "Confirm Action",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display transcript
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "\"$recognizedText\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (parsedIntent.type == IntentType.EXPENSE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "₹${parsedIntent.amount}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Expense Log",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                    .padding(vertical = 8.dp)
            ) {
                DetailRow(icon = Icons.Default.Subject, label = "Note", value = parsedIntent.note ?: "")
            }
        } else if (parsedIntent.type == IntentType.GENERIC) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Send this message to Assistant?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (parsedIntent.type == IntentType.ERROR) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Try Again",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .padding(end = 8.dp, bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Retry",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(2f)
                        .height(56.dp)
                        .padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (parsedIntent.type == IntentType.EXPENSE) "Confirm & Log" else "Send Message",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        }
    }
}
