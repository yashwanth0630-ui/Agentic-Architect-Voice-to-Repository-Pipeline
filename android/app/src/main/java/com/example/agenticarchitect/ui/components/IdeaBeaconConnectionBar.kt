package com.example.agenticarchitect.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.data.remote.ConnectionInfo
import com.example.agenticarchitect.data.remote.ConnectionState
import com.example.agenticarchitect.theme.*

/**
 * Connection indicator bar for Idea Beacon Wi-Fi LAN Bridge.
 * Displays 3 distinct states:
 * 🟢 Connected to Idea Beacon Laptop
 * 🟡 Searching for laptop
 * 🔴 Laptop not found (with actionable guidance)
 *
 * Includes manual fallback settings dialog trigger.
 */
@Composable
fun IdeaBeaconConnectionBar(
    connectionInfo: ConnectionInfo,
    onRefreshScan: () -> Unit,
    onManualAddressSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "beacon_dot_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    val (statusColor, statusTitle, statusIcon) = when (connectionInfo.state) {
        ConnectionState.CONNECTED -> Triple(
            Color(0xFF10B981), // Emerald Green
            if (connectionInfo.isAdbFallback) "Connected via ADB Reverse" else "Connected to Idea Beacon Laptop",
            Icons.Default.Wifi
        )
        ConnectionState.SEARCHING -> Triple(
            Color(0xFFF59E0B), // Amber Yellow
            "Searching for laptop...",
            Icons.Default.WifiFind
        )
        ConnectionState.DISCONNECTED -> Triple(
            Color(0xFFEF4444), // Crimson Red
            "Laptop not found",
            Icons.Default.WifiOff
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.92f))
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Main Header Row: Status Indicator + Endpoint info + Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing Status Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (connectionInfo.state == ConnectionState.SEARCHING)
                                statusColor.copy(alpha = pulseAlpha)
                            else statusColor
                        )
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = statusTitle,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (connectionInfo.state == ConnectionState.CONNECTED) {
                            val badgeText = if (connectionInfo.isWifiLan) "Wi-Fi LAN" else "USB ADB"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(statusColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }

                    if (connectionInfo.state == ConnectionState.CONNECTED && connectionInfo.endpointUrl.isNotBlank()) {
                        Text(
                            text = connectionInfo.endpointUrl,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Quick actions: Scan & Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scan / Refresh Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                        .clickable { onRefreshScan() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Wi-Fi",
                        tint = if (connectionInfo.state == ConnectionState.SEARCHING) statusColor else OnSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Settings / Manual Address Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                        .clickable { showSettingsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure Laptop Address",
                        tint = OnSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Actionable guidance banner when disconnected
        AnimatedVisibility(visible = connectionInfo.state == ConnectionState.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerLowest.copy(alpha = 0.85f))
                    .border(1.dp, Color(0x33EF4444), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 2.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Make sure your phone and laptop are connected to the same Wi-Fi network and that the Idea Beacon Bridge is running.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = OnSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan Again",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                modifier = Modifier.clickable { onRefreshScan() }
                            )
                            Text(
                                text = "Enter IP Manually",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryColor,
                                modifier = Modifier.clickable { showSettingsDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        ManualAddressDialog(
            currentAddress = connectionInfo.endpointUrl,
            onDismiss = { showSettingsDialog = false },
            onSave = { newAddress ->
                showSettingsDialog = false
                onManualAddressSave(newAddress)
            }
        )
    }
}

/**
 * Settings Dialog allowing user to manually enter the laptop IP:port fallback
 */
@Composable
fun ManualAddressDialog(
    currentAddress: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textInput by remember {
        mutableStateOf(
            currentAddress
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            errorMessage = null
            return true // Allow clearing to enable auto-discovery
        }
        val parts = trimmed.split(":")
        val host = parts[0].trim()
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        val hostnameRegex = Regex("""^[a-zA-Z0-9.\-_]+$""")

        if (ipv4Regex.matches(host)) {
            val octets = host.split(".").map { it.toIntOrNull() ?: 999 }
            if (octets.any { it !in 0..255 }) {
                errorMessage = "Invalid IPv4 octets (0-255 each)"
                return false
            }
        } else if (!hostnameRegex.matches(host)) {
            errorMessage = "Invalid hostname or IP address"
            return false
        }

        if (parts.size > 1) {
            val port = parts[1].trim().toIntOrNull()
            if (port == null || port !in 1..65535) {
                errorMessage = "Port must be 1 - 65535"
                return false
            }
        }
        errorMessage = null
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Laptop Bridge Address",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your laptop's local Wi-Fi IP and port. Leave blank to restore automatic Wi-Fi discovery.",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        validateInput(it)
                    },
                    placeholder = {
                        Text("e.g. 192.168.1.100:8000", color = OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 13.sp)
                    },
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let {
                            Text(text = it, color = Color(0xFFEF4444), fontSize = 11.sp)
                        } ?: Text("Default port is 8000", color = OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = SurfaceContainerHighest,
                        errorBorderColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validateInput(textInput)) {
                        onSave(textInput.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer)
            ) {
                Text("Save & Connect", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceVariant, fontSize = 12.sp)
            }
        }
    )
}
