package com.elianiva.jisholens.data

/** What a component contributes, so the app can group and name its downloads. */
enum class ComponentKind(val label: String) {
    /** Dictionary data that lookups read from. */
    ENTRIES("Entries"),

    /** Models that turn a picture or a sentence into words. */
    ENGINE("Engine"),
}

/**
 * One part of the app that is fetched after install and kept on the device.
 *
 * Components download when the app opens, not when their feature is first used,
 * so a scan or a lookup never starts a download behind the reader's back.
 */
interface Component {
    val id: String
    val kind: ComponentKind

    /** The name the download indicator shows. */
    val title: String

    /** True when the component is on the device and its feature can run. */
    suspend fun isInstalled(): Boolean

    /** False when this device cannot install the component at all. */
    suspend fun isSupported(): Boolean = true

    /**
     * Downloads the component and suspends until it is usable.
     *
     * [onProgress] receives 0 to 100, or null while the size is unknown. Returns
     * false when the download failed or the platform that serves it is missing.
     */
    suspend fun install(onProgress: (Int?) -> Unit): Boolean
}

/** How far a component is from being usable. */
sealed interface ComponentState {
    /** The app is asking the platform whether the component is on the device. */
    data object Checking : ComponentState

    /** The component is arriving. [percent] is null while the size is unknown. */
    data class Downloading(val percent: Int?) : ComponentState

    /** The component is on the device. */
    data object Ready : ComponentState

    /** The device cannot install the component, for example because Play services is missing. */
    data object Unavailable : ComponentState

    /** The download did not finish. The next app start tries again. */
    data object Failed : ComponentState
}

/** What the download indicator shows for one component. */
data class ComponentStatus(
    val id: String,
    val kind: ComponentKind,
    val title: String,
    val state: ComponentState,
)
