package dev.davidfdev.stela.settings

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/// The note list's sort field. Each has a default direction — timestamps newest-first,
/// title A–Z — that [Settings.sortReversed] inverts.
enum class SortOrder { MODIFIED, CREATED, TITLE }

/// Which notes the list shows, by pin state.
enum class NoteFilter { ALL, PINNED, UNPINNED }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideOnLockScreen: Boolean = false,
    val quickAddEnabled: Boolean = true,
    val swipeToUnpin: Boolean = false,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val sortReversed: Boolean = false,
    val noteFilter: NoteFilter = NoteFilter.ALL,
)
