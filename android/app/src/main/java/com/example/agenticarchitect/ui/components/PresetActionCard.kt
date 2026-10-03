package com.example.agenticarchitect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.agenticarchitect.theme.GlassCardBackground
import com.example.agenticarchitect.theme.GlassCardBorder

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
            .width(134.dp)
            .height(115.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassCardBackground)
            .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
            .clickable { onClick(prompt) }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Top Left Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(19.dp)
            )

            // Bottom Text
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                color = Color.White
            )
        }
    }
}
