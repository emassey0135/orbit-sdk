package dev.emassey0135.orbit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * An app's own activity brought to the front from the background, as Orbit's own Alarm Clock opens
 * its ringing dialog over whatever is shown: through root's `am start`, since Android starts no
 * activity for an app in the background, and Magisk is on every Speak and Player. For what cannot
 * wait for the person to come back to the app, such as an alarm or a reminder going off; the app
 * then says it in front as Orbit's own dialogs are said, and once it is dealt with goes back
 * ([returnToPrevious]), so the person is where they were, as the Alarm Clock's dialog leaves.
 *
 * Root may be refused, and then nothing comes to the front: whatever else the app does about it
 * (a notification, which Satellite's shade shows) stays its only way of being heard.
 */
public object Foreground {
    /**
     * Wakes the device and starts [activity], which must be this app's, in front, with [extras] as
     * its intent's string extras, and marked as come from the background ([openedFromBackground]).
     * It blocks while `su` runs, so never call it on the main thread. Fails, with why, where root is
     * refused or `am` refuses the activity.
     */
    public fun open(context: Context, activity: Class<out Activity>, extras: Map<String, String> = emptyMap()): Result<Unit> {
        wake(context)
        return Root.run(command(context.packageName, activity.name, extras + (FROM_BACKGROUND to "true"))).mapCatching { output ->
            // `am start` exits 0 even when it refuses, saying why on a line of its own.
            val refused = output.lineSequence().firstOrNull { it.startsWith("Error") }
            if (refused != null) throw RootFailure(refused)
        }
    }

    /**
     * Whether [intent] is one [open] started: the app came to the front over whatever the person was
     * in, and goes back to it ([returnToPrevious]) once what it came for is dealt with. False where
     * the app was opened as usual, or was in front already.
     */
    public fun openedFromBackground(intent: Intent?): Boolean = intent?.getStringExtra(FROM_BACKGROUND) == "true"

    /** Takes the mark of [open] out of [intent] once heard, so the activity made again does not go back of itself. */
    public fun heard(intent: Intent?) {
        intent?.removeExtra(FROM_BACKGROUND)
    }

    /**
     * Goes back to whatever was in front before the app came to the front, as the Alarm Clock's
     * dialog leaves once it is dismissed: the app's task goes behind, its state kept.
     */
    public fun returnToPrevious(activity: Activity) {
        activity.moveTaskToBack(true)
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

    private const val FROM_BACKGROUND = "dev.emassey0135.orbit.FROM_BACKGROUND"
    private const val WAKE_TAG = "orbit-sdk:foreground"
    private const val WAKE_MS = 3_000L
}
