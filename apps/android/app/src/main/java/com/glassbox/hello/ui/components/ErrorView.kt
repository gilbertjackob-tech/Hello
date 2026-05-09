package com.glassbox.hello.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    HelloErrorState(message = message, onRetry = onRetry, modifier = modifier)
}
