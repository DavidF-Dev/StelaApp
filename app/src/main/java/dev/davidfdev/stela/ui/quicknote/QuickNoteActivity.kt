package dev.davidfdev.stela.ui.quicknote

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
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
/// AppCompat is required by the emoji picker's `BottomSheetDialog`, as in [MainActivity].
class QuickNoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StelaApp).container

        val noteId = if (intent.hasExtra(EXTRA_NOTE_ID)) intent.getLongExtra(EXTRA_NOTE_ID, 0L) else null
        val pinOnSave = intent.getBooleanExtra(EXTRA_PIN, true)

        // Behind a secure lock screen the overlay isn't shown; fall back to the full editor in
        // MainActivity (which unlocks first), matching pre-popup behaviour.
        val keyguard = getSystemService<KeyguardManager>()
        if (keyguard != null && keyguard.isKeyguardLocked && keyguard.isDeviceSecure) {
            startActivity(fullEditorIntent(noteId, pinOnSave))
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
            StelaTheme(darkTheme) {
                QuickNotePopup(
                    viewModel = viewModel(factory = EditorViewModel.Factory),
                    noteId = noteId,
                    onExpand = { draft ->
                        container.pendingDraft = draft
                        startActivity(fullEditorIntent(draft.noteId, draft.pinOnSave))
                        finish()
                    },
                    onFinished = { finish() },
                )
            }
        }
    }

    /// Surfaces the intent's note id / pin intent as the [EditorViewModel]'s `SavedStateHandle` default
    /// args. A nav back-stack entry does this automatically from its route arguments; an Activity does
    /// not, so wire them in here (Open question 2 in the popup spec).
    override val defaultViewModelCreationExtras: CreationExtras
        get() {
            val extras = MutableCreationExtras(super.defaultViewModelCreationExtras)
            extras[DEFAULT_ARGS_KEY] = Bundle().apply {
                if (intent.hasExtra(EXTRA_NOTE_ID)) {
                    putLong(EditorViewModel.NOTE_ID_KEY, intent.getLongExtra(EXTRA_NOTE_ID, 0L))
                }
                putBoolean(EditorViewModel.PIN_KEY, intent.getBooleanExtra(EXTRA_PIN, true))
            }
            return extras
        }

    private fun fullEditorIntent(noteId: Long?, pinOnSave: Boolean): Intent {
        val uri = if (noteId != null) {
            "${AndroidNotificationController.DEEP_LINK_BASE}/editor/$noteId"
        } else {
            "${AndroidNotificationController.DEEP_LINK_BASE}/new?${EditorViewModel.PIN_KEY}=$pinOnSave"
        }
        return Intent(Intent.ACTION_VIEW, uri.toUri(), this, MainActivity::class.java)
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
