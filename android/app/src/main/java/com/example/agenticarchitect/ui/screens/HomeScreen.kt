package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.data.local.IdeaSyncStatus
import com.example.agenticarchitect.data.local.IdeaVaultEntity
import com.example.agenticarchitect.data.remote.ConnectionInfo
import com.example.agenticarchitect.data.remote.SelectedRepository
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.EmberBackground
import com.example.agenticarchitect.ui.components.IdeaBeaconConnectionBar
import com.example.agenticarchitect.ui.components.PresetActionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedModel: String,
    onModelClick: () -> Unit,
    onMicClick: () -> Unit,
    onPresetSelect: (String) -> Unit,
    onNavigateTab: (String) -> Unit = {},
    onTuneClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onUserAvatarClick: () -> Unit = {},
    onTelemetryClick: (String) -> Unit = {},
    connectionInfo: ConnectionInfo = ConnectionInfo(),
    onRefreshScan: () -> Unit = {},
    onManualAddressSave: (String) -> Unit = {},
    selectedRepo: SelectedRepository? = null,
    onOpenGitHubClick: () -> Unit = {},
    onNewProjectClick: () -> Unit = {},
    onOpenRepoStudio: () -> Unit = {},
    savedIdeas: List<IdeaVaultEntity> = emptyList(),
    onSelectIdea: (IdeaVaultEntity) -> Unit = {},
    onEditIdea: (localId: String, newTitle: String, newPrompt: String) -> Unit = { _, _, _ -> },
    onDeleteIdea: (String) -> Unit = {},
    onRetrySyncIdea: (IdeaVaultEntity) -> Unit = {},
    onSyncAllIdeas: () -> Unit = {},
    onSaveIdea: (String) -> Unit = {}
) {


    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "home_dot_alpha"
    )

    var ideaToDelete by remember { mutableStateOf<IdeaVaultEntity?>(null) }
    var ideaToEdit by remember { mutableStateOf<IdeaVaultEntity?>(null) }
    var editTitleText by remember { mutableStateOf("") }
    var editPromptText by remember { mutableStateOf("") }

    if (ideaToDelete != null) {
        val target = ideaToDelete!!
        AlertDialog(
            onDismissRequest = { ideaToDelete = null },
            title = {
                Text(
                    text = "Delete Idea?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.title}\"? If this idea was already synced to your laptop, the deletion will also be communicated to the laptop backend.",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteIdea(target.localId)
                        ideaToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ideaToDelete = null }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceContainerHigh
        )
    }

    if (ideaToEdit != null) {
        val target = ideaToEdit!!
        AlertDialog(
            onDismissRequest = { ideaToEdit = null },
            title = {
                Text(
                    text = "Edit Idea",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editTitleText,
                        onValueChange = { editTitleText = it },
                        label = { Text("Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryColor,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPromptText,
                        onValueChange = { editPromptText = it },
                        label = { Text("Idea Description") },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryColor,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditIdea(target.localId, editTitleText, editPromptText)
                        ideaToEdit = null
                    }
                ) {
                    Text("Save Changes", color = PrimaryColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ideaToEdit = null }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceContainerHigh
        )
    }

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Dashboard Top Header Bar (Matching Stitch Header) ─────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = dotAlpha))
                            )
                            Text(
                                text = "ONLINE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (selectedRepo != null) PrimaryColor.copy(alpha = 0.2f) else SurfaceContainerLow.copy(alpha = 0.6f))
                            .border(if (selectedRepo != null) 1.dp else 0.dp, PrimaryColor, CircleShape)
                            .clickable { onOpenGitHubClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "GitHub Repositories",
                            tint = if (selectedRepo != null) PrimaryColor else OnSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow.copy(alpha = 0.6f))
                            .clickable { onMenuClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor)
                            .clickable { onUserAvatarClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Avatar",
                            tint = OnPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // ── 2. Scrollable Dashboard Body ────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Wi-Fi LAN Connection Status Bar
                IdeaBeaconConnectionBar(
                    connectionInfo = connectionInfo,
                    onRefreshScan = onRefreshScan,
                    onManualAddressSave = onManualAddressSave
                )

                // User Profile Sub-Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(SurfaceContainerHighest, SurfaceContainer)
                                    )
                                )
                                .clickable { onUserAvatarClick() }
                        ) {
                            Text(
                                text = "F",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer)
                                    .border(1.5.dp, SurfaceDark, CircleShape)
                            )
                        }

                        Column {
                            Text(
                                text = "WELCOME BACK",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Finley Vance",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerLow.copy(alpha = 0.7f))
                                .clickable { onMenuClick() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Apps",
                                tint = OnSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Hero Header Block
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                            .clickable { onTelemetryClick("NPU") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryColor)
                            )
                            Text(
                                text = "CORE NEURAL ENGINE V4.8 • QUALCOMM GENIEX SDK",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Text(
                        text = "What are we building today?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "High-concurrency autonomous pipelines armed for multimodal generation and telemetry.",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // ── Mode Choice: New Project vs Open GitHub Repository ───────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. New Project Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.9f))
                            .border(
                                width = if (selectedRepo == null) 1.5.dp else 1.dp,
                                color = if (selectedRepo == null) PrimaryColor else Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onNewProjectClick() }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.25f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (selectedRepo == null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(PrimaryColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Active",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryColor
                                        )
                                    }
                                }
                            }
                            Column {
                                Text(
                                    text = "New Project",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Scaffold local project via voice",
                                    fontSize = 11.sp,
                                    color = OnSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // 2. Open GitHub Repository Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.9f))
                            .border(
                                width = if (selectedRepo != null) 1.5.dp else 1.dp,
                                color = if (selectedRepo != null) PrimaryColor else Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (selectedRepo != null) {
                                    onOpenRepoStudio()
                                } else {
                                    onOpenGitHubClick()
                                }
                            }

                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedRepo != null) Color(0x334CAF50) else SurfaceContainerHighest)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = if (selectedRepo != null) Color(0xFF81C784) else SecondaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selectedRepo != null) Color(0x334CAF50) else SurfaceContainerHighest)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (selectedRepo != null) "Selected ✓" else "Browse",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedRepo != null) Color(0xFF81C784) else OnSurfaceVariant
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "GitHub Repository",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (selectedRepo != null) selectedRepo.fullName else "Select cloud repository",
                                    fontSize = 11.sp,
                                    fontFamily = if (selectedRepo != null) FontFamily.Monospace else FontFamily.Default,
                                    color = if (selectedRepo != null) PrimaryColor else OnSurfaceVariant,
                                    maxLines = 1,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Rapid Directives Horizontal Scroll
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAPID DIRECTIVES",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Swipe to view",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryColor
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PresetActionCard(
                            title = "Deploy autonomous agent swarm",
                            prompt = "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.",
                            icon = Icons.Default.SmartToy,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Optimize gas for ZK-proofs",
                            prompt = "Optimize gas consumption and prover pipelines for ZK-rollups on EVM L2s.",
                            icon = Icons.Default.Bolt,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Audit smart contract security",
                            prompt = "Perform comprehensive security audit and invariant checks on Solidity smart contracts.",
                            icon = Icons.Default.Security,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Analyze liquidity pool depth",
                            prompt = "Analyze liquidity pool depth, slippage thresholds, and sandwich attack vectors across Uniswap v3 and Curve.",
                            icon = Icons.Default.ShowChart,
                            onClick = onPresetSelect
                        )
                    }
                }

                // ── 2B. Persistent Idea Vault Section ─────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "SAVED IDEAS (IDEA VAULT)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val pendingCount = savedIdeas.count { it.syncStatus != IdeaSyncStatus.SYNCED }
                            if (pendingCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(SecondaryContainer.copy(alpha = 0.3f))
                                        .border(1.dp, SecondaryColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                        .clickable { onSyncAllIdeas() }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Sync All",
                                            tint = SecondaryColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Sync $pendingCount Pending",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SecondaryColor
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${savedIdeas.size} Saved",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    if (savedIdeas.isEmpty()) {
                        // Sleek Empty State Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.8f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = PrimaryColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Your Idea Vault is empty",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Speak into the microphone or type below to capture an idea. Ideas are stored locally on your device immediately, surviving offline and app restarts.",
                                    fontSize = 11.sp,
                                    color = OnSurfaceVariant,
                                    lineHeight = 15.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Render ideas list
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedIdeas.forEach { idea ->
                                val (statusLabel, statusColor) = when (idea.syncStatus) {
                                    IdeaSyncStatus.SYNCED -> Pair("Synced", Color(0xFF22C55E))
                                    IdeaSyncStatus.SYNCING -> Pair("Syncing...", Color(0xFF38BDF8))
                                    IdeaSyncStatus.PENDING -> Pair("Offline Pending", Color(0xFFF59E0B))
                                    IdeaSyncStatus.FAILED -> Pair("Sync Failed", Color(0xFFEF4444))
                                }

                                val formattedTime = try {
                                    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                    sdf.format(Date(idea.updatedAt))
                                } catch (_: Exception) { "" }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                        .border(
                                            1.dp,
                                            if (idea.syncStatus == IdeaSyncStatus.FAILED) Color(0x66EF4444) else Color(0x1AFFFFFF),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onSelectIdea(idea) }
                                        .padding(14.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Header Row: Title & Sync Status Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = idea.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(statusColor.copy(alpha = 0.15f))
                                                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(statusColor)
                                                    )
                                                    Text(
                                                        text = statusLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                            }
                                        }

                                        // Prompt Preview
                                        Text(
                                            text = idea.rawPrompt,
                                            fontSize = 12.sp,
                                            color = OnSurfaceVariant,
                                            maxLines = 2,
                                            lineHeight = 16.sp
                                        )

                                        // Error message if Failed
                                        if (idea.syncStatus == IdeaSyncStatus.FAILED && !idea.syncErrorMessage.isNullOrBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x22EF4444))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "⚠ ${idea.syncErrorMessage}",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFFF8888),
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "Retry",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryColor,
                                                        modifier = Modifier.clickable { onRetrySyncIdea(idea) }
                                                    )
                                                }
                                            }
                                        }

                                        // Footer Row: Metadata & Actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = formattedTime,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = OnSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                if (idea.techStack != "auto") {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(SurfaceContainerHighest)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = idea.techStack,
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Load into prompt
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onSelectIdea(idea) }
                                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Load",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = PrimaryColor
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowForward,
                                                            contentDescription = "Load",
                                                            tint = PrimaryColor,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }

                                                // Edit
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            editTitleText = idea.title
                                                            editPromptText = idea.rawPrompt
                                                            ideaToEdit = idea
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Idea",
                                                        tint = OnSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Delete
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .clickable { ideaToDelete = idea },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Idea",
                                                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // Secondary Visual Telemetry Panel (2 Columns)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Latency Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainer.copy(alpha = 0.6f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .clickable { onTelemetryClick("LATENCY") }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LATENCY",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "18.4",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ms",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(SurfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.75f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(SecondaryColor, PrimaryColor)
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        // Compute Alloc Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainer.copy(alpha = 0.6f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .clickable { onTelemetryClick("COMPUTE") }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "COMPUTE ALLOC",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = TertiaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "88.2",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "TFLOPS",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(SurfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(TertiaryContainer, PrimaryContainer)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Bottom Suspended Floating Command Input & Nav Rail ──────
            Column {
                // Suspended Floating Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow.copy(alpha = 0.95f))
                        .border(1.dp, Color(0x33FF570B), RoundedCornerShape(20.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Text Field
                        BasicTextField(
                            value = promptText,
                            onValueChange = onPromptChange,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(PrimaryColor),
                            decorationBox = { innerTextField ->
                                if (promptText.isEmpty()) {
                                    Text(
                                        text = "Ask AI a question or describe your idea",
                                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerLowest.copy(alpha = 0.8f))
                                .padding(10.dp)
                        )

                        // Bottom Row: Model Pill + Mic + Send
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                                    .clickable { onModelClick() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryColor)
                                    )
                                    Text(
                                        text = selectedModel,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Elevated Glowing Mic Button
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(PrimaryContainer, OrangeMicGradientEnd)
                                            )
                                        )
                                        .clickable { onMicClick() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Input",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Send Action Button
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                                        .clickable {
                                            val textToSend = if (promptText.isNotBlank()) promptText else "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot."
                                            onSaveIdea(textToSend)
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Save Idea",
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom 3-Tab Navigation Rail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.85f))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(0.dp)),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("HOME") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BubbleChart,
                            contentDescription = "Assistant",
                            tint = PrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Assistant", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("LISTENING") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = "Agents",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Agents", fontSize = 10.sp, color = OnSurfaceVariant)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("CHAT") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Artifacts",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Artifacts", fontSize = 10.sp, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
