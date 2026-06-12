package dev.davidfdev.stela.ui.quicknote

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.MainActivity
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.notifications.AndroidNotificationController
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.StelaTheme
import dev.davidfdev.stela.ui.editor.EditorViewModel

/// A transparent activity hosting the quick-note popup, so a notification or widget tap can float the
/// editor over whatever is on screen (UI can't be drawn from a notification without an Activity).
/// AppCompat is required by the emoji picker's `BottomSheetDialog`, as in [MainActivity]. It is a
/// `singleTask`: a fresh trigger reuses this instance via [onNewIntent], replacing the popup's contents.
class QuickNoteActivity : AppCompatActivity() {

    // Bumped on each new intent so the popup re-seeds (fresh view-model + note id), discarding the old.
    private val intentVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StelaApp).container

        // Behind a secure lock screen the overlay isn't shown; fall back to the full editor in
        // MainActivity (which unlocks first), matching pre-popup behaviour.
        val keyguard = getSystemService<KeyguardManager>()
        if (keyguard != null && keyguard.isKeyguardLocked && keyguard.isDeviceSecure) {
            startActivity(fullEditorIntent(currentNoteId(), currentPinOnSave()))
            finish()
            return
        }

        // The app is already on-screen: route into it rather than float a popup over our own UI, which
        // would open a second editor for the same note. The popup exists to float over *other* apps.
        if (container.isMainActivityVisible) {
            startActivity(fullEditorIntent(currentNoteId(), currentPinOnSave()))
            finish()
            return
        }

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = Settings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keyed on intentVersion so onNewIntent rebuilds with a fresh view-model and note id.
            val version = intentVersion.intValue
            val noteId = remember(version) { currentNoteId() }
            StelaTheme(darkTheme) {
                QuickNotePopup(
                    viewModel = viewModel(key = "quicknote-$version", factory = EditorViewModel.Factory),
                    noteId = noteId,
                    onExpand = { draft ->
                        container.pendingDraft = draft
                        startActivity(fullEditorIntent(draft.noteId, draft.pinOnSave, fromExpand = true))
                        finish()
                    },
                    onFinished = { finish() },
                )
            }
        }
    }

    /// A `singleTask` re-trigger lands here instead of starting a second popup; swap to the new intent
    /// and bump the version so the popup re-seeds (discarding any unsaved content in the old one).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentVersion.intValue += 1
    }

    /// Surfaces the intent's note id / pin intent as the [EditorViewModel]'s `SavedStateHandle` default
    /// args. A nav back-stack entry does this automatically from its route arguments; an Activity does
    /// not, so wire them in here (Open question 2 in the popup spec). Reads the current intent, so it
    /// stays correct after [onNewIntent].
    override val defaultViewModelCreationExtras: CreationExtras
        get() {
            val extras = MutableCreationExtras(super.defaultViewModelCreationExtras)
            extras[DEFAULT_ARGS_KEY] = Bundle().apply {
                currentNoteId()?.let { putLong(EditorViewModel.NOTE_ID_KEY, it) }
                putBoolean(EditorViewModel.PIN_KEY, currentPinOnSave())
            }
            return extras
        }

    private fun currentNoteId(): Long? =
        if (intent.hasExtra(EXTRA_NOTE_ID)) intent.getLongExtra(EXTRA_NOTE_ID, 0L) else null

    private fun currentPinOnSave(): Boolean = intent.getBooleanExtra(EXTRA_PIN, true)

    private fun fullEditorIntent(noteId: Long?, pinOnSave: Boolean, fromExpand: Boolean = false): Intent {
        val uri = if (noteId != null) {
            "${AndroidNotificationController.DEEP_LINK_BASE}/editor/$noteId"
        } else {
            "${AndroidNotificationController.DEEP_LINK_BASE}/new?${EditorViewModel.PIN_KEY}=$pinOnSave"
        }
        return Intent(Intent.ACTION_VIEW, uri.toUri(), this, MainActivity::class.java).apply {
            // NEW_TASK sends MainActivity to the app's own task rather than into this popup's empty-
            // affinity task (which would tangle the two activities together).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Expand is in-app navigation, not a notification entry: the editor should go to the list
            // on done rather than finish the task.
            if (fromExpand) putExtra(MainActivity.EXTRA_FROM_POPUP_EXPAND, true)
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "dev.davidfdev.stela.extra.NOTE_ID"
        private const val EXTRA_PIN = "dev.davidfdev.stela.extra.PIN"

        /// Opens the popup on a fresh note that pins on save (the quick-add / widget entry points).
        fun newNoteIntent(context: Context): Intent =
            Intent(context, QuickNoteActivity::class.java).putExtra(EXTRA_PIN, true)

        /// Opens the popup editing an existing note (the pinned-note Edit action / body tap).
        fun editNoteIntent(context: Context, noteId: Long): Intent =
            Intent(context, QuickNoteActivity::class.java).putExtra(EXTRA_NOTE_ID, noteId)
    }
}
