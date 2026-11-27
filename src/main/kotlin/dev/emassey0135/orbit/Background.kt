package dev.emassey0135.orbit

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Whether Android lets the app run in the background, and where the person allows it. The Speak and
 * the Player run MediaTek's DuraSpeed, which restricts every app not switched on in its own list:
 * Android then reports the app as background restricted, and holds its alarms back ("Pending user
 * blocked background alarms") until it is opened, so a reminder or an alarm set through
 * `AlarmManager` never goes off on time. DuraSpeed's own list is closed to other apps (it needs a
 * permission of MediaTek's own), so the person allows it on the app's own battery page instead.
 *
 * Ask once, as the app first finds itself restricted, where the person will hear it, rather than
 * only where the setting lives: an app that sets alarms by itself would otherwise never be heard.
 */
public object Background {
    /** Whether Android holds the app back in the background: its alarms wait, undelivered, until it is opened. */
    public fun restricted(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    /**
     * Opens where the app's background use is allowed, as close to it as Android lets another app go:
     * its own battery page (`APP_BATTERY_SETTINGS` on Android 11, which Android does not publish),
     * where Background restriction is, else its App info page. Whether one opened.
     */
    public fun openSettings(context: Context): Boolean {
        val app = Uri.parse("package:${context.packageName}")
        return listOf(Intent(APP_BATTERY, app), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)).any { intent ->
            intent.resolveActivity(context.packageManager) != null &&
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    private const val APP_BATTERY = "android.settings.APP_BATTERY_SETTINGS"
}
