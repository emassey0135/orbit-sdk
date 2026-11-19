package dev.emassey0135.orbit.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** What a [FileRequest] asks the File Manager to choose. */
public enum class PickKind {
    /** A file that is there already, to read. */
    OPEN_FILE,

    /** Where to write a file: a folder and a name the person types, which need not exist yet. Nothing is made. */
    SAVE_FILE,

    /** A folder that is there already, the one the person is in when they choose. */
    OPEN_FOLDER,
}

/**
 * What to ask Orbit's File Manager for, through [FilePicker].
 */
public data class FileRequest(
    /** Opening a file, saving one, or choosing a folder. */
    val kind: PickKind,
    /** What the File Manager calls its screen; its own words for [kind] when null. */
    val title: String? = null,
    /**
     * The extensions offered, without their dots ("json", "csv"). Opening, only files with one of
     * them are listed, and every file when there are none. Saving, the first is added to a name
     * typed without one; with none, [suggestedName]'s own extension is used.
     */
    val extensions: List<String> = emptyList(),
    /** The name a save starts with. */
    val suggestedName: String? = null,
    /** The folder the File Manager opens in, as an absolute path; its own choice when null. */
    val startIn: String? = null,
) {
    /**
     * [extensions] as the File Manager takes them: trimmed, without a leading dot, lowercase, each
     * once; for a save without any, [suggestedName]'s own.
     */
    public val offered: List<String>
        get() {
            val given = extensions.map { it.trim().removePrefix(".").lowercase() }.filter { it.isNotEmpty() }.distinct()
            if (given.isNotEmpty() || kind != PickKind.SAVE_FILE) return given
            return listOfNotNull(suggestedName?.let(::extensionOf))
        }
}

/**
 * What the person chose: its absolute [path], and the [uri] the File Manager granted this app, where
 * it gives one. Read and write it with [openInput] and [openOutput], which use the [uri] where there
 * is one and the path otherwise.
 */
public data class PickedFile(
    /** The file or folder chosen, as an absolute path. */
    val path: String,
    /**
     * A `content:` Uri for it that this app may read and write, granted by the File Manager for a
     * file and for a folder and everything in it; null where its answer carried none.
     */
    val uri: Uri?,
) {
    /** The last part of [path]: a file's name with its extension, or a folder's. */
    val name: String get() = path.trimEnd('/').substringAfterLast('/')

    /** The folder [path] is in, to open the next request in ([FileRequest.startIn]). */
    val parent: String? get() = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }

    /**
     * Opens the file chosen to read, through [uri] where there is one and the path otherwise. Call it
     * off the main thread.
     *
     * @throws java.io.IOException if it cannot be read.
     */
    public fun openInput(context: Context): InputStream =
        uri?.let { context.contentResolver.openInputStream(it) } ?: File(path).inputStream()

    /**
     * Opens the file chosen to write, made if it is not there and emptied if it is, through [uri]
     * where there is one and the path otherwise. Call it off the main thread.
     *
     * @throws java.io.IOException if it cannot be written.
     */
    public fun openOutput(context: Context): OutputStream =
        uri?.let { context.contentResolver.openOutputStream(it, "wt") } ?: File(path).outputStream()
}

/** A name's extension without its dot, or null for a name without one (".profile" has none). */
internal fun extensionOf(name: String): String? {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.length - 1) return null
    return name.substring(dot + 1).lowercase()
}

/**
 * An intent to the File Manager as plain values, so how a request is put can be tested on the JVM,
 * where `Intent` is a stub.
 */
internal data class PickerCall(
    val action: String,
    /** The folder to start in, as a path, which goes as the intent's data. */
    val folder: String? = null,
    val categories: List<String> = emptyList(),
    val flags: Int = 0,
    val extras: Map<String, Extra> = emptyMap(),
)

/** One extra's value, by the type the File Manager reads it as. */
internal sealed interface Extra {
    data class Text(val value: String) : Extra
    data class TextArray(val value: List<String>) : Extra
    data class TextList(val value: List<String>) : Extra
    data class Number(val value: Int) : Extra
}

/** [extras] put on this intent, each as its type. */
internal fun android.content.Intent.putAll(extras: Map<String, Extra>): android.content.Intent = apply {
    extras.forEach { (key, extra) ->
        when (extra) {
            is Extra.Text -> putExtra(key, extra.value)
            is Extra.TextArray -> putExtra(key, extra.value.toTypedArray())
            is Extra.TextList -> putStringArrayListExtra(key, ArrayList(extra.value))
            is Extra.Number -> putExtra(key, extra.value)
        }
    }
}
