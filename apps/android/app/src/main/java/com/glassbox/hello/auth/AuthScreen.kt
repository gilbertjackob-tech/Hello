package com.glassbox.hello.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloBrandMark
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloScreenBackground
import com.glassbox.hello.ui.components.HelloTextField
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

private enum class AuthMode {
    Login,
    Register
}

private enum class LoginStep {
    Username,
    Question
}

private val commonQuestions = listOf(
    "What was the name of your first pet?",
    "In what city where you born?",
    "What is your mother's maiden name?",
    "What was the name of your first school?",
    "What is your favorite book?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel()
    val authState by viewModel.authState.collectAsState()
    val securityQuestion by viewModel.securityQuestion.collectAsState()
    val questionState by viewModel.questionState.collectAsState()

    var mode by remember { mutableStateOf(AuthMode.Login) }
    var loginStep by remember { mutableStateOf(LoginStep.Username) }
    var name by remember { mutableStateOf("") }
    var selectedSecurityQuestion by remember { mutableStateOf(commonQuestions.first()) }
    var securityAnswer by remember { mutableStateOf("") }
    var questionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedName = context
            .getSharedPreferences("hello_auth", Context.MODE_PRIVATE)
            .getString("whatsclone_last_username", null)
        if (!savedName.isNullOrBlank()) {
            name = savedName
        }
    }

    LaunchedEffect(authState) {
        val state = authState
        if (state is ResultState.Success && state.data != null) {
            onAuthSuccess(state.data)
        }
    }

    LaunchedEffect(questionState, securityQuestion) {
        if (questionState is ResultState.Success && securityQuestion != null) {
            loginStep = LoginStep.Question
            securityAnswer = ""
            saveLastUsername(context, name.trim())
        } else if (
            questionState is ResultState.Error &&
            (questionState as ResultState.Error).message == "User not found"
        ) {
            mode = AuthMode.Register
        }
    }

    val switchToLogin = {
        mode = AuthMode.Login
        loginStep = LoginStep.Username
        securityAnswer = ""
        viewModel.resetState()
    }
    val switchToRegister = {
        mode = AuthMode.Register
        loginStep = LoginStep.Username
        securityAnswer = ""
        viewModel.resetState()
    }

    val isCheckingQuestion = questionState is ResultState.Loading
    val isAuthLoading = authState is ResultState.Loading
    val errorMessage = when {
        authState is ResultState.Error -> (authState as ResultState.Error).message
        questionState is ResultState.Error -> (questionState as ResultState.Error).message
        else -> null
    }

    HelloScreenBackground(
        modifier = modifier,
        auth = true,
        dark = true
    ) {
        HelloPanel(
            modifier = Modifier
                .fillMaxWidth(),
            auth = true,
            dark = true,
            strong = true,
            shape = HelloShapes.AuthCard
        ) {
            Column(
                modifier = Modifier.padding(HelloSpacing.AuthCardPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HelloBrandMark(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HelloSpacing.Xxl),
                    dark = true
                )
                Text(
                    text = if (mode == AuthMode.Login) "Welcome Back" else "Create Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = HelloColors.AuthText,
                    modifier = Modifier.padding(bottom = HelloSpacing.Xxl)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HelloSpacing.Lg),
                        shape = HelloShapes.Sm,
                        colors = CardDefaults.cardColors(containerColor = HelloColors.AuthErrorPanel)
                    ) {
                        Text(
                            text = errorMessage,
                            color = HelloColors.AuthErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(HelloSpacing.Md)
                        )
                    }
                }

                when (mode) {
                    AuthMode.Login -> LoginContent(
                        loginStep = loginStep,
                        name = name,
                        onNameChange = {
                            name = it
                            viewModel.resetQuestionState()
                        },
                        securityQuestion = securityQuestion.orEmpty(),
                        securityAnswer = securityAnswer,
                        onSecurityAnswerChange = { securityAnswer = it },
                        onContinue = { viewModel.getUserQuestion(name.trim()) },
                        onSignIn = { viewModel.login(name.trim(), securityAnswer.trim()) },
                        onBack = {
                            loginStep = LoginStep.Username
                            securityAnswer = ""
                            viewModel.resetQuestionState()
                            viewModel.resetAuthState()
                        },
                        isCheckingQuestion = isCheckingQuestion,
                        isAuthLoading = isAuthLoading
                    )

                    AuthMode.Register -> RegisterContent(
                        name = name,
                        onNameChange = { name = it },
                        selectedSecurityQuestion = selectedSecurityQuestion,
                        onSecurityQuestionChange = { selectedSecurityQuestion = it },
                        questionMenuExpanded = questionMenuExpanded,
                        onQuestionMenuExpandedChange = { questionMenuExpanded = it },
                        securityAnswer = securityAnswer,
                        onSecurityAnswerChange = { securityAnswer = it },
                        onSignUp = {
                            saveLastUsername(context, name.trim())
                            viewModel.register(
                                name.trim(),
                                selectedSecurityQuestion.trim(),
                                securityAnswer.trim()
                            )
                        },
                        isAuthLoading = isAuthLoading
                    )
                }

                Spacer(modifier = Modifier.height(HelloSpacing.Xxl))

                if (mode == AuthMode.Login) {
                    AuthModeSwitch(
                        leadingText = "Don't have an account? ",
                        actionText = "Sign up",
                        onClick = switchToRegister
                    )
                } else {
                    AuthModeSwitch(
                        leadingText = "Already have an account? ",
                        actionText = "Sign in",
                        onClick = switchToLogin
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    loginStep: LoginStep,
    name: String,
    onNameChange: (String) -> Unit,
    securityQuestion: String,
    securityAnswer: String,
    onSecurityAnswerChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    isCheckingQuestion: Boolean,
    isAuthLoading: Boolean
) {
    if (loginStep == LoginStep.Username) {
        HelloTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Username",
            auth = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        HelloPrimaryButton(
            text = if (isCheckingQuestion) "Checking..." else "Continue",
            onClick = onContinue,
            enabled = name.trim().isNotEmpty() && !isCheckingQuestion,
            auth = true
        )
    } else {
        Text(
            text = "Security Question:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = HelloColors.AuthMuted,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = securityQuestion,
            style = MaterialTheme.typography.titleMedium,
            color = HelloColors.AuthText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp)
        )

        HelloTextField(
            value = securityAnswer,
            onValueChange = onSecurityAnswerChange,
            label = "Your Answer",
            auth = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        HelloPrimaryButton(
            text = if (isAuthLoading) "Signing in..." else "Sign In",
            onClick = onSignIn,
            enabled = securityAnswer.trim().isNotEmpty() &&
                securityQuestion.isNotBlank() &&
                !isAuthLoading,
            auth = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back", color = HelloColors.AuthMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterContent(
    name: String,
    onNameChange: (String) -> Unit,
    selectedSecurityQuestion: String,
    onSecurityQuestionChange: (String) -> Unit,
    questionMenuExpanded: Boolean,
    onQuestionMenuExpandedChange: (Boolean) -> Unit,
    securityAnswer: String,
    onSecurityAnswerChange: (String) -> Unit,
    onSignUp: () -> Unit,
    isAuthLoading: Boolean
) {
    HelloTextField(
        value = name,
        onValueChange = onNameChange,
        label = "Choose a Username",
        auth = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    ExposedDropdownMenuBox(
        expanded = questionMenuExpanded,
        onExpandedChange = { onQuestionMenuExpandedChange(!questionMenuExpanded) }
    ) {
        HelloTextField(
            value = selectedSecurityQuestion,
            onValueChange = {},
            readOnly = true,
            label = "Security Question",
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = questionMenuExpanded)
            },
            auth = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = questionMenuExpanded,
            onDismissRequest = { onQuestionMenuExpandedChange(false) },
            containerColor = HelloColors.AuthInput
        ) {
            commonQuestions.forEach { question ->
                DropdownMenuItem(
                    text = { Text(question, color = HelloColors.AuthText) },
                    onClick = {
                        onSecurityQuestionChange(question)
                        onQuestionMenuExpandedChange(false)
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    HelloTextField(
        value = securityAnswer,
        onValueChange = onSecurityAnswerChange,
        label = "Your Answer (This will be your password)",
        auth = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    HelloPrimaryButton(
        text = if (isAuthLoading) "Creating..." else "Sign Up",
        onClick = onSignUp,
        enabled = name.trim().isNotEmpty() &&
            selectedSecurityQuestion.trim().isNotEmpty() &&
            securityAnswer.trim().isNotEmpty() &&
            !isAuthLoading,
        auth = true
    )
}

@Composable
private fun AuthModeSwitch(
    leadingText: String,
    actionText: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(leadingText, color = HelloColors.AuthMuted)
        Text(actionText, color = HelloColors.AuthAccent)
    }
}

private fun saveLastUsername(context: Context, username: String) {
    if (username.isBlank()) return
    context
        .getSharedPreferences("hello_auth", Context.MODE_PRIVATE)
        .edit()
        .putString("whatsclone_last_username", username)
        .apply()
}
