package com.example.agenticarchitect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.theme.GlassIconCircleBg
import com.example.agenticarchitect.theme.GlassIconCircleBorder
import com.example.agenticarchitect.theme.SubtitleBrown

@Composable
fun TopHeaderBar(
    userName: String = "Brakeman",
    subtitle: String = "Welcome back",
    onTuneClick: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Profile Info
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x22FFFFFF),
                                Color(0x11000000)
                            )
                        )
                    )
                    .border(1.2.dp, Color(0x33FFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Avatar",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = userName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.2.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = SubtitleBrown
                )
            }
        }

        // Action Icons (Tune & Grid/Menu)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassIconCircleBg)
                    .border(1.dp, GlassIconCircleBorder, CircleShape)
                    .clickable { onTuneClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings / Tune",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassIconCircleBg)
                    .border(1.dp, GlassIconCircleBorder, CircleShape)
                    .clickable { onMenuClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Menu / Grid",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
