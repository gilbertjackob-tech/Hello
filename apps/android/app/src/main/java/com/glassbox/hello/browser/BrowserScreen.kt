package com.glassbox.hello.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloListItem
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg)
    ) {
        HelloTopBar(
            eyebrow = "GLASSBOX",
            title = "GlassBox Browser",
            modifier = Modifier.padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Md)
        ) {
            HelloPill("Family network", active = true)
        }

        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
            Column(modifier = Modifier.padding(HelloSpacing.Xxl), verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)) {
                Text("Native browser module placeholder", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text("Open web, share pages to Hello, and keep family network browsing visually inside the Hello app.", color = HelloColors.DarkTextMuted)
            }
        }

        Spacer(modifier = Modifier.height(HelloSpacing.Lg))

        Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            BrowserAction("Open web", "Launch the Hello web surface", Icons.Default.OpenInBrowser)
            BrowserAction("Share page to Hello", "Prepare a GlassBox page share", Icons.Default.Share)
            BrowserAction("Family network browser", "Use the private network browser context", Icons.Default.Public)
        }
    }
}

@Composable
private fun BrowserAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        HelloListItem(
            title = title,
            subtitle = subtitle,
            leading = {
                HelloIconButton(onClick = {}, active = true) {
                    Icon(icon, contentDescription = null, tint = HelloColors.DarkAccent)
                }
            }
        )
    }
}
