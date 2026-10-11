package com.example.agenticarchitect.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.data.remote.*
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.EmberBackground

@Composable
fun EnvironmentSetupScreen(
    detection: EnvironmentDetectionResponse?,
    isDetecting: Boolean,
    onDetectEnvironment: () -> Unit,
    activeIdea: IdeaRecordItem?,
    isRefiningIdea: Boolean,
    onCaptureAndRefineIdea: (String, String) -> Unit,
    activePlan: SetupPlanResponse?,
    isPlanning: Boolean,
    onGeneratePlan: (String, String) -> Unit,
    setupStatus: SetupStatusResponse?,
    isExecuting: Boolean,
    onExecutePlan: (String) -> Unit,
    onCancelSetup: (String) -> Unit,
    onStopServer: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var ideaTitleInput by remember { mutableStateOf("Smart Contract Monitor") }
    var ideaPromptInput by remember {
        mutableStateOf("AI agent that monitors smart contract transactions with a clean FastAPI backend")
    }
    var selectedStack by remember { mutableStateOf("fastapi") }

    LaunchedEffect(Unit) {
        if (detection == null) {
            onDetectEnvironment()
        }
    }

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── 1. Top Header Bar ─────────────────────────────────────────────
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
                            .clickable { onBack() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Environment Setup Agent",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Idea → Blueprint → Verified Environment",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PrimaryColor
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                        .clickable { onDetectEnvironment() }
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(
                            color = PrimaryColor,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Detect Environment",
                            tint = PrimaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── 2. Scrollable Body ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section A: Host Environment Diagnostics
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. HOST LAPTOP ENVIRONMENT",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                .border(1.dp, Color(0x33FF570B), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            if (detection != null) {
                                val env = detection.environment
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "OS: ${env.os_name} ${env.os_release} (${env.architecture})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0x3310B981))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "LAN: ${env.lan_ip}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }

                                    Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        ToolPill(label = "Python", tool = env.python)
                                        ToolPill(label = "Node", tool = env.node)
                                        ToolPill(label = "NPM", tool = env.npm)
                                        ToolPill(label = "Git", tool = env.git)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = PrimaryColor,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Probing laptop tools and runtimes...",
                                        fontSize = 12.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section B: Idea Capture & Blueprint Refinement
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. IDEA CAPTURE & ARCHITECTURAL BLUEPRINT",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Idea Prompt",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceContainerHighest.copy(alpha = 0.5f))
                                        .padding(10.dp)
                                ) {
                                    BasicTextField(
                                        value = ideaPromptInput,
                                        onValueChange = { ideaPromptInput = it },
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Default
                                        ),
                                        cursorBrush = SolidColor(PrimaryColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StackChip(
                                        name = "FastAPI",
                                        id = "fastapi",
                                        selected = selectedStack == "fastapi"
                                    ) { selectedStack = "fastapi" }
                                    StackChip(
                                        name = "React",
                                        id = "react",
                                        selected = selectedStack == "react"
                                    ) { selectedStack = "react" }
                                    StackChip(
                                        name = "Python",
                                        id = "python",
                                        selected = selectedStack == "python"
                                    ) { selectedStack = "python" }
                                    StackChip(
                                        name = "Full-Stack",
                                        id = "fullstack",
                                        selected = selectedStack == "fullstack"
                                    ) { selectedStack = "fullstack" }
                                }

                                Button(
                                    onClick = {
                                        onCaptureAndRefineIdea(ideaTitleInput, ideaPromptInput)
                                    },
                                    enabled = !isRefiningIdea && ideaPromptInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isRefiningIdea) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refining Blueprint...", fontSize = 12.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refine Idea Blueprint", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (activeIdea?.blueprint != null) {
                                    val bp = activeIdea.blueprint
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceDark.copy(alpha = 0.8f))
                                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "✓ Blueprint: ${bp.suggested_architecture}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = bp.summary,
                                                fontSize = 11.5.sp,
                                                color = OnSurface
                                            )
                                            Text(
                                                text = "Key Deps: ${bp.key_dependencies.joinToString(", ")}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = OnSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section C: Setup Plan & Operations
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "3. PROPOSED SETUP PLAN & SAFETY GATES",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        onGeneratePlan(ideaPromptInput, selectedStack)
                                    },
                                    enabled = !isPlanning && ideaPromptInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isPlanning) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Generating Plan...", fontSize = 12.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistAddCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Generate Setup Plan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (activePlan != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Plan: ${activePlan.project_name} (${activePlan.operations.size} operations)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = activePlan.summary,
                                            fontSize = 11.sp,
                                            color = OnSurfaceVariant
                                        )

                                        activePlan.operations.forEach { op ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SurfaceDark.copy(alpha = 0.6f))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "• ${op.title}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = op.description,
                                                        fontSize = 10.sp,
                                                        color = OnSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = op.status.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = when (op.status) {
                                                        "completed" -> Color(0xFF10B981)
                                                        "running" -> PrimaryColor
                                                        "failed" -> Color(0xFFEF4444)
                                                        else -> OnSurfaceVariant
                                                    }
                                                )
                                            }
                                        }

                                        // Approval & Execution Trigger
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                onExecutePlan(activePlan.plan_id)
                                            },
                                            enabled = !isExecuting,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF10B981)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isExecuting) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Executing Approved Plan...", fontSize = 12.sp)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Approve & Prepare Environment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section D: Live Execution Progress & Preview Server
                if (setupStatus != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "4. ENVIRONMENT STATUS & DEV SERVER",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                letterSpacing = 1.sp
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Status: ${setupStatus.status.uppercase()} (${(setupStatus.progress_pct * 100).toInt()}%)",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (setupStatus.server_running) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0x3310B981))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PORT ${setupStatus.server_port ?: 8080} LIVE",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { setupStatus.progress_pct },
                                        color = if (setupStatus.status == "completed") Color(0xFF10B981) else PrimaryColor,
                                        trackColor = SurfaceContainerHighest,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )

                                    if (!setupStatus.current_operation.isNullOrBlank()) {
                                        Text(
                                            text = "Current: ${setupStatus.current_operation}",
                                            fontSize = 11.sp,
                                            color = OnSurfaceVariant
                                        )
                                    }

                                    // Preview URL Button
                                    if (setupStatus.preview_url != null) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(setupStatus.preview_url)).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(browserIntent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInBrowser,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Live Preview (${setupStatus.preview_url})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolPill(label: String, tool: HostToolInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = OnSurfaceVariant
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (tool.installed) Color(0x2610B981) else Color(0x26EF4444))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (tool.installed) (tool.version ?: "✓") else "✗",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (tool.installed) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}

@Composable
fun StackChip(name: String, id: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryContainer else SurfaceContainerHighest.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else OnSurfaceVariant
        )
    }
}
