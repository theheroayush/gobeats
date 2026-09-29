package com.example.gobeats.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsButtonPrimaryBg
import com.example.gobeats.theme.GobeatsButtonPrimaryText
import com.example.gobeats.theme.GobeatsButtonSecondaryBg
import com.example.gobeats.theme.GobeatsButtonSecondaryBorder
import com.example.gobeats.theme.GobeatsButtonSecondaryText

/**
 * Primary Pill Button matching gobeats mockups (Warm Bone-White with crisp dark typography and arrow)
 */
@Composable
fun GobeatsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = Icons.AutoMirrored.Filled.ArrowForward
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "buttonScale")

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = if (enabled) GobeatsButtonPrimaryBg else GobeatsButtonPrimaryBg.copy(alpha = 0.5f),
        contentColor = GobeatsButtonPrimaryText,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = GobeatsButtonPrimaryText
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GobeatsButtonPrimaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Secondary Dark Pill Button ("I'll explore first")
 */
@Composable
fun GobeatsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "buttonScaleSec")

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = GobeatsButtonSecondaryBg,
        border = BorderStroke(1.dp, GobeatsButtonSecondaryBorder),
        contentColor = GobeatsButtonSecondaryText,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = GobeatsButtonSecondaryText
            )
        }
    }
}
