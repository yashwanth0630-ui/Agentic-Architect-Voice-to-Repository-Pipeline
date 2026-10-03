package com.example.agenticarchitect.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.ui.components.BottomChatBar
import com.example.agenticarchitect.ui.components.EmberBackground
import com.example.agenticarchitect.ui.components.PresetActionCard
import com.example.agenticarchitect.ui.components.TopHeaderBar

@Composable
fun HomeScreen(
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedModel: String,
    onModelClick: () -> Unit,
    onMicClick: () -> Unit,
    onPresetSelect: (String) -> Unit,
    onTuneClick: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Bar ──────────────────────────────────────────
            Column {
                TopHeaderBar(
                    userName = "Brakeman",
                    subtitle = "Welcome back",
                    onTuneClick = onTuneClick,
                    onMenuClick = onMenuClick
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Hero Heading ─────────────────────────────────
                Text(
                    text = "What are we\nbuilding today?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp,
                    color = Color.White,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── Preset Action Cards (Horizontal Row) ──────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PresetActionCard(
                        title = "Deploy autonomous\nagent",
                        prompt = "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onPresetSelect
                    )

                    PresetActionCard(
                        title = "Optimize gas\nfor ZK-proofs",
                        prompt = "Optimize gas consumption and prover pipelines for ZK-rollups on EVM L2s.",
                        icon = Icons.Default.Bolt,
                        onClick = onPresetSelect
                    )

                    PresetActionCard(
                        title = "Audit smart\ncontracts",
                        prompt = "Perform comprehensive security audit and invariant checks on Solidity smart contracts.",
                        icon = Icons.Default.Security,
                        onClick = onPresetSelect
                    )

                    PresetActionCard(
                        title = "FastAPI +\nNext.js Stack",
                        prompt = "FastAPI backend, Next.js frontend, Tailwind UI, Prisma ORM",
                        icon = Icons.Default.Code,
                        onClick = onPresetSelect
                    )
                }
            }

            // ── Bottom Input & Controls ──────────────────────────
            BottomChatBar(
                promptText = promptText,
                onPromptChange = onPromptChange,
                selectedModel = selectedModel,
                onModelClick = onModelClick,
                onMicClick = onMicClick,
                onSubmit = {
                    if (promptText.isNotBlank()) {
                        onPresetSelect(promptText)
                    }
                }
            )
        }
    }
}
