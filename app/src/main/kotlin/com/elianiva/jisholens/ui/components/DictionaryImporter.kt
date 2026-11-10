package com.elianiva.jisholens.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.elianiva.jisholens.data.ImportService

/**
 * Returns an action that opens the file picker and starts the import service for the chosen
 * archive. On Android 13 and later it asks for the notification permission first, so the
 * foreground notification can show progress.
 */
@Composable
fun rememberDictionaryImporter(): () -> Unit {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingUri?.let { uri ->
            ImportService.start(context, uri)
            pendingUri = null
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingUri = uri
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ImportService.start(context, uri)
            }
        }
    }
    return remember(picker) { { picker.launch(arrayOf("*/*")) } }
}
