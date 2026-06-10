package dev.davidfdev.stela.settings

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideOnLockScreen: Boolean = false,
    val quickAddEnabled: Boolean = true,
    val swipeToUnpin: Boolean = false,
)
