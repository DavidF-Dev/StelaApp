package dev.davidfdev.stela.ui.settings

import dev.davidfdev.stela.settings.FakeSettingsRepository
import dev.davidfdev.stela.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setThemeMode_updatesState() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    @Test
    fun setHideOnLockScreen_updatesState() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setHideOnLockScreen(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hideOnLockScreen)
    }

    @Test
    fun setQuickAddEnabled_updatesState() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setQuickAddEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.quickAddEnabled)
    }
}
