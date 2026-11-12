package com.medivox.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.medivox.ai.data.AppUser
import com.medivox.ai.presentation.AuthUiState
import com.medivox.ai.voice.TranscriptEntry
import com.medivox.ai.voice.VoicePhase
import com.medivox.ai.voice.VoiceUiState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AuthScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var registering by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1D2B), Color(0xFF07131F), Color(0xFF081824))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF17384A)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.HealthAndSafety,
                        contentDescription = "MediVox",
                        tint = Color(0xFF79E8D5)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "MediVox AI",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Voice-first health companion",
                color = Color(0xFFA9BECC),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            if (registering) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (registering) onRegister(email, password, displayName)
                    else onSignIn(email, password)
                },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(if (registering) "Create account" else "Sign in")
            }

            TextButton(onClick = { registering = !registering }) {
                Text(
                    if (registering) "Already have an account? Sign in"
                    else "New to MediVox? Create an account"
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "MediVox provides general health information, not a diagnosis.",
                color = Color(0xFF718B9B),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun VoiceScreen(
    state: VoiceUiState,
    accountName: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSignOut: () -> Unit,
    onPermissionDenied: () -> Unit,
    onDemoUtterance: (String) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onConnect() else onPermissionDenied()
    }

    var demoText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1D2B), Color(0xFF07131F), Color(0xFF081824))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF17384A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.HealthAndSafety,
                            contentDescription = "MediVox",
                            tint = Color(0xFF79E8D5)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = "MEDIVOX",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = accountName,
                        color = Color(0xFF9DB4C4),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(shape = CircleShape, color = Color(0xFF153044)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF75DEC9),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("REALTIME", color = Color(0xFFB9D8E6), style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onSignOut) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFF9DB4C4))
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "A more natural way\nto talk about health.",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This screen is the UI foundation for the OpenAI Realtime WebRTC experience.",
                color = Color(0xFFA9BECC),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))
            VoiceOrb(state.phase, Modifier.size(214.dp))
            Spacer(Modifier.height(8.dp))
            Text(state.phase.label, color = Color(0xFFDAE8EF), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (state.phase) {
                    VoicePhase.IDLE -> "Tap below to begin."
                    VoicePhase.CONNECTING -> "Negotiating a secure WebRTC connection…"
                    VoicePhase.LISTENING -> "Speak naturally. MediVox can be interrupted."
                    VoicePhase.USER_SPEAKING -> "I’m listening to you."
                    VoicePhase.THINKING -> "Preparing a response."
                    VoicePhase.ASSISTANT_SPEAKING -> "MediVox is responding in real time."
                    VoicePhase.DISCONNECTING -> "Closing the voice session."
                    VoicePhase.ERROR -> "Review the message below and try again."
                },
                color = Color(0xFF8FA9B9),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))
            VoiceWaveform(state.phase, Modifier.fillMaxWidth().height(38.dp))

            state.errorMessage?.let {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = Color(0xFF3A222A),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = it,
                        color = Color(0xFFFFC5C5),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (state.transcript.isNotEmpty() || state.liveCaption.isNotBlank()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.transcript.takeLast(6)) { entry ->
                        TranscriptBubble(entry)
                    }
                    if (state.liveCaption.isNotBlank()) {
                        item {
                            Surface(
                                color = Color(0xFF173244),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(
                                    text = state.liveCaption,
                                    color = Color.White,
                                    modifier = Modifier.padding(11.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            OutlinedTextField(
                value = demoText,
                onValueChange = { demoText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Demo prompt") },
                singleLine = true
            )
            Spacer(Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onConnect()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF78E5D1),
                        contentColor = Color(0xFF06221F)
                    )
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF06221F)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Please wait")
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.phase == VoicePhase.ERROR) "Try again" else "Start voice conversation")
                    }
                }

                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = state.isSessionActive,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.MicOff, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("End")
                }
            }

            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    onDemoUtterance(demoText)
                    demoText = ""
                },
                enabled = demoText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22384A))
            ) {
                Text("Send demo message")
            }

            if (!state.isSessionActive && state.recentConversations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Recent conversations",
                        color = Color(0xFFB6CBD7),
                        style = MaterialTheme.typography.labelLarge
                    )
                    state.recentConversations.take(3).forEach {
                        Text(
                            text = "• ${it.title}",
                            color = Color(0xFF8FA9B9),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "General health information only. Not a diagnosis or emergency service.",
                color = Color(0xFF718B9B),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VoiceOrb(phase: VoicePhase, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = if (phase == VoicePhase.USER_SPEAKING || phase == VoicePhase.ASSISTANT_SPEAKING) pulse else 1f

    Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.30f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x6674E7D3), Color(0x2270BFE0), Color.Transparent),
                center = center,
                radius = radius * 3f
            ),
            radius = radius * 3f,
            center = center
        )
        drawCircle(
            color = Color(0x4475E6D2),
            radius = radius * 1.48f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF9DF5E5), Color(0xFF3799C4), Color(0xFF475EC2)),
                start = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y + radius)
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.20f),
            radius = radius * 0.62f,
            center = androidx.compose.ui.geometry.Offset(center.x - radius * 0.22f, center.y - radius * 0.25f)
        )
        drawCircle(
            color = Color(0xFF0B2537).copy(alpha = 0.45f),
            radius = radius * 0.32f,
            center = center
        )
    }
}

@Composable
private fun VoiceWaveform(phase: VoicePhase, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Restart),
        label = "progress"
    )
    val level = when (phase) {
        VoicePhase.USER_SPEAKING, VoicePhase.ASSISTANT_SPEAKING -> 0.95f
        VoicePhase.THINKING, VoicePhase.CONNECTING -> 0.55f
        VoicePhase.LISTENING -> 0.30f
        else -> 0.10f
    }
    Canvas(modifier = modifier) {
        val bars = 36
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
        val middle = size.height / 2f
        repeat(bars) { index ->
            val sine = (sin(progress + index * 0.36f) + 1f) / 2f
            val factor = 0.15f + sine * level
            val barHeight = (size.height * factor).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = Color(0xFF79E5D2).copy(alpha = 0.30f + factor * 0.65f),
                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), middle - barHeight / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth)
            )
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val assistant = entry.speaker == "MediVox"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = if (assistant) Color(0xFF173244) else Color(0xFF17443F),
            shape = RoundedCornerShape(13.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = entry.speaker,
                    color = Color(0xFF83E8D6),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.text,
                    color = Color(0xFFE7F2F6),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
