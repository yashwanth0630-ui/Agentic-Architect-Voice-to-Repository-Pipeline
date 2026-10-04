package com.example.agenticarchitect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.theme.PrimaryColor
import com.example.agenticarchitect.theme.SurfaceContainer
import com.example.agenticarchitect.theme.SurfaceContainerHighest

@Composable
fun PresetActionCard(
    title: String,
    prompt: String,
    icon: ImageVector = Icons.Default.AutoAwesome,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(200.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainer.copy(alpha = 0.85f))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
            .clickable { onClick(prompt) }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Top Row: Icon Badge (Left) + Sparkle Star (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighest)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PrimaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom Text
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp,
                color = Color.White
            )
        }
    }
}
