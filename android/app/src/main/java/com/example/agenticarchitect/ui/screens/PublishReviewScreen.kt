package com.example.agenticarchitect.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
fun PublishReviewScreen(
    workspaceId: String,
    reviewResponse: PublishReviewResponse?,
    isLoadingReview: Boolean,
    isCommitting: Boolean,
    commitResponse: CommitResponse?,
    isPushing: Boolean,
    pushResponse: PushResponse?,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefreshReview: () -> Unit,
    onCommit: (commitMessage: String, selectedFiles: List<String>) -> Unit,
    onPush: (branch: String?, confirmed: Boolean) -> Unit
) {
    val context = LocalContext.current
    var commitMessageText by remember { mutableStateOf("") }
    val selectedFiles = remember { mutableStateListOf<String>() }
    var isDiffExpanded by remember { mutableStateOf(false) }

    // Initialize commit message and selectable files when review data arrives
    LaunchedEffect(reviewResponse) {
        if (reviewResponse != null) {
            if (commitMessageText.isBlank()) {
                val inst = reviewResponse.latest_instruction
                commitMessageText = if (!inst.isNullOrBlank()) {
                    "Idea Beacon: $inst"
                } else {
                    "Idea Beacon AI updates"
                }
            }
            // Auto-select eligible files initially
            selectedFiles.clear()
            reviewResponse.files.filter { it.eligible_for_commit }.forEach {
                selectedFiles.add(it.path)
            }
        }
    }

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top App Bar ──────────────────────────────────────────────────
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "Review & Publish",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val repoSub = reviewResponse?.let { "${it.owner}/${it.repo} (${it.current_branch})" } ?: workspaceId
                        Text(
                            text = repoSub,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = onRefreshReview, enabled = !isLoadingReview) {
                    if (isLoadingReview) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = EmberOrangePrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = EmberOrangePrimary
                        )
                    }
                }
            }

            // ── Main Content ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Error Banner
                if (errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF330B0B)),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFF882222)))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5555))
                                Text(text = errorMessage, color = Color(0xFFFFCCCC), fontSize = 13.sp)
                            }
                        }
                    }
                }

                // 2. Publish Blocked Banner
                if (reviewResponse?.publish_blocked == true) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2605)),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFF885500)))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmberOrangePrimary)
                                    Text(
                                        text = "Publishing Blocked for Safety",
                                        color = EmberOrangePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = reviewResponse.publish_block_reason
                                        ?: "Colliding uncommitted user changes detected. Attribution cannot be guaranteed without risking user work.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 3. Remote & Target Details
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderSubtle))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "TARGET REPOSITORY",
                                color = EmberOrangePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Branch", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    reviewResponse?.current_branch ?: "main",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Remote", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    reviewResponse?.remote_name ?: "origin",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Destination URL", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    reviewResponse?.remote_url ?: "Not configured",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 4. File Selection Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHANGED FILES (${reviewResponse?.files?.size ?: 0})",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        val eligibleFiles = reviewResponse?.files?.filter { it.eligible_for_commit } ?: emptyList()
                        if (eligibleFiles.isNotEmpty()) {
                            Text(
                                text = if (selectedFiles.size == eligibleFiles.size) "Deselect All" else "Select All",
                                color = EmberOrangePrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    if (selectedFiles.size == eligibleFiles.size) {
                                        selectedFiles.clear()
                                    } else {
                                        selectedFiles.clear()
                                        eligibleFiles.forEach { selectedFiles.add(it.path) }
                                    }
                                }
                            )
                        }
                    }
                }

                val files = reviewResponse?.files ?: emptyList()
                if (files.isEmpty() && !isLoadingReview) {
                    item {
                        Text("No changes detected in workspace.", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    items(files) { file ->
                        val isSelected = selectedFiles.contains(file.path)
                        val isEligible = file.eligible_for_commit

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isEligible) Color(0xFF22110E) else if (isSelected) SurfaceCard else Color(0xFF140D0B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(if (!isEligible) Color(0xFF662211) else if (isSelected) EmberOrangePrimary.copy(alpha = 0.5f) else BorderSubtle)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (isEligible) {
                                                    if (checked) selectedFiles.add(file.path) else selectedFiles.remove(file.path)
                                                }
                                            },
                                            enabled = isEligible,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = EmberOrangePrimary,
                                                uncheckedColor = TextMuted,
                                                disabledCheckedColor = TextMuted,
                                                disabledUncheckedColor = Color(0xFF332222)
                                            )
                                        )
                                        Column {
                                            Text(
                                                text = file.path,
                                                color = if (isEligible) TextPrimary else Color(0xFFAAAAAA),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "Source: ${file.source.replace("_", " ")}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Action badge
                                    val (badgeBg, badgeColor) = when (file.action.lowercase()) {
                                        "created" -> Color(0xFF0F3820) to Color(0xFF44DD88)
                                        "deleted" -> Color(0xFF3B1212) to Color(0xFFFF5555)
                                        else -> Color(0xFF2E2410) to Color(0xFFFFCC44)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(file.action.uppercase(), color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!isEligible && file.ineligibility_reason != null) {
                                    Row(
                                        modifier = Modifier.padding(start = 38.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF8844), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = file.ineligibility_reason,
                                            color = Color(0xFFFF8844),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Expandable Diff Viewer
                if (!reviewResponse?.diff.isNullOrBlank()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderSubtle)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDiffExpanded = !isDiffExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "UNIFIED DIFF PREVIEW",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Icon(
                                        imageVector = if (isDiffExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = EmberOrangePrimary
                                    )
                                }

                                AnimatedVisibility(visible = isDiffExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0A0706))
                                            .padding(10.dp)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Column {
                                            reviewResponse.diff.split("\n").forEach { line ->
                                                val lineCol = when {
                                                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF44DD88)
                                                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFFFF6666)
                                                    line.startsWith("@@") -> Color(0xFF88AAFF)
                                                    else -> TextMuted
                                                }
                                                Text(
                                                    text = line,
                                                    color = lineCol,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Step 1: Commit Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderSubtle)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "STEP 1: AUTHORIZE & COMMIT",
                                color = EmberOrangePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = "Commit Message",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            BasicTextField(
                                value = commitMessageText,
                                onValueChange = { commitMessageText = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                cursorBrush = SolidColor(EmberOrangePrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B110E))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                enabled = commitResponse == null && !isCommitting
                            )

                            val canCommit = !reviewResponse?.publish_blocked!! &&
                                    selectedFiles.isNotEmpty() &&
                                    commitMessageText.isNotBlank() &&
                                    !isCommitting &&
                                    commitResponse == null

                            Button(
                                onClick = {
                                    onCommit(commitMessageText, selectedFiles.toList())
                                },
                                enabled = canCommit,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmberOrangePrimary,
                                    disabledContainerColor = Color(0xFF332018)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCommitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Executing Git Commit...", color = TextPrimary)
                                } else if (commitResponse != null) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF44DD88), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Committed (${commitResponse.commit_hash?.take(7)})", color = Color(0xFF44DD88))
                                } else {
                                    Text("Authorize & Create Commit (${selectedFiles.size} files)", color = if (canCommit) TextPrimary else TextMuted)
                                }
                            }
                        }
                    }
                }

                // 7. Step 2: Push Section (Shown when commit succeeds or commit already created)
                if (commitResponse != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141F14)),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFF225522))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF44DD88))
                                    Text(
                                        text = "COMMIT CREATED SUCCESSFULLY",
                                        color = Color(0xFF44DD88),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Text(
                                    text = "Commit: ${commitResponse.commit_hash?.take(8)}",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "\"${commitResponse.commit_message}\"",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                HorizontalDivider(color = Color(0xFF224422))

                                Text(
                                    text = "STEP 2: CONFIRM PUSH TO REMOTE",
                                    color = EmberOrangePrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = "Destination: ${reviewResponse?.remote_name ?: "origin"} / ${commitResponse.branch ?: "main"}",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )

                                val isPushComplete = pushResponse != null

                                Button(
                                    onClick = {
                                        onPush(commitResponse.branch, true)
                                    },
                                    enabled = !isPushing && !isPushComplete,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF228833),
                                        disabledContainerColor = Color(0xFF183018)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isPushing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Fetching & Pushing to Remote...", color = TextPrimary)
                                    } else if (isPushComplete) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pushed to ${pushResponse.branch}!", color = TextPrimary)
                                    } else {
                                        Icon(Icons.Default.Upload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Confirm & Push to Remote", color = TextPrimary)
                                    }
                                }

                                if (isPushComplete) {
                                    Text(
                                        text = "Remote updated at: ${pushResponse.timestamp}",
                                        color = Color(0xFF88DD88),
                                        fontSize = 11.sp
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
