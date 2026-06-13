package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.notifications.AndroidNotificationController

/// True when an intent is one of the app's deep links that lands directly on the editor — an
/// ACTION_VIEW into the app's own URI scheme whose path targets a new or existing note. The list
/// deep link (same scheme) is excluded: it lands on the list, which is a valid place to stay.
fun isEditorDeepLink(action: String?, scheme: String?, path: String?): Boolean =
    action == Intent.ACTION_VIEW &&
        scheme == AndroidNotificationController.DEEP_LINK_SCHEME &&
        (path == "/new" || path?.startsWith("/editor") == true)
