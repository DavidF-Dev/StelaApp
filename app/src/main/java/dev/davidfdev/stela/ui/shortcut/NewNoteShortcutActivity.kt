package dev.davidfdev.stela.ui.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.davidfdev.stela.ui.quicknote.QuickNoteActivity

/// A no-UI trampoline for the "New quick note" launcher shortcut. It is exported only so the launcher
/// (a different app) has an entry point; it immediately forwards to the non-exported [QuickNoteActivity].
/// Keeping the popup non-exported means its edit-by-id path is never reachable from outside the app —
/// this trampoline only ever opens a fresh note.
class NewNoteShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(QuickNoteActivity.newNoteIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
