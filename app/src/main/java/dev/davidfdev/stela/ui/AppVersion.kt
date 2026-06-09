package dev.davidfdev.stela.ui

import android.content.Context

/// The app's versionName from its package metadata (e.g. "0.1.0"), or empty if unavailable.
fun appVersionName(context: Context): String =
    runCatching {
        // The int-flags overload spans all supported API levels; the typed-flags variant is API 33+.
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""
