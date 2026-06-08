package dev.davidfdev.stela

import android.app.Application
import dev.davidfdev.stela.di.AppContainer

class StelaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
