package com.elianiva.jisholens.ui.screens

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.BuildConfig
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.screen.LensAccessibilityService
import com.elianiva.jisholens.screen.LensSetupActivity
import com.elianiva.jisholens.data.AppTheme
import com.elianiva.jisholens.data.Settings
import com.elianiva.jisholens.ui.components.SectionLabel
import com.elianiva.jisholens.ui.components.segmentedItemColors
import com.elianiva.jisholens.ui.components.SegmentedList
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenDictionaries: () -> Unit,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var liveReading by remember { mutableStateOf(LensAccessibilityService.isEnabled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) liveReading = LensAccessibilityService.isEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun open(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
            android.widget.Toast.makeText(context, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    SettingsContent(
        settings = settings,
        dictionarySummary = when {
            dictionaries.isEmpty() -> "None imported"
            dictionaries.none { it.enabled } -> "${dictionaries.size} imported, none on"
            else -> "${dictionaries.count { it.enabled }} of ${dictionaries.size} on"
        },
        versionName = BuildConfig.VERSION_NAME,
        dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onTheme = { theme -> scope.launch { container.settings.setTheme(theme) } },
        onDynamicColor = { scope.launch { container.settings.setDynamicColor(it) } },
        onFullText = { scope.launch { container.settings.setFullTextSearch(it) } },
        onOpenDictionaries = onOpenDictionaries,
        onOpenLink = ::open,
        liveReading = liveReading,
        onOpenLiveReading = { context.startActivity(LensSetupActivity.intent(context)) },
    )
}

@Composable
fun SettingsContent(
    settings: Settings,
    dictionarySummary: String,
    versionName: String,
    dynamicColorAvailable: Boolean,
    onTheme: (AppTheme) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFullText: (Boolean) -> Unit,
    onOpenDictionaries: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    liveReading: Boolean = false,
    onOpenLiveReading: () -> Unit = {},
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("Dictionaries")
            SegmentedList {
                SegmentedListItem(
                    onClick = onOpenDictionaries,
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.AutoMirrored.Rounded.LibraryBooks) },
                    supportingContent = { Text(dictionarySummary) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    content = { Text("Manage dictionaries") },
                )
            }

            SectionLabel("Appearance")
            val appearanceCount = if (dynamicColorAvailable) 2 else 1
            SegmentedList {
                SegmentedListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes(0, appearanceCount),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.Rounded.Palette) },
                    supportingContent = {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            AppTheme.entries.forEachIndexed { index, theme ->
                                TonalToggleButton(
                                    checked = settings.theme == theme,
                                    onCheckedChange = { onTheme(theme) },
                                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        AppTheme.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                ) {
                                    Text(theme.label)
                                }
                            }
                        }
                    },
                    content = { Text("Theme") },
                )
                if (dynamicColorAvailable) {
                    SegmentedListItem(
                        onClick = { onDynamicColor(!settings.dynamicColor) },
                        shapes = ListItemDefaults.segmentedShapes(1, 2),
                    colors = segmentedItemColors(),
                        leadingContent = { RowIcon(Icons.Rounded.Wallpaper) },
                        trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
                        content = { Text("Wallpaper colors") },
                    )
                }
            }

            SectionLabel("Search")
            SegmentedList {
                SegmentedListItem(
                    onClick = { onFullText(!settings.fullTextSearch) },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.AutoMirrored.Rounded.ManageSearch) },
                    trailingContent = { Switch(checked = settings.fullTextSearch, onCheckedChange = null) },
                    content = { Text("Search inside definitions") },
                )
            }

            SectionLabel("Screen lens")
            SegmentedList {
                SegmentedListItem(
                    onClick = onOpenLiveReading,
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.Rounded.CenterFocusStrong) },
                    supportingContent = { Text(if (liveReading) "On" else "Off") },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    content = { Text("Live reading") },
                )
            }

            SectionLabel("About")
            SegmentedList {
                SegmentedListItem(
                    onClick = { onOpenLink("https://github.com/elianiva/jisho-lens") },
                    shapes = ListItemDefaults.segmentedShapes(0, 3),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.Rounded.Code) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    content = { Text("Jisho Lens $versionName") },
                )
                SegmentedListItem(
                    onClick = { onOpenLink("https://github.com/elianiva/jisho-lens/issues") },
                    shapes = ListItemDefaults.segmentedShapes(1, 3),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.Rounded.BugReport) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    content = { Text("Report an issue") },
                )
                SegmentedListItem(
                    onClick = { onOpenLink("https://github.com/elianiva/jisho-lens/blob/master/THIRD_PARTY_NOTICES.md") },
                    shapes = ListItemDefaults.segmentedShapes(2, 3),
                    colors = segmentedItemColors(),
                    leadingContent = { RowIcon(Icons.Rounded.Description) },
                    supportingContent = { Text("Jitendex and JMdict are CC BY-SA 4.0") },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    content = { Text("Licenses and attribution") },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}
