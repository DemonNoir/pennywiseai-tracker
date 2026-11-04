package com.pennywiseai.tracker.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.R

/** Full gallery access is needed for automatic scanning. The photo picker still works without it. */
@Composable
internal fun rememberSlipScanAccess(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED

    var hasAccess by remember { mutableStateOf(hasMediaPermission()) }
    var showDisclosure by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = hasMediaPermission()
    }
    LifecycleResumeEffect(context) {
        hasAccess = hasMediaPermission()
        onPauseOrDispose {}
    }

    if (showDisclosure) {
        PermissionDisclosureDialog(
            onDismissRequest = { showDisclosure = false },
            onConfirm = {
                showDisclosure = false
                val permissions = buildList {
                    add(mediaPermission)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                launcher.launch(permissions.toTypedArray())
            },
            title = stringResource(R.string.slip_scan_disclosure_title),
            description = stringResource(R.string.slip_scan_disclosure_body)
        )
    }

    return hasAccess to { showDisclosure = true }
}
