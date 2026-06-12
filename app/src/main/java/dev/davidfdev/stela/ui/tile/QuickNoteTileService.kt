package dev.davidfdev.stela.ui.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.davidfdev.stela.ui.quicknote.QuickNoteActivity

/// A Quick Settings tile that opens the quick-note popup. The tile runs in our own process, so it can
/// start the non-exported [QuickNoteActivity] directly. Behind a secure lock screen the system unlocks
/// first; the activity's own keyguard handling then decides popup vs. full editor.
class QuickNoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    // The Intent overload only exists below API 34; it is reached only there, where it isn't deprecated.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = QuickNoteActivity.newNoteIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
