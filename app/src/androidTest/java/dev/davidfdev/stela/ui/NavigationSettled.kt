package dev.davidfdev.stela.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText

private const val SETTLE_TIMEOUT_MILLIS = 5_000L

/// Waits until the launched activity is showing the note list, for use before a test's first interaction.
///
/// Until the persisted settings arrive the activity draws a bare placeholder, which is a perfectly idle
/// composition holding none of the app's nodes — so the framework's idle-sync lets a first tap land on
/// nothing and the lookup fails outright. The list's add button appearing marks the real UI as up.
fun ComposeTestRule.awaitNoteList() {
    waitUntil(SETTLE_TIMEOUT_MILLIS) {
        onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
    }
}

/// Waits until a just-saved note's row is on the list and the screen has settled.
///
/// Waiting on [title] alone is not enough: the editor's own title field carries the same text, so the wait
/// can pass while the editor is still up. Both destinations also stay composed through the navigation
/// cross-fade, during which an input gate swallows taps — so a test that acts too early either misses its
/// target or has its click silently dropped. The editor's Save button leaving the tree rules out both.
fun ComposeTestRule.awaitSavedNoteOnList(title: String) {
    waitUntil(SETTLE_TIMEOUT_MILLIS) {
        onAllNodesWithContentDescription("Save").fetchSemanticsNodes().isEmpty() &&
            onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    }
}

/// Waits until the editor opened from a list row is on screen and the screen has settled, so the next tap
/// is not swallowed by the input gate that covers the navigation cross-fade. The list's add button leaving
/// the tree marks the transition complete.
fun ComposeTestRule.awaitEditorFromList() {
    waitUntil(SETTLE_TIMEOUT_MILLIS) {
        onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isEmpty()
    }
}
