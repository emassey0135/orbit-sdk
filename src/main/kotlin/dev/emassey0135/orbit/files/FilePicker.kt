package dev.emassey0135.orbit.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Choosing a file or a folder through Orbit's own File Manager, as Orbit's built-in apps do: its
 * picker works with the keypad and reads itself, where Android's own (DocumentsUI) is one Orbit's
 * screen reader clicks through by itself.
 *
 * Start the intent for a result, through [launch], and read what comes back with [picked]:
 *
 * ```kotlin
 * private val choose = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
 *     val file = FilePicker.picked(result.resultCode, result.data) ?: return@registerForActivityResult
 *     thread { file.openInput(this).use { … } }
 * }
 *
 * FilePicker.launch(FileRequest(PickKind.OPEN_FILE, extensions = listOf("txt"))) { choose.launch(it) }
 * ```
 *
 * The File Manager answers with the path chosen and a `content:` Uri it grants the app to read and
 * write (persistable, and for a folder everything under it too), so an app needs no storage
 * permission to use a file it was given ([PickedFile.openInput], [PickedFile.openOutput]).
 */
public object FilePicker {
    /** The File Manager's package. */
    public const val PACKAGE: String = "com.orbit.filemanager"

    internal const val OPEN_FILE = "com.orbit.file_picker.OPEN_FILE"
    internal const val OPEN_FOLDER = "com.orbit.file_picker.OPEN_FOLDER"
    internal const val SAVE_FILE = "com.orbit.file_picker.SAVE_FILE"
    internal const val FILTERS = "extra_file_filters"
    internal const val LOCATION = "extra_last_known_location"
    internal const val TITLE = "extra_title"
    internal const val SUGGESTED_NAME = "extra_suggested_name"
    internal const val RESULT = "extra_result"
    internal const val RESULT_LIST = "extra_result_list"

    /**
     * The intent that asks for [request], to start for a result. [launch] starts it and says whether
     * it could be.
     *
     * @throws IllegalArgumentException for a [PickKind.SAVE_FILE] with no extension, given or in its
     *   suggested name: Orbit's File Manager saves only with one.
     */
    public fun intent(request: FileRequest): Intent = call(request).let { call ->
        Intent(call.action).setPackage(PACKAGE).addCategory(Intent.CATEGORY_DEFAULT).putAll(call.extras)
    }

    /**
     * Starts [request]'s intent with [start] (an `ActivityResultLauncher`'s `launch`, or
     * `startActivityForResult`), and says whether it started: false where the File Manager is not
     * there to answer.
     *
     * @throws IllegalArgumentException as [intent] does.
     */
    public fun launch(request: FileRequest, start: (Intent) -> Unit): Boolean {
        val intent = intent(request)
        return try {
            start(intent)
            true
        } catch (missing: ActivityNotFoundException) {
            false
        } catch (closed: SecurityException) {
            false
        }
    }

    /** Whether the File Manager is on this device to answer (its package is in the SDK's `<queries>`). */
    public fun isAvailable(context: Context): Boolean =
        context.packageManager.resolveActivity(intent(FileRequest(PickKind.OPEN_FILE)), 0) != null

    /**
     * What the person chose, from the result the File Manager gave: null when they cancelled, or
     * when the answer holds no path.
     */
    public fun picked(resultCode: Int, data: Intent?): PickedFile? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return picked(data.getStringExtra(RESULT), data.getStringArrayListExtra(RESULT_LIST), data.data)
    }

    /** What was chosen, from the answer's parts: the path, else the first of several. */
    internal fun picked(path: String?, paths: List<String>?, uri: Uri?): PickedFile? {
        val chosen = path?.takeIf { it.isNotBlank() } ?: paths?.firstOrNull { it.isNotBlank() } ?: return null
        return PickedFile(chosen, uri)
    }

    /** [request] as the File Manager reads it (`PickerContract`'s action and extras). */
    internal fun call(request: FileRequest): PickerCall {
        val offered = request.offered
        require(request.kind != PickKind.SAVE_FILE || offered.isNotEmpty()) {
            "Orbit's File Manager saves a file only with an extension: give one, or a suggested name with one"
        }
        val extras = buildMap<String, Extra> {
            if (offered.isNotEmpty() && request.kind != PickKind.OPEN_FOLDER) put(FILTERS, Extra.TextArray(offered))
            request.title?.takeIf { it.isNotBlank() }?.let { put(TITLE, Extra.Text(it)) }
            if (request.kind == PickKind.SAVE_FILE) request.suggestedName?.takeIf { it.isNotBlank() }?.let { put(SUGGESTED_NAME, Extra.Text(it)) }
            // The folders the person was in, the last the one to open; one is enough to start in.
            request.startIn?.takeIf { it.isNotBlank() }?.let { put(LOCATION, Extra.TextList(listOf(it))) }
        }
        val action = when (request.kind) {
            PickKind.OPEN_FILE -> OPEN_FILE
            PickKind.SAVE_FILE -> SAVE_FILE
            PickKind.OPEN_FOLDER -> OPEN_FOLDER
        }
        return PickerCall(action, extras = extras)
    }
}
