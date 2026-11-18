package dev.emassey0135.orbit

import dev.emassey0135.orbit.files.Extra
import dev.emassey0135.orbit.files.FilePicker
import dev.emassey0135.orbit.files.FileRequest
import dev.emassey0135.orbit.files.PickKind
import dev.emassey0135.orbit.files.PickedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePickerTest {
    @Test fun openingAFileAsksForItsExtensionsWithoutDotsAndInLowercase() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FILE, title = "Import", extensions = listOf(".JSON", "csv", "json", " ")))
        assertEquals("com.orbit.file_picker.OPEN_FILE", call.action)
        assertEquals(Extra.TextArray(listOf("json", "csv")), call.extras["extra_file_filters"])
        assertEquals(Extra.Text("Import"), call.extras["extra_title"])
        assertNull(call.extras["extra_suggested_name"])
    }

    @Test fun openingAnyFileOffersNoFilter() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FILE))
        assertEquals(emptyMap<String, Extra>(), call.extras)
    }

    @Test fun savingTakesItsExtensionFromTheSuggestedNameWhenNoneIsGiven() {
        val call = FilePicker.call(FileRequest(PickKind.SAVE_FILE, suggestedName = "lumenna-2026-11-17.lumbak"))
        assertEquals("com.orbit.file_picker.SAVE_FILE", call.action)
        assertEquals(Extra.TextArray(listOf("lumbak")), call.extras["extra_file_filters"])
        assertEquals(Extra.Text("lumenna-2026-11-17.lumbak"), call.extras["extra_suggested_name"])
    }

    @Test(expected = IllegalArgumentException::class) fun savingWithNoExtensionAnywhereIsRefusedBeforeTheFileManagerRefusesIt() {
        FilePicker.call(FileRequest(PickKind.SAVE_FILE, suggestedName = "notes"))
    }

    @Test fun aFolderStartsWhereItIsToldAndOffersNoFilter() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FOLDER, extensions = listOf("txt"), startIn = "/storage/emulated/0/Documents"))
        assertEquals("com.orbit.file_picker.OPEN_FOLDER", call.action)
        assertNull(call.extras["extra_file_filters"])
        assertEquals(Extra.TextList(listOf("/storage/emulated/0/Documents")), call.extras["extra_last_known_location"])
    }

    @Test fun theAnswerIsItsPathOrElseTheFirstOfItsList() {
        assertEquals(PickedFile("/storage/emulated/0/a.json", null), FilePicker.picked("/storage/emulated/0/a.json", null, null))
        assertEquals(PickedFile("/storage/emulated/0/b.json", null), FilePicker.picked(null, listOf("", "/storage/emulated/0/b.json"), null))
        assertNull(FilePicker.picked(" ", emptyList(), null))
    }

    @Test fun aCancelledPickIsNothing() {
        assertNull(FilePicker.picked(0, null))
    }

    @Test fun aPickedFileNamesItselfAndItsFolder() {
        val file = PickedFile("/storage/emulated/0/Documents/tasks.csv", null)
        assertEquals("tasks.csv", file.name)
        assertEquals("/storage/emulated/0/Documents", file.parent)
        assertEquals("Documents", PickedFile("/storage/emulated/0/Documents/", null).name)
    }
}
