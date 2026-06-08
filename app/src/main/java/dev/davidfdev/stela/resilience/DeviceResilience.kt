package dev.davidfdev.stela.resilience

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/// Best-effort helpers for resisting OS/OEM background kills. The OEM autostart
/// targets are inherently fragile (components vary by skin version), so every launch
/// is guarded by a resolve check and a graceful fallback.
object DeviceResilience {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /// Opens the system battery-optimization list (Play-safe; no restricted
    /// permission, unlike the direct request dialog).
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun autostartIntent(manufacturer: String = Build.MANUFACTURER): Intent? =
        autostartTarget(manufacturer)?.let {
            Intent().setComponent(ComponentName(it.packageName, it.className))
        }

    data class AutostartTarget(val packageName: String, val className: String)

    /// Pure mapping from manufacturer to its autostart-settings Activity. Null for
    /// manufacturers without a known/reliable target (e.g. stock Android, Samsung).
    /// Returns a plain data class (not [ComponentName]) so it is unit-testable.
    fun autostartTarget(manufacturer: String): AutostartTarget? =
        when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" ->
                AutostartTarget(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )

            "oppo", "realme" ->
                AutostartTarget(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                )

            "vivo" ->
                AutostartTarget(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                )

            "huawei", "honor" ->
                AutostartTarget(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )

            "oneplus" ->
                AutostartTarget(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                )

            else -> null
        }
}
