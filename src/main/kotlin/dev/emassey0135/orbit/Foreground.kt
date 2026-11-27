package dev.emassey0135.orbit

import android.app.Activity
import android.content.Context
import android.os.PowerManager

/**
 * An app's own activity brought to the front from the background, as Orbit's own Alarm Clock opens
 * its ringing dialog over whatever is shown: through root's `am start`, since Android starts no
 * activity for an app in the background, and Magisk is on every Speak and Player. For what cannot
 * wait for the person to come back to the app, such as an alarm or a reminder going off; the app
 * then says it in front as Orbit's own dialogs are said.
 *
 * Root may be refused, and then nothing comes to the front: whatever else the app does about it
 * (a notification, which Satellite's shade shows) stays its only way of being heard.
 */
public object Foreground {
    /**
     * Wakes the device and starts [activity], which must be this app's, in front, with [extras] as
     * its intent's string extras. It blocks while `su` runs, so never call it on the main thread.
     * Fails, with why, where root is refused or `am` refuses the activity.
     */
    public fun open(context: Context, activity: Class<out Activity>, extras: Map<String, String> = emptyMap()): Result<Unit> {
        wake(context)
        return Root.run(command(context.packageName, activity.name, extras)).mapCatching { output ->
            // `am start` exits 0 even when it refuses, saying why on a line of its own.
            output.lineSequence().firstOrNull { it.startsWith("Error") }?.let { throw RootFailure(it) }
        }
    }

    /**
     * Wakes the device from sleep for a moment, as the Alarm Clock does as it rings, and lets it sleep
     * again by its own timeout: the screen reader does not speak while the device sleeps.
     */
    @Suppress("DEPRECATION") // ACQUIRE_CAUSES_WAKEUP is what wakes the device from the background on Android 11.
    public fun wake(context: Context) {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        runCatching {
            power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, WAKE_TAG)
                .acquire(WAKE_MS)
        }
    }

    /** `am start` for [activity] of [packageName] with [extras], each quoted for the shell. */
    internal fun command(packageName: String, activity: String, extras: Map<String, String>): String =
        buildString {
            append("am start -n ")
            append(quoted("$packageName/$activity"))
            extras.forEach { (key, value) -> append(" --es ").append(quoted(key)).append(' ').append(quoted(value)) }
        }

    /** [text] as one word to `sh`, whatever it holds. */
    internal fun quoted(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private const val WAKE_TAG = "orbit-sdk:foreground"
    private const val WAKE_MS = 3_000L
}
