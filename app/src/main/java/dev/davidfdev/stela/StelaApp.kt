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
        observeLockScreenPreference()
    }

    // Keep the controller's visibility in sync with the preference, and re-assert
    // pinned notifications when it changes so already-posted ones update. The first
    // emission only seeds the value (no re-post on every launch).
    private fun observeLockScreenPreference() {
        scope.launch {
            container.settingsRepository.settings
                .map { it.hideOnLockScreen }
                .distinctUntilChanged()
                .collectIndexed { index, hide ->
                    container.notificationController.hideOnLockScreen = hide
                    if (index > 0) container.notePinner.reassertPinned()
                }
        }
    }
}
