package dev.davidfdev.stela.pin

class FakeServiceController : ServiceController {
    var startCount = 0
    var stopCount = 0

    override fun start() { startCount++ }
    override fun stop() { stopCount++ }
}
