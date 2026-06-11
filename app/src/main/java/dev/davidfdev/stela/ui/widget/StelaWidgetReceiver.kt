package dev.davidfdev.stela.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/// The manifest-registered receiver that binds [StelaWidget] to the home screen.
class StelaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StelaWidget()
}
