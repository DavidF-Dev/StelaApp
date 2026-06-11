package dev.davidfdev.stela

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.google.GoogleEmojiProvider
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.di.AppContainer
import dev.davidfdev.stela.ui.widget.StelaWidget
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
        // Bundled colour sprites — every emoji renders consistently and offline, with no font dependency.
        EmojiManager.install(GoogleEmojiProvider())
        container = AppContainer(this)
        observePinnedNotificationPreferences()
        observeQuickAddPreference()
        observePinnedNotesForWidget()
    }

    /// Re-renders the home-screen widget whenever the pinned-notes set it shows changes (pin/unpin, a
    /// pinned note's title or emoji edit, delete). Keyed on id + display title so unrelated edits don't
    /// redraw it. Fires on the first emission too, not just changes: a widget can show a stale snapshot
    /// after the process was killed, and creating a pinned note from the widget cold-starts this process
    /// with the note already present in that first emission — skipping it would never update the widget.
    private fun observePinnedNotesForWidget() {
        scope.launch {
            container.noteRepository.notes
                .map { notes -> notes.filter { it.isPinned }.map { it.id to it.displayTitle } }
                .distinctUntilChanged()
                .collect { StelaWidget().updateAll(this@StelaApp) }
        }
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
