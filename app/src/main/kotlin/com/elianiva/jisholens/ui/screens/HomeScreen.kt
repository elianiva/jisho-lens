package com.elianiva.jisholens.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    container: AppContainer,
    onScan: (String) -> Unit,
    onOpenDictionaries: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val hasDictionary = dictionaries.any { it.enabled }
    var cameraPath by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath
        if (saved && path != null) onScan(path)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                onScan(path)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ScanTile(
            label = "Take a picture",
            icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(32.dp)) },
            onClick = {
                if (!hasDictionary) {
                    onOpenDictionaries()
                } else {
                    val file = File(context.cacheDir, "scans/scan-${System.currentTimeMillis()}.jpg")
                    file.parentFile?.mkdirs()
                    cameraPath = file.absolutePath
                    camera.launch(
                        FileProvider.getUriForFile(context, "${context.packageName}.files", file),
                    )
                }
            },
        )
        Text(
            text = if (hasDictionary) "Scan Japanese text from an image" else "Import a dictionary to begin",
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanTile(
            label = "Scan from gallery",
            icon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(32.dp)) },
            onClick = {
                if (!hasDictionary) {
                    onOpenDictionaries()
                } else {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
        )
    }
}

@Composable
private fun ScanTile(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Text(
                text = label,
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun copyToCache(context: Context, uri: Uri): String {
    val file = File(context.cacheDir, "scans/scan-${System.currentTimeMillis()}.jpg")
    file.parentFile?.mkdirs()
    val input = context.contentResolver.openInputStream(uri)
        ?: error("The selected image could not be read")
    input.use { source ->
        file.outputStream().use { sink -> source.copyTo(sink) }
    }
    return file.absolutePath
}
