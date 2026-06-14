package dev.davidfdev.stela.ui

import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.runBlocking

/// Marks first-run onboarding complete so a `MainActivity`-launching UI test lands on the app rather
/// than the onboarding gate. Call from `@BeforeClass`, before the activity is launched.
fun markOnboardingComplete() {
    setOnboardingComplete(true)
}

/// Sets the onboarding-complete flag directly on the app's settings store (used to force or clear the
/// first-run state in tests). Blocks until the write lands.
fun setOnboardingComplete(value: Boolean) {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StelaApp
    runBlocking { app.container.settingsRepository.setOnboardingComplete(value) }
}
