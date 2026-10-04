package com.example.agenticarchitect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.theme.*

@Composable
fun CloseChatPill(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(SurfaceContainerHigh.copy(alpha = 0.60f))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(50))
            .clickable { onClose() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = PrimaryColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Close chat",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryColor,
                letterSpacing = 0.3.sp
            )
        }
    }
}

