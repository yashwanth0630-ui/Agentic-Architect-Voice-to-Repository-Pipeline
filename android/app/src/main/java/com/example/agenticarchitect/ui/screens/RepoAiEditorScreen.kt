package com.example.agenticarchitect.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.data.remote.*
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.EmberBackground

@Composable
fun RepoAiEditorScreen(
    selectedRepo: SelectedRepository,
    workspaceRecord: RepoWorkspaceRecord?,
    isWorkspaceLoading: Boolean,
    workspaceError: String?,
    isInstructing: Boolean,
    instructionResult: RepoWorkspaceInstructResponse?,
    diffResponse: RepoWorkspaceDiffResponse?,
    isDiffLoading: Boolean,
    previewResponse: RepoWorkspacePreviewResponse?,
    onBack: () -> Unit,
    onInitializeWorkspace: () -> Unit,
    onSubmitInstruction: (String) -> Unit,
    onRefreshDiff: () -> Unit,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onCancelInstruction: () -> Unit = {},
    onReviewPublish: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var instructionText by remember { mutableStateOf("") }
    var expandedFileDiff by remember { mutableStateOf<String?>(null) }

    val activePreviewUrl = previewResponse?.preview_url ?: workspaceRecord?.preview_url
    val isPreviewRunning = activePreviewUrl != null

    val isWorkspaceReady = workspaceRecord != null && workspaceError == null

    val sampleDirectives = listOf(
        "Improve the homepage layout and make it responsive on mobile.",
        "Add a modern dark-mode color palette and typography.",
        "Create an About section and improve SEO meta tags.",
        "Refactor navigation bar with a mobile hamburger menu."
    )

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(SurfaceDark.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow.copy(alpha = 0.8f))
                            .clickable { onBack() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "AI Repository Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = selectedRepo.fullName,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PrimaryColor,
                            maxLines = 1
                        )
                    }
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isInstructing -> Color(0x33F59E0B)
                                isWorkspaceLoading -> Color(0x333B82F6)
                                isWorkspaceReady -> Color(0x3310B981)
                                else -> Color(0x33EF4444)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            isInstructing -> "⚡ Editing..."
                            isWorkspaceLoading -> "⌛ Cloning..."
                            isWorkspaceReady -> "● Workspace Ready"
                            else -> "⚠ Not Initialized"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isInstructing -> Color(0xFFF59E0B)
                            isWorkspaceLoading -> Color(0xFF60A5FA)
                            isWorkspaceReady -> Color(0xFF10B981)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }
            }

            // ── Scrollable Body ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
            ) {
                // 1. Workspace Isolation Status Card
                item {
                    WorkspaceStatusCard(
                        selectedRepo = selectedRepo,
                        workspaceRecord = workspaceRecord,
                        isWorkspaceLoading = isWorkspaceLoading,
                        workspaceError = workspaceError,
                        onInitialize = onInitializeWorkspace
                    )
                }

                // 2. AI Editing Instruction Card
                item {
                    InstructionInputCard(
                        instructionText = instructionText,
                        onTextChange = { instructionText = it },
                        isInstructing = isInstructing,
                        isWorkspaceReady = isWorkspaceReady,
                        sampleDirectives = sampleDirectives,
                        onSubmit = {
                            if (instructionText.isNotBlank()) {
                                onSubmitInstruction(instructionText)
                            }
                        },
                        onCancel = onCancelInstruction
                    )
                }

                // 3. Execution Results Card (if instruction finished or failed)
                if (instructionResult != null) {
                    item {
                        ExecutionResultsCard(
                            result = instructionResult,
                            onRefreshDiff = onRefreshDiff
                        )
                    }
                }

                // 4. Live Preview Card
                item {
                    PreviewControlCard(
                        previewUrl = activePreviewUrl,
                        isRunning = isPreviewRunning,
                        onStart = onStartPreview,
                        onStop = onStopPreview,
                        onOpen = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 5. Unified Diff Review Section
                item {
                    DiffReviewSection(
                        diffResponse = diffResponse,
                        isLoading = isDiffLoading,
                        onRefresh = onRefreshDiff,
                        onCopyDiff = { diffText ->
                            clipboardManager.setText(AnnotatedString(diffText))
                            Toast.makeText(context, "Unified diff copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // 6. Controlled Git Publishing Action Banner
                if (isWorkspaceReady) {
                    item {
                        val hasChanges = (diffResponse?.changes?.total_changes ?: 0) > 0 ||
                                (instructionResult?.changes?.total_changes ?: 0) > 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (hasChanges) {
                                        Modifier.background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF2E1065), Color(0xFF1E1B4B))
                                            )
                                        )
                                    } else {
                                        Modifier.background(SurfaceContainerLow.copy(alpha = 0.9f))
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (hasChanges) Color(0xFFA855F7) else Color(0x1AFFFFFF),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Publish,
                                            contentDescription = null,
                                            tint = if (hasChanges) Color(0xFFC084FC) else OnSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Review & Publish Changes",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (hasChanges) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0x33A855F7))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "Ready to Review",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC084FC)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (hasChanges) {
                                        "Inspect exact changes, separate AI operations from user edits, verify against unstaged/staged collisions, author signed commit, and push."
                                    } else {
                                        "View publishing pipeline status, inspect staging index, or prepare changes for push."
                                    },
                                    fontSize = 11.5.sp,
                                    color = OnSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (hasChanges) PrimaryColor else SurfaceContainerHighest)
                                        .clickable { onReviewPublish() }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (hasChanges) "Open Review & Publishing Pipeline" else "Inspect Review Screen",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasChanges) Color.Black else OnSurfaceVariant
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

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
fun WorkspaceStatusCard(
    selectedRepo: SelectedRepository,
    workspaceRecord: RepoWorkspaceRecord?,
    isWorkspaceLoading: Boolean,
    workspaceError: String?,
    onInitialize: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Isolated Workspace",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (workspaceRecord != null) {
                    Text(
                        text = "Branch: ${workspaceRecord.current_branch}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SecondaryColor
                    )
                }
            }

            if (isWorkspaceLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryColor,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Cloning repository safely via laptop bridge...",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            } else if (!workspaceError.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Workspace Initialization Notice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                    }
                    Text(
                        text = workspaceError,
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryColor.copy(alpha = 0.2f))
                            .clickable { onInitialize() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Retry Workspace Clone",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor
                        )
                    }
                }
            } else if (workspaceRecord != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Path: ${workspaceRecord.workspace_path.split("\\", "/").takeLast(2).joinToString("/")}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OnSurfaceVariant
                    )
                    if (workspaceRecord.baseline_commit.isNotBlank()) {
                        Text(
                            text = "Baseline: ${workspaceRecord.baseline_commit.take(10)}",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local workspace not yet allocated on laptop.",
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryColor)
                            .clickable { onInitialize() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Open Workspace",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionInputCard(
    instructionText: String,
    onTextChange: (String) -> Unit,
    isInstructing: Boolean,
    isWorkspaceReady: Boolean,
    sampleDirectives: List<String>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33F59E0B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "AI Editing Instruction",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isInstructing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFF59E0B),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Quick Directives Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sampleDirectives.forEach { directive ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHighest.copy(alpha = 0.8f))
                            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(50))
                            .clickable(enabled = !isInstructing) { onTextChange(directive) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = directive,
                            fontSize = 10.5.sp,
                            color = OnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Multiline Input Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 85.dp, max = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerLowest.copy(alpha = 0.95f))
                    .border(
                        width = 1.dp,
                        color = if (instructionText.isNotBlank()) PrimaryColor.copy(alpha = 0.6f) else Color(0x22FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = instructionText,
                    onValueChange = onTextChange,
                    enabled = !isInstructing,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(PrimaryColor),
                    decorationBox = { innerTextField ->
                        if (instructionText.isEmpty()) {
                            Text(
                                text = "Describe what code to modify, add, or refactor in this repository...",
                                color = OnSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (instructionText.isNotEmpty() && !isInstructing) {
                    Text(
                        text = "Clear",
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        modifier = Modifier
                            .clickable { onTextChange("") }
                            .padding(4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isInstructing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33EF4444))
                                .clickable { onCancel() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (instructionText.isNotBlank() && !isInstructing && isWorkspaceReady) {
                                    PrimaryColor
                                } else {
                                    PrimaryColor.copy(alpha = 0.35f)
                                }
                            )
                            .clickable(enabled = instructionText.isNotBlank() && !isInstructing && isWorkspaceReady) {
                                onSubmit()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = if (instructionText.isNotBlank() && !isInstructing && isWorkspaceReady) Color.Black else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isInstructing) "Generating Edits..." else "Run AI Edit",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (instructionText.isNotBlank() && !isInstructing && isWorkspaceReady) Color.Black else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionResultsCard(
    result: RepoWorkspaceInstructResponse,
    onRefreshDiff: () -> Unit
) {
    val isSuccess = result.success
    val isPartial = result.stage == "PARTIAL_ERROR"
    val changes = result.changes ?: RepoChangeSummary()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(
                width = 1.dp,
                color = when {
                    isSuccess -> Color(0x3310B981)
                    isPartial -> Color(0x33F59E0B)
                    else -> Color(0x33EF4444)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        imageVector = when {
                            isSuccess -> Icons.Default.CheckCircle
                            isPartial -> Icons.Default.Warning
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = when {
                            isSuccess -> Color(0xFF10B981)
                            isPartial -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = when {
                            isSuccess -> "AI Edits Applied"
                            isPartial -> "Partial Edits (Preserved)"
                            else -> "Execution Failed"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSuccess -> Color(0xFF10B981)
                            isPartial -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }

                Text(
                    text = "${changes.total_changes} files changed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            val msg = result.message
            if (!msg.isNullOrBlank()) {
                Text(
                    text = msg,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 16.sp
                )
            }


            if (result.error != null) {
                Text(
                    text = "Notice: ${result.error}",
                    fontSize = 11.5.sp,
                    color = Color(0xFFFF8A80),
                    lineHeight = 15.sp
                )
            }

            // Breakdown Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (changes.files_created.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2210B981))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+${changes.files_created.size} Created",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                if (changes.files_modified.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x223B82F6))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "~${changes.files_modified.size} Modified",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
                if (changes.files_deleted.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22EF4444))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "-${changes.files_deleted.size} Deleted",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }

            // Changed Files List
            val allFiles = changes.files_created.map { it to "CREATED" } +
                    changes.files_modified.map { it to "MODIFIED" } +
                    changes.files_deleted.map { it to "DELETED" }

            if (allFiles.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLowest.copy(alpha = 0.8f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allFiles.forEach { (filePath, action) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = filePath,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = action,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (action) {
                                    "CREATED" -> Color(0xFF10B981)
                                    "DELETED" -> Color(0xFFEF4444)
                                    else -> Color(0xFF60A5FA)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewControlCard(
    previewUrl: String?,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(
                width = 1.dp,
                color = if (isRunning) Color(0x3310B981) else Color(0x1AFFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0x3310B981) else SurfaceContainerHighest)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (isRunning) Color(0xFF10B981) else OnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Static LAN Preview",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isRunning) Color(0x3310B981) else SurfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isRunning) "● Live" else "○ Stopped",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF10B981) else OnSurfaceVariant
                    )
                }
            }

            if (isRunning && previewUrl != null) {
                Text(
                    text = previewUrl,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = PrimaryColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryColor)
                            .clickable { onOpen(previewUrl) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🌐 Open in Browser",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHighest)
                            .clickable { onStop() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Stop",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Supports static HTML websites. (Requires index.html)",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryColor.copy(alpha = 0.2f))
                            .clickable { onStart() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Launch Preview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiffReviewSection(
    diffResponse: RepoWorkspaceDiffResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onCopyDiff: (String) -> Unit
) {
    val changes = diffResponse?.changes ?: RepoChangeSummary()
    val diffText = changes.diff

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x333B82F6))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Difference,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Git Changes Diff (Review)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (diffText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerHighest)
                                .clickable { onCopyDiff(diffText) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Copy Diff",
                                fontSize = 10.5.sp,
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerHighest)
                            .clickable { onRefresh() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Refresh",
                            fontSize = 10.5.sp,
                            color = PrimaryColor
                        )
                    }
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = PrimaryColor,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Loading Git diff from workspace...",
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant
                    )
                }
            } else if (diffText.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLowest.copy(alpha = 0.8f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No uncommitted Git changes in workspace.\nRun an AI instruction above to generate edits.",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            } else {
                // Render Diff with syntax coloring
                val lines = remember(diffText) { diffText.lines().take(400) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLowest.copy(alpha = 0.95f))
                        .padding(10.dp)
                ) {
                    lines.forEach { line ->
                        val linePrefix = line.take(2)
                        val textColor = when {
                            line.startsWith("+++") || line.startsWith("---") -> Color(0xFF9CA3AF)
                            line.startsWith("+") -> Color(0xFF81C784)
                            line.startsWith("-") -> Color(0xFFFF8A80)
                            line.startsWith("@@") -> Color(0xFF80D8FF)
                            line.startsWith("diff --git") -> Color(0xFFFFD54F)
                            else -> Color(0xFFE0E0E0)
                        }
                        val bgColor = when {
                            line.startsWith("+") && !line.startsWith("+++") -> Color(0x1A10B981)
                            line.startsWith("-") && !line.startsWith("---") -> Color(0x1AEF4444)
                            else -> Color.Transparent
                        }

                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(vertical = 1.dp)
                        )
                    }

                    if (diffText.lines().size > 400) {
                        Text(
                            text = "... (${diffText.lines().size - 400} additional lines truncated for performance. Tap Copy Diff to view full diff) ...",
                            fontSize = 10.5.sp,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
