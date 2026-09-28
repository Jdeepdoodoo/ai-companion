package com.hydra.shell.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class VoiceState {
    Processing,
    ReadyToSave
}

@Composable
fun VoiceScreen() {
    var state by remember { mutableStateOf(VoiceState.Processing) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state) {
            VoiceState.Processing -> ProcessingScreen(
                onClose = { /* Navigate back or dismiss */ }
            )
            VoiceState.ReadyToSave -> ReadyToSaveScreen(
                onClose = { /* Navigate back or dismiss */ },
                onSave = { state = VoiceState.Processing } // Reset for demo purposes
            )
        }
    }
}

@Composable
fun ProcessingScreen(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Close Button
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

        // Glowing Orb Animation
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
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Loading Text
        Text(
            text = "Sorting the details...",
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            fontSize = 16.sp
        )
    }
}

@Composable
fun ReadyToSaveScreen(onClose: () -> Unit, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Ready to save",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp)) // Balance the close button width
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transcription Pill
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
                text = "\"spent 200 rupees on lunch\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Amount Card
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
                    text = "₹200.0",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Today",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Details Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(vertical = 8.dp)
        ) {
            DetailRow(icon = Icons.Default.Restaurant, label = "Category", value = "Food and Dining")
            Divider(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow(icon = Icons.Default.Payments, label = "Payment mode", value = "Cash")
            Divider(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow(icon = Icons.Default.Subject, label = "Note", value = "lunch")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Button
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "That's magic.",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
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
