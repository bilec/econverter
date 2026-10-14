package com.econverter.app

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConverterViewModelTest {
    @Test
    fun kfxIsAvailableAsAnOutputFormat() {
        assertTrue(ConverterViewModel().outputFormats.contains("kfx"))
    }

    @Test
    fun backendControlsFormatsAndPreservesIncompatibleSelections() {
        val vm = ConverterViewModel()
        val uri = Uri.parse("content://books/document")
        vm.inputUris = listOf(uri)
        vm.inputFileNames = listOf("document.docx")
        vm.outputFormat = "mobi"

        vm.selectBackend(ConversionBackend.BOKO)

        assertEquals(setOf("epub", "azw3", "mobi", "kfx"), vm.inputFormats)
        assertEquals(listOf("epub", "kfx", "azw3", "md"), vm.outputFormats)
        assertFalse(vm.isInputSupported("document.docx"))
        assertTrue(vm.isInputSupported("BOOK.KFX"))
        assertEquals(listOf(uri), vm.inputUris)
        assertEquals(listOf("document.docx"), vm.unsupportedInputFileNames)
        assertFalse(vm.canConvert)
        assertEquals("epub", vm.outputFormat)

        vm.selectBackend(ConversionBackend.AUTO)
        assertTrue(vm.isInputSupported("document.docx"))
        assertTrue(vm.isInputSupported("book.kfx"))
        assertTrue(vm.outputFormats.containsAll(listOf("mobi", "kfx", "md")))
        assertTrue(vm.canConvert)

        vm.selectBackend(ConversionBackend.CALIBRE)
        assertFalse(vm.isInputSupported("book.kfx"))
        assertFalse(vm.outputFormats.contains("kfx"))
        assertFalse(vm.outputFormats.contains("md"))
        assertTrue(vm.canConvert)
    }

    @Test
    fun backendPreferenceAndHistoryRestoreCapabilities() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val vm = ConverterViewModel()
        vm.selectBackend(ConversionBackend.BOKO, context)
        val restored = ConverterViewModel()
        restored.loadBackendPreference(context)
        assertEquals(ConversionBackend.BOKO, restored.backend)
        assertFalse(restored.isInputSupported("book.docx"))

        val entry = HistoryEntry("book.docx", "mobi", "Done", 1, listOf(Uri.parse("content://books/1")), listOf("book.docx"), ConversionBackend.CALIBRE)
        restored.selectHistory(entry)
        assertEquals(ConversionBackend.CALIBRE, restored.backend)
        assertEquals("mobi", restored.outputFormat)
        assertTrue(restored.canConvert)
        restored.loadBackendPreference(context)
        assertEquals(ConversionBackend.CALIBRE, restored.backend)
        context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    @Test
    fun clearSelectionRemovesBooksButKeepsHistory() {
        val vm = ConverterViewModel()
        vm.inputUris = listOf(Uri.parse("content://books/1"))
        vm.inputFileNames = listOf("first.epub")
        vm.status = "Selected"
        vm.history.add(HistoryEntry("first.epub", "epub", "Done", 1))

        vm.clearSelection()

        assertFalse(vm.hasSelection)
        assertTrue(vm.inputFileNames.isEmpty())
        assertEquals("", vm.status)
        assertEquals(1, vm.history.size)
    }

    @Test
    fun historyRestoresAllBooksAndOutputFormat() {
        val vm = ConverterViewModel()
        val uris = listOf(Uri.parse("content://books/1"), Uri.parse("content://books/2"))
        val names = listOf("first.epub", "second.epub")
        val entry = HistoryEntry("2 eBooks", "azw3", "Done", 1, uris, names)

        vm.selectHistory(entry)

        assertEquals(uris, vm.inputUris)
        assertEquals(names, vm.inputFileNames)
        assertEquals("azw3", vm.outputFormat)
        assertTrue(vm.isBatch)
    }

    @Test
    fun selectionCannotChangeDuringConversionOrSave() {
        val vm = ConverterViewModel()
        val uri = Uri.parse("content://books/1")
        vm.inputUris = listOf(uri)
        vm.inputFileNames = listOf("first.epub")
        val entry = HistoryEntry("second.epub", "txt", "Done", 1, listOf(Uri.parse("content://books/2")), listOf("second.epub"))

        for (saving in listOf(false, true)) {
            vm.isConverting = !saving
            vm.pendingSave = saving
            vm.clearSelection()
            vm.selectHistory(entry)
            vm.selectBackend(ConversionBackend.BOKO)
            assertEquals(ConversionBackend.AUTO, vm.backend)
            assertEquals(listOf(uri), vm.inputUris)
            assertEquals(listOf("first.epub"), vm.inputFileNames)
            assertEquals("epub", vm.outputFormat)
        }
    }

    @Test
    fun cancellingBeforeWorkerStartsDeletesOnlyItsRunDirectory() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        val original = File(context.cacheDir, "original.epub").apply { writeText("source") }
        val directory = File(context.cacheDir, "cancel-test").apply { mkdirs() }
        File(directory, "partial.kfx").writeText("unfinished")
        val worker = ConversionWorker(context, directory, emptyList(), emptyList(), "epub", emptyList())

        worker.cancel()
        val result = worker.run()

        assertEquals("Cancelled", result.status)
        assertFalse(directory.exists())
        assertEquals("source", original.readText())
        original.delete()
    }
}
