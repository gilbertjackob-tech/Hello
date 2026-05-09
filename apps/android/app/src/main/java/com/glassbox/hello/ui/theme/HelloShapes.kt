package com.glassbox.hello.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object HelloShapes {
    val Xl = RoundedCornerShape(28.dp)
    val Lg = RoundedCornerShape(20.dp)
    val Md = RoundedCornerShape(16.dp)
    val Sm = RoundedCornerShape(12.dp)
    val AuthCard = RoundedCornerShape(16.dp)
    val AuthInput = RoundedCornerShape(8.dp)
    val ChatShell = RoundedCornerShape(32.dp)
    val HeaderPanel = RoundedCornerShape(24.dp)
    val Composer = RoundedCornerShape(24.dp)
    val ComposerInput = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(999.dp)
    val Message = RoundedCornerShape(8.dp)
    val MessageMine = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 0.dp,
        bottomEnd = 8.dp,
        bottomStart = 8.dp
    )
    val MessageOther = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 8.dp,
        bottomEnd = 8.dp,
        bottomStart = 8.dp
    )
}
