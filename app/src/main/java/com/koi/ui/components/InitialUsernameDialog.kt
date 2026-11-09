package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koi.ui.R
import com.koi.ui.theme.*

@Composable
fun InitialUsernameDialog(
    initialUsername: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var usernameInput by remember { mutableStateOf(initialUsername) }
    val isInputValid = usernameInput.trim().isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = RogSurfaceDark,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Avatar Icon
                val previewInitial = usernameInput.trim().firstOrNull()?.uppercaseChar()?.toString()
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(RogSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewInitial != null) {
                        Text(
                            text = previewInitial,
                            fontSize = 30.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextGold
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile),
                            contentDescription = "Profile",
                            tint = RogTextGold,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Heading
                Text(
                    text = "Welcome to Koi",
                    fontSize = 22.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "What should we call you? Enter your username to set up your profile.",
                    fontSize = 13.5.sp,
                    fontFamily = CircularFontFamily,
                    color = RogTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input Field
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = {
                        if (it.length <= 25) {
                            usernameInput = it
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Enter your username",
                            fontSize = 14.sp,
                            fontFamily = CircularFontFamily,
                            color = RogTextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RogTextPrimary,
                        unfocusedTextColor = RogTextPrimary,
                        focusedContainerColor = RogSurfaceHighlight,
                        unfocusedContainerColor = RogSurfaceHighlight,
                        cursorColor = RogGoldPrimary,
                        focusedBorderColor = RogGoldPrimary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isInputValid) {
                                onConfirm(usernameInput.trim())
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Button: Continue / Get Started
                Button(
                    onClick = {
                        if (isInputValid) {
                            onConfirm(usernameInput.trim())
                        }
                    },
                    enabled = isInputValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RogGoldPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = RogSurfaceHighlight,
                        disabledContentColor = RogTextMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 15.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Skip Action
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Skip for now",
                        fontSize = 13.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary
                    )
                }
            }
        }
    }
}
