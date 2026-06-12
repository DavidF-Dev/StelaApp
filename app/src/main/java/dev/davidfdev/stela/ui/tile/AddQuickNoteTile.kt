package dev.davidfdev.stela.ui.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dev.davidfdev.stela.R

/// The outcome of an add-tile request, narrowed to the cases the Settings row surfaces to the user.
enum class AddTileResult { ADDED, ALREADY_ADDED, OTHER }

/// Asks the system to add the quick-note Quick Settings tile. The system shows its own confirm dialog;
/// [onResult] reports whether the tile ended up present. Requires API 33 (the request API's baseline).
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun requestAddQuickNoteTile(context: Context, onResult: (AddTileResult) -> Unit) {
    val statusBar = context.getSystemService<StatusBarManager>() ?: return
    statusBar.requestAddTileService(
        ComponentName(context, QuickNoteTileService::class.java),
        context.getString(R.string.qs_tile_label),
        Icon.createWithResource(context, R.drawable.ic_stela_pin),
        context.mainExecutor,
    ) { result ->
        onResult(
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> AddTileResult.ADDED
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AddTileResult.ALREADY_ADDED
                else -> AddTileResult.OTHER
            },
        )
    }
}
