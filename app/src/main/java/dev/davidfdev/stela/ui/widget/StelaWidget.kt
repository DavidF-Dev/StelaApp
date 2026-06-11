package dev.davidfdev.stela.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.davidfdev.stela.MainActivity
import dev.davidfdev.stela.R
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.notifications.AndroidNotificationController
import dev.davidfdev.stela.ui.quicknote.QuickNoteActivity
import kotlinx.coroutines.flow.first

/// The home-screen widget: a quick-add header plus the list of pinned notes. It renders a snapshot
/// of the pinned notes; [StelaApp] re-renders it via `updateAll` whenever that set changes. Taps
/// route through the existing deep links rather than duplicating navigation.
class StelaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pinned = (context.applicationContext as StelaApp).container
            .noteRepository.notes.first().filter { it.isPinned }
        provideContent {
            GlanceTheme {
                Content(context, pinned)
            }
        }
    }

    @Composable
    private fun Content(context: Context, notes: List<Note>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp),
        ) {
            Header(context)
            if (notes.isEmpty()) {
                EmptyState(context)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(notes, itemId = { it.id }) { note ->
                        NoteRow(context, note)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(deepLink(context, "/list"))),
            )
            Text(
                text = context.getString(R.string.widget_add),
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp)
                    // The + opens the quick-note popup; the note rows still open the full editor.
                    .clickable(actionStartActivity(QuickNoteActivity.newNoteIntent(context))),
            )
        }
    }

    @Composable
    private fun NoteRow(context: Context, note: Note) {
        Text(
            text = note.displayTitle,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(actionStartActivity(deepLink(context, "/editor/${note.id}"))),
        )
    }

    @Composable
    private fun EmptyState(context: Context) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
            )
        }
    }
}

/// An ACTION_VIEW intent into MainActivity for one of the app's existing deep-link paths.
private fun deepLink(context: Context, path: String): Intent =
    Intent(Intent.ACTION_VIEW, "${AndroidNotificationController.DEEP_LINK_BASE}$path".toUri(), context, MainActivity::class.java)
