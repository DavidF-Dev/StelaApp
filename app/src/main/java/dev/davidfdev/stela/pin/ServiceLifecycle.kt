package dev.davidfdev.stela.pin

/// The single rule that decides whether the pin service should be running. The
/// service exists to keep something alive: at least one pinned note, or (from
/// Phase 5) the quick-add entry.
object ServiceLifecycle {
    fun shouldRun(pinnedCount: Int, quickAddEnabled: Boolean): Boolean =
        pinnedCount > 0 || quickAddEnabled
}
