package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R

@Composable
fun PermissionDisclosureDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Info
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title)
        },
        text = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.perm_disclosure_agree))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.perm_disclosure_not_now))
            }
        }
    )
}
