package com.elianiva.jisholens.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentManagerTest {
    @Test
    fun `a component that is already on the device is ready without downloading`() = runTest {
        val component = FakeComponent(installed = true)
        val manager = ComponentManager(listOf(component), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()

        assertEquals(ComponentState.Ready, manager.statuses.value.single().state)
        assertEquals(0, component.installs)
    }

    @Test
    fun `a missing component downloads and reports its progress`() = runTest {
        val component = FakeComponent(steps = listOf(10, 60, 100))
        val manager = ComponentManager(listOf(component), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()

        assertEquals(ComponentState.Ready, manager.statuses.value.single().state)
        assertEquals(listOf<Int?>(10, 60, 100), component.reported)
    }

    @Test
    fun `a failed component stays failed until a retry succeeds`() = runTest {
        val component = FakeComponent(succeeding = false)
        val manager = ComponentManager(listOf(component), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()
        assertEquals(ComponentState.Failed, manager.statuses.value.single().state)

        component.succeeding = true
        manager.retry(component.id)

        assertEquals(ComponentState.Ready, manager.statuses.value.single().state)
        assertEquals(2, component.installs)
    }

    @Test
    fun `one failure does not hold up another component`() = runTest {
        val broken = FakeComponent(id = "broken", succeeding = false)
        val working = FakeComponent(id = "working")
        val manager = ComponentManager(listOf(broken, working), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()

        assertEquals(ComponentState.Failed, manager.statuses.value.first { it.id == "broken" }.state)
        assertEquals(ComponentState.Ready, manager.statuses.value.first { it.id == "working" }.state)
    }

    @Test
    fun `a component the device cannot install is not reported as failed`() = runTest {
        val component = FakeComponent(supported = false)
        val manager = ComponentManager(listOf(component), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()

        assertEquals(ComponentState.Unavailable, manager.statuses.value.single().state)
        assertEquals(0, component.installs)
    }

    @Test
    fun `a ready component ignores retry`() = runTest {
        val component = FakeComponent(installed = true)
        val manager = ComponentManager(listOf(component), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        manager.start()
        manager.retry(component.id)

        assertEquals(0, component.installs)
    }
}

private class FakeComponent(
    override val id: String = "ocr",
    override val kind: ComponentKind = ComponentKind.ENGINE,
    override val title: String = "Japanese text recognition",
    var installed: Boolean = false,
    private val supported: Boolean = true,
    var succeeding: Boolean = true,
    private val steps: List<Int?> = listOf(null, 100),
) : Component {
    var installs = 0
    val reported = mutableListOf<Int?>()

    override suspend fun isInstalled(): Boolean = installed

    override suspend fun isSupported(): Boolean = supported

    override suspend fun install(onProgress: (Int?) -> Unit): Boolean {
        installs++
        steps.forEach { step ->
            reported += step
            onProgress(step)
        }
        if (succeeding) installed = true
        return succeeding
    }
}
