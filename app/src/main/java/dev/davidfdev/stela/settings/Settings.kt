package dev.davidfdev.stela.settings

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/// The note list's sort field. Each has a default direction — timestamps newest-first,
/// title A–Z — that [Settings.sortReversed] inverts.
enum class SortOrder { MODIFIED, CREATED, TITLE }

/// Which notes the list shows, by pin state.
enum class NoteFilter { ALL, PINNED, UNPINNED }

/// What "removing" a pinned note does — from the notification's remove action, and from a swipe when
/// [Settings.swipeToRemove] is on. DELETE is permanent (and has no undo from the tray).
enum class RemovalPreference { UNPIN, ARCHIVE, DELETE }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideOnLockScreen: Boolean = false,
    val quickAddEnabled: Boolean = true,
    val swipeToRemove: Boolean = false,
    val removalPreference: RemovalPreference = RemovalPreference.UNPIN,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val sortReversed: Boolean = false,
    val noteFilter: NoteFilter = NoteFilter.ALL,
)
