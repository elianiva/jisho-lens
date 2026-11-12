package com.elianiva.jisholens.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.elianiva.jisholens.R
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.buildAnkiNote
import com.elianiva.jisholens.data.mineToAnki
import com.elianiva.jisholens.ui.screens.LensOverlayContent
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import kotlinx.coroutines.delay

/**
 * Owns one lens overlay window.
 *
 * The window is an accessibility overlay, so it needs no draw-over-other-apps grant,
 * and it is not focusable, so the app underneath keeps its focus. Compose needs a
 * lifecycle and a saved-state owner even outside an activity; this class supplies
 * both for the lifetime of the window. A new host is made for each opening, which
 * keeps a destroyed lifecycle from being reused.
 */
class LensOverlayHost(
    private val service: LensAccessibilityService,
    private val container: AppContainer,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, LensOverlayVisibility {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val themedContext = ContextThemeWrapper(service, R.style.Theme_JishoLens_Translucent)
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val screenSize = mutableStateOf(displaySize())
    private val refresh = Runnable { presenter?.refresh() }
    private val start = Runnable { presenter?.start() }

    private var view: ComposeView? = null
    private var presenter: LensPresenter? = null

    private val reader = LensNodeReader(service, container.ocr, this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    val isShowing: Boolean get() = view != null

    init {
        savedState.performRestore(null)
    }

    fun show(): Boolean {
        if (view != null) return true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val lens = LensPresenter(
            repository = container.repository,
            settings = container.settings,
            reader = reader,
            onDismiss = ::hide,
        )
        presenter = lens
        val compose = ComposeView(themedContext).apply {
            setViewTreeLifecycleOwner(this@LensOverlayHost)
            setViewTreeViewModelStoreOwner(this@LensOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@LensOverlayHost)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                val state by lens.state.collectAsStateWithLifecycle()
                val size by screenSize
                JishoLensTheme(
                    darkTheme = settings?.theme?.isDark(isSystemInDarkTheme()) ?: false,
                    dynamicColor = settings?.dynamicColor ?: true,
                ) {
                    LensOverlayContent(
                        state = state,
                        screenSize = size,
                        loadMedia = container.repository::media,
                        onTap = lens::onTap,
                        onSelectCandidate = lens::selectCandidate,
                        onToggleKnown = lens::toggleKnown,
                        onExpand = lens::expand,
                        onCollapse = lens::collapse,
                        onClearSelection = lens::clearSelection,
                        onRescan = lens::rescan,
                        onReadImage = lens::readImage,
                        onScroll = lens::scroll,
                        onCopy = ::copy,
                        onMine = { entry ->
                            val note = buildAnkiNote(entry, state.line?.text)
                            runCatching { service.mineToAnki(note) }
                                .onFailure {
                                    Toast.makeText(service, "No app can open the note", Toast.LENGTH_SHORT).show()
                                }
                        },
                        onClose = ::hide,
                    )
                }
            }
        }
        val added = runCatching {
            windowManager.addView(compose, windowParams())
        }.isSuccess
        if (!added) {
            lens.close()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
            presenter = null
            return false
        }
        view = compose
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        // The trigger activity is still the active window for a frame or two. Reading now
        // would read Jisho Lens instead of the page, so let it finish first.
        handler.postDelayed(start, START_DELAY_MILLIS)
        LensAccessibilityService.requestTileUpdate(service)
        return true
    }

    fun hide() {
        val compose = view ?: return
        view = null
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(start)
        presenter?.close()
        presenter = null
        runCatching { windowManager.removeView(compose) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        service.onOverlayHidden(this)
        LensAccessibilityService.requestTileUpdate(service)
    }

    /** A window changed under the lens: read it again shortly, without fighting a live selection. */
    fun scheduleRefresh() {
        if (view == null) return
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, REFRESH_DELAY_MILLIS)
    }

    fun onWindowChanged() {
        presenter?.onWindowChanged()
    }

    fun onConfigurationChanged() {
        screenSize.value = displaySize()
        scheduleRefresh()
    }

    override suspend fun <T> hidden(block: suspend () -> T): T {
        val compose = view ?: return block()
        compose.visibility = View.INVISIBLE
        delay(HIDE_FOR_SCREENSHOT_MILLIS)
        return try {
            block()
        } finally {
            compose.visibility = View.VISIBLE
        }
    }

    private fun displaySize(): IntSize {
        val bounds = service.displayBounds()
        return IntSize(bounds.width(), bounds.height())
    }

    private fun windowParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Jisho Lens"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun copy(text: String) {
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Jisho Lens", text))
    }

    private companion object {
        const val REFRESH_DELAY_MILLIS = 350L
        const val HIDE_FOR_SCREENSHOT_MILLIS = 120L
        const val START_DELAY_MILLIS = 150L
    }
}
