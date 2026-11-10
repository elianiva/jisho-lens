package com.elianiva.jisholens.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.ShapeBadge
import com.elianiva.jisholens.ui.components.pressedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ScanHomeScreen(
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

    ScanHomeContent(
        hasDictionary = hasDictionary,
        onOpenDictionaries = onOpenDictionaries,
        onCamera = {
            val file = File(context.cacheDir, "scans/scan-${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            cameraPath = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        },
        onGallery = {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}

@Composable
fun ScanHomeContent(
    hasDictionary: Boolean,
    onOpenDictionaries: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("Scan") }, scrollBehavior = scroll) },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasDictionary) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Translate, contentDescription = null)
                    Text(
                        "No dictionary is on yet",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    TextButton(onClick = onOpenDictionaries) { Text("Set up") }
                }
            }
        }
        ScanTile(
            label = "Take a picture",
            icon = Icons.Rounded.CameraAlt,
            polygon = MaterialShapes.Cookie9Sided,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = if (hasDictionary) onCamera else onOpenDictionaries,
        )
        ScanTile(
            label = "From gallery",
            icon = Icons.Rounded.PhotoLibrary,
            polygon = MaterialShapes.Sunny,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = if (hasDictionary) onGallery else onOpenDictionaries,
        )
        ScreenLensCard(
            hasDictionary = hasDictionary,
            onOpenDictionaries = onOpenDictionaries,
        )
    }
    }
}

/**
 * The screen lens is a Quick Settings tile, not a button here: opening this screen
 * makes Jisho Lens the foreground app, so a capture started from here would photograph
 * Jisho Lens. The card explains the tile and asks the system to add it where it can.
 */
@Composable
private fun ScreenLensCard(
    hasDictionary: Boolean,
    onOpenDictionaries: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CenterFocusStrong,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Read the screen you are looking at", style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Text(
                text = "Add the Screen lens tile to Quick settings, then tap it in any app. It captures one frame and every word on it becomes tappable.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasDictionary) {
                TextButton(onClick = onOpenDictionaries, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Set up a dictionary first")
                }
            } else {
                Text(
                    text = "Open Quick settings, tap Edit, then drag Screen lens into the tiles.",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ScanTile(
    label: String,
    icon: ImageVector,
    polygon: RoundedPolygon,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = pressedCornerShape(interaction, rest = 36.dp, pressed = 16.dp),
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShapeBadge(polygon, content.copy(alpha = 0.14f), size = 80.dp, spinSeconds = 50) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            }
            Text(label, style = MaterialTheme.typography.headlineSmallEmphasized)
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
