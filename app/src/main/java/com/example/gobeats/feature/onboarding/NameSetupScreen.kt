package com.example.gobeats.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.OnboardingProgressBar
import com.example.gobeats.data.repository.SettingsRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsCharcoal
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

/**
 * Screen S01B: Onboarding Name Personalization Screen (Step 2 of 4)
 * Allows the user to enter their name for personalized home greetings and profile.
 */
@Composable
fun NameSetupScreen(
    settingsRepository: SettingsRepository,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val userProfile by settingsRepository.userProfile.collectAsState()
    var nameInput by remember { mutableStateOf(userProfile.name.ifBlank { "Ayush" }) }

    fun submit() {
        val cleanName = nameInput.trim().ifBlank { "Music Lover" }
        settingsRepository.setUserName(cleanName)
        onContinue()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GobeatsObsidian,
                        GobeatsCharcoal,
                        GobeatsObsidian
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onBack() }
                    )

                    // 4-Stage Segmented Progress Bar (Stage 2 Active)
                    OnboardingProgressBar(currentStep = 2)

                    Text(
                        text = "Skip",
                        fontSize = 14.sp,
                        color = GobeatsTextMuted,
                        modifier = Modifier
                            .clickable {
                                settingsRepository.setUserName("Music Lover")
                                onSkip()
                            }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Avatar Icon with Ambient Ring
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(GobeatsSurfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = GobeatsAmber,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "What should we call you?",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Personalize your gobeats experience. We'll use this\nfor your greetings and offline stats.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = GobeatsTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Input Field
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = {
                        Text("Enter your name (e.g. Ayush)", color = GobeatsTextMuted, fontSize = 15.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GobeatsSurface,
                        unfocusedContainerColor = GobeatsSurface,
                        focusedBorderColor = GobeatsAmber,
                        unfocusedBorderColor = GobeatsBorderSubtle,
                        focusedTextColor = GobeatsTextPrimary,
                        unfocusedTextColor = GobeatsTextPrimary,
                        cursorColor = GobeatsAmber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Pill Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GobeatsAmber.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Greeting Preview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextMuted
                            )
                            Text(
                                text = "Welcome, ${nameInput.ifBlank { "Music Lover" }}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GobeatsPrimaryButton(
                    text = "Continue",
                    onClick = { submit() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Step 2 of 4 • Profile Setup",
                    fontSize = 11.sp,
                    color = GobeatsTextMuted
                )
            }
        }
    }
}
