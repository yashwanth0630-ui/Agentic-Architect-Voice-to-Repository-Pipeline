package com.example.agenticarchitect.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.agenticarchitect.data.remote.GitHubDeviceCodeResponse
import com.example.agenticarchitect.data.remote.GitHubRepositoryItem
import com.example.agenticarchitect.data.remote.GitHubStatusResponse
import com.example.agenticarchitect.data.remote.SelectedRepository
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.EmberBackground

@Composable
fun GitHubReposScreen(
    statusResponse: GitHubStatusResponse?,
    repositories: List<GitHubRepositoryItem>,
    isLoading: Boolean,
    errorMessage: String?,
    selectedRepo: SelectedRepository?,
    deviceCodeInfo: GitHubDeviceCodeResponse?,
    isDevicePolling: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRepo: (GitHubRepositoryItem) -> Unit,
    onClearSelectedRepo: () -> Unit,
    onRequestDeviceCode: () -> Unit,
    onCancelDeviceCode: () -> Unit,
    onLogout: () -> Unit,
    onOpenStudio: () -> Unit = {}
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }

    val isAuthenticated = statusResponse?.authenticated == true

    val filteredRepos = remember(repositories, searchQuery) {
        if (searchQuery.isBlank()) {
            repositories
        } else {
            repositories.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.fullName.contains(searchQuery, ignoreCase = true) ||
                (it.description?.contains(searchQuery, ignoreCase = true) == true)
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
            // ── Top Header Bar ───────────────────────────────────────────────
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
                            text = "GitHub Repositories",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = if (isAuthenticated) {
                                val user = statusResponse?.user
                                "Connected: @${user?.login ?: "user"}"
                            } else {
                                "Unauthenticated"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isAuthenticated) PrimaryColor else OnSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow.copy(alpha = 0.8f))
                            .clickable { onRefresh() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PrimaryColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // ── Main Content Area ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                // 1. Current Selected Target Repository Card (if any selected)
                if (selectedRepo != null) {
                    item {
                        SelectedTargetRepoBanner(
                            selectedRepo = selectedRepo,
                            onClear = onClearSelectedRepo,
                            onOpenUrl = { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            onOpenStudio = onOpenStudio
                        )

                    }
                }

                // 2. Authentication / Connection Status Banner
                item {
                    GitHubConnectionStatusCard(
                        statusResponse = statusResponse,
                        deviceCodeInfo = deviceCodeInfo,
                        isDevicePolling = isDevicePolling,
                        onRequestDeviceCode = onRequestDeviceCode,
                        onCancelDeviceCode = onCancelDeviceCode,
                        onLogout = onLogout,
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
                        },
                        onOpenVerificationUri = { uri ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open browser for $uri", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 3. Repository Search Bar (Only shown when authenticated)
                if (isAuthenticated) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.9f))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.5.sp
                                    ),
                                    cursorBrush = SolidColor(PrimaryColor),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Filter repositories by name...",
                                                color = OnSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 13.5.sp
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACCESSIBLE REPOSITORIES (${filteredRepos.size})",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryColor
                                )
                            }
                        }
                    }
                }

                // 4. Loading, Error, or Empty States
                if (isLoading && repositories.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = PrimaryColor,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Loading repositories from laptop bridge...",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!errorMessage.isNullOrBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLowest.copy(alpha = 0.9f))
                                .border(1.dp, Color(0x33FF3B30), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Repository Service Notice",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252)
                                    )
                                }
                                Text(
                                    text = errorMessage,
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryColor.copy(alpha = 0.15f))
                                        .clickable { onRefresh() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Retry",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryColor
                                    )
                                }
                            }
                        }
                    }
                } else if (isAuthenticated && filteredRepos.isEmpty() && !isLoading) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching repositories" else "No repositories found",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Try a different search term" else "Repositories for this account will appear here.",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 5. Repository Items
                if (isAuthenticated) {
                    items(filteredRepos, key = { it.fullName }) { repo ->
                        val isSelected = selectedRepo?.fullName == repo.fullName
                        RepositoryCard(
                            repo = repo,
                            isSelected = isSelected,
                            onSelect = {
                                onSelectRepo(repo)
                                Toast.makeText(context, "Selected: ${repo.fullName}", Toast.LENGTH_SHORT).show()
                            },
                            onOpenUrl = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prominent banner showing the currently selected repository target.
 */
@Composable
fun SelectedTargetRepoBanner(
    selectedRepo: SelectedRepository,
    onClear: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenStudio: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.95f))
            .border(1.5.dp, PrimaryColor, RoundedCornerShape(18.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor)
                    )
                    Text(
                        text = "TARGET WORKSPACE REPOSITORY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceContainerHighest)
                        .clickable { onClear() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Change",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OnSurfaceVariant
                    )
                }
            }

            Text(
                text = selectedRepo.fullName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Privacy badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (selectedRepo.isPrivate) "Private" else "Public",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedRepo.isPrivate) Color(0xFFFFB74D) else Color(0xFF81C784)
                        )
                    }

                    // Default branch badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Branch: ${selectedRepo.defaultBranch}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Open on GitHub ↗",
                    fontSize = 11.sp,
                    color = PrimaryColor,
                    modifier = Modifier.clickable { onOpenUrl(selectedRepo.htmlUrl) }
                )
            }

            // Action button to enter AI Studio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryColor)
                    .clickable { onOpenStudio() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Open in AI Studio (Edit & Preview)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Authentication card showing user profile or Device Flow authorization options.
 */
@Composable
fun GitHubConnectionStatusCard(
    statusResponse: GitHubStatusResponse?,
    deviceCodeInfo: GitHubDeviceCodeResponse?,
    isDevicePolling: Boolean,
    onRequestDeviceCode: () -> Unit,
    onCancelDeviceCode: () -> Unit,
    onLogout: () -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenVerificationUri: (String) -> Unit
) {
    val isAuthenticated = statusResponse?.authenticated == true
    val user = statusResponse?.user

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Status Indicator
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isAuthenticated) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHighest)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isAuthenticated) PrimaryColor else OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isAuthenticated) "GitHub Connected" else "GitHub Authorization",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isAuthenticated) "Active session verified" else "Not authenticated",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isAuthenticated) Color(0xFF81C784) else Color(0xFFFFB74D)
                        )
                    }
                }

                if (isAuthenticated) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHighest)
                            .clickable { onLogout() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Disconnect",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Authenticated: show user profile details
            if (isAuthenticated && user != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerLowest.copy(alpha = 0.8f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor)
                    ) {
                        Text(
                            text = user.login.take(1).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.name ?: "@${user.login}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "@${user.login} • ${user.publicRepos} public repos",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Unauthenticated: Device Flow Instructions & Actions
            if (!isAuthenticated) {
                Text(
                    text = "Connect securely using GitHub Device Authorization or by configuring GITHUB_TOKEN on your laptop bridge.",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 16.sp
                )

                // If device code has been requested
                if (deviceCodeInfo != null && deviceCodeInfo.userCode != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLowest.copy(alpha = 0.9f))
                            .border(1.dp, PrimaryColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. Enter this verification code on GitHub:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        // Big Monospace Code Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = deviceCodeInfo.userCode,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                letterSpacing = 2.sp
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryColor.copy(alpha = 0.2f))
                                    .clickable { onCopyCode(deviceCodeInfo.userCode) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Copy Code",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryColor
                                )
                            }
                        }

                        // Open Browser Action Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryColor)
                                .clickable {
                                    onOpenVerificationUri(deviceCodeInfo.verificationUri ?: "https://github.com/login/device")
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Open github.com/login/device",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Polling state indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isDevicePolling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = PrimaryColor
                                    )
                                }
                                Text(
                                    text = if (isDevicePolling) "Waiting for authorization..." else "Code active",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariant
                                )
                            }

                            Text(
                                text = "Cancel",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.clickable { onCancelDeviceCode() }
                            )
                        }
                    }
                } else {
                    // Action Buttons: Device Code or Token Explanation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(PrimaryContainer, OrangeMicGradientEnd)
                                    )
                                )
                                .clickable { onRequestDeviceCode() }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Device Authorization",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Repository Card item in the accessible repository list.
 */
@Composable
fun RepositoryCard(
    repo: GitHubRepositoryItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow.copy(alpha = 0.9f))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) PrimaryColor else Color(0x1AFFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top Row: Name and Selection Badge
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
                    Icon(
                        imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (repo.isPrivate) Color(0xFFFFB74D) else Color(0xFF81C784),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = repo.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryColor.copy(alpha = 0.2f))
                            .border(1.dp, PrimaryColor, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Target ✓",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor
                        )
                    }
                }
            }

            // Full Name (owner/repo)
            Text(
                text = repo.fullName,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                color = OnSurfaceVariant
            )

            // Description (if available)
            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    fontSize = 12.sp,
                    color = OnSurface,
                    lineHeight = 16.sp,
                    maxLines = 2
                )
            }

            // Footer Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Branch
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = repo.defaultBranch,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurfaceVariant
                        )
                    }

                    // Language
                    if (!repo.language.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = repo.language,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SecondaryColor
                            )
                        }
                    }

                    // Stars
                    if (repo.stargazersCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = repo.stargazersCount.toString(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "View ↗",
                    fontSize = 11.sp,
                    color = PrimaryColor,
                    modifier = Modifier.clickable { onOpenUrl() }
                )
            }
        }
    }
}
