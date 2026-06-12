package dev.davidfdev.stela.pin

/// The single seam that touches `AlarmManager`. Schedules and cancels a note's auto-pin / auto-unpin
/// alarms. Alarms are inexact and allow-while-idle, so no exact-alarm permission is needed; the reconcile
/// pass on boot / app start is the safety net for missed or reboot-cleared alarms.
interface PinScheduler {
    fun schedulePin(noteId: Long, atMillis: Long)
    fun scheduleUnpin(noteId: Long, atMillis: Long)
    fun cancelPin(noteId: Long)
    fun cancelUnpin(noteId: Long)
}

/// A scheduler that does nothing — the default where no real alarms are wanted (e.g. a unit test that
/// isn't exercising scheduling).
object NoopPinScheduler : PinScheduler {
    override fun schedulePin(noteId: Long, atMillis: Long) = Unit
    override fun scheduleUnpin(noteId: Long, atMillis: Long) = Unit
    override fun cancelPin(noteId: Long) = Unit
    override fun cancelUnpin(noteId: Long) = Unit
}
