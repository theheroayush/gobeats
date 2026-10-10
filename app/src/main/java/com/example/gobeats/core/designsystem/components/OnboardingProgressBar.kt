package com.example.gobeats.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle

/**
 * 4-Stage Segmented Progress Bar for the Onboarding Funnel.
 * @param currentStep 1-indexed (1, 2, 3, or 4)
 * @param totalSteps Total number of steps (default 4)
 */
@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.width(110.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (step in 1..totalSteps) {
            val isPassedOrCurrent = step <= currentStep
            val isCurrent = step == currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = when {
                            isCurrent -> GobeatsAmber
                            isPassedOrCurrent -> Color.White
                            else -> GobeatsBorderSubtle
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
