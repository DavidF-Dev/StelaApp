package dev.davidfdev.stela.settings

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/// How the note list is ordered. Each order has a fixed direction: the timestamps
/// newest-first, the title A–Z.
enum class SortOrder { MODIFIED, CREATED, TITLE }

/// Which notes the list shows, by pin state.
enum class NoteFilter { ALL, PINNED, UNPINNED }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideOnLockScreen: Boolean = false,
    val quickAddEnabled: Boolean = true,
    val swipeToUnpin: Boolean = false,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val noteFilter: NoteFilter = NoteFilter.ALL,
)
