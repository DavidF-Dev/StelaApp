package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.notifications.AndroidNotificationController

/// True when an intent is one of the app's notification deep links — an ACTION_VIEW
/// into the app's own URI scheme.
fun isNotificationDeepLink(action: String?, scheme: String?): Boolean =
    action == Intent.ACTION_VIEW && scheme == AndroidNotificationController.DEEP_LINK_SCHEME
