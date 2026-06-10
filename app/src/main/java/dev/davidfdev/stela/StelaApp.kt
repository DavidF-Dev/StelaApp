package dev.davidfdev.stela

import android.app.Application
import dev.davidfdev.stela.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StelaApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        observePinnedNotificationPreferences()
        observeQuickAddPreference()
    }

    /// Starts/stops/swaps the service whenever the quick-add preference changes (and
    /// once on launch). Starting is a no-op without notification permission; the UI
    /// requests it and this re-evaluates on grant.
    private fun observeQuickAddPreference() {
        scope.launch {
            container.settingsRepository.settings
                .map { it.quickAddEnabled }
                .distinctUntilChanged()
                .collect { container.notePinner.reconcileService() }
        }
    }

    /// Keeps the controller's pinned-notification flags (lock-screen visibility,
    /// swipe-to-unpin) in sync with preferences, and re-asserts pinned notifications when
    /// either changes so already-posted ones update. The first emission only seeds the
    /// values (no re-post on every launch).
    private fun observePinnedNotificationPreferences() {
        scope.launch {
            container.settingsRepository.settings
                .map { it.hideOnLockScreen to it.swipeToUnpin }
                .distinctUntilChanged()
                .collectIndexed { index, (hide, swipe) ->
                    container.notificationController.hideOnLockScreen = hide
                    container.notificationController.swipeToUnpin = swipe
                    if (index > 0) container.notePinner.reassertPinned()
                }
        }
    }
}
