package com.example.gobeats.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary

@Composable
fun LibraryCategoryTile(
    title: String,
    countText: String,
    icon: ImageVector,
    iconTint: Color = GobeatsAmber,
    isCircleIcon: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = GobeatsSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
        modifier = modifier
            .width(86.dp)
            .height(112.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        GobeatsSurfaceElevated,
                        if (isCircleIcon) CircleShape else RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = countText,
                fontSize = 11.sp,
                color = GobeatsTextMuted,
                maxLines = 1
            )
        }
    }
}
