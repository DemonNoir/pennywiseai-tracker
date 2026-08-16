package com.pennywiseai.tracker.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.R

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        titleRes = R.string.nav_home,
        icon = Icons.Default.Home
    )
    
    data object Analytics : BottomNavItem(
        route = "analytics",
        titleRes = R.string.nav_analytics,
        icon = Icons.Default.Analytics
    )
    
    data object Chat : BottomNavItem(
        route = "chat",
        titleRes = R.string.nav_chat,
        icon = Icons.AutoMirrored.Filled.Chat
    )
}
