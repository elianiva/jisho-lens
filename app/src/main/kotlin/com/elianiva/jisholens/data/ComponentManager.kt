package com.elianiva.jisholens.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Downloads every registered [Component] in the background and reports what
 * each one is doing.
 *
 * The manager starts the downloads once, when the app opens. A component that
 * fails stays failed until its next app start or an explicit [retry], and one
 * failure never holds up another component.
 */
class ComponentManager(
    private val components: List<Component>,
    private val scope: CoroutineScope,
) {
    private val _statuses = MutableStateFlow(
        components.map { ComponentStatus(it.id, it.kind, it.title, ComponentState.Checking) },
    )
    val statuses: StateFlow<List<ComponentStatus>> = _statuses.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        components.forEach(::download)
    }

    /** Tries a failed component again. */
    fun retry(id: String) {
        val component = components.firstOrNull { it.id == id } ?: return
        if (_statuses.value.firstOrNull { it.id == id }?.state !is ComponentState.Failed) return
        download(component)
    }

    private fun download(component: Component) {
        set(component.id, ComponentState.Checking)
        scope.launch {
            val state = try {
                when {
                    !component.isSupported() -> ComponentState.Unavailable
                    component.isInstalled() -> ComponentState.Ready
                    component.install { percent ->
                        set(component.id, ComponentState.Downloading(percent))
                    } -> ComponentState.Ready
                    else -> ComponentState.Failed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ComponentState.Failed
            }
            set(component.id, state)
        }
    }

    private fun set(id: String, state: ComponentState) = _statuses.update { statuses ->
        statuses.map { if (it.id == id) it.copy(state = state) else it }
    }
}
