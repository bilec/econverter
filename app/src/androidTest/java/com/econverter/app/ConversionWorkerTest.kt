package com.econverter.app

import android.app.ActivityManager
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConversionSmokeTest

@RunWith(AndroidJUnit4::class)
class ConversionWorkerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetBackend() {
        composeRule.runOnIdle {
            ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java].selectBackend(ConversionBackend.AUTO, context)
        }
    }

    @Test
    @ConversionSmokeTest
    fun emptyQueueShowsOutputSettingsAndDisabledConvertAction() {
        composeRule.onNodeWithText("Your books").assertIsDisplayed()
        composeRule.onNodeWithText("No books selected").assertIsDisplayed()
        composeRule.onNodeWithText("Select eBooks").assertIsDisplayed()
        composeRule.onNodeWithText("Output Format").assertIsDisplayed()
        composeRule.onNodeWithText("Convert").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    @ConversionSmokeTest
    fun selectedQueueListsBooksAndClearsSelection() {
        val names = listOf("A very long book title that should wrap without pushing controls off screen.epub", "Second book.epub")
        composeRule.runOnIdle {
            val vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
            vm.inputUris = names.map { Uri.parse("content://test/$it") }
            vm.inputFileNames = names
        }
        composeRule.onNodeWithText(context.getString(R.string.books_selected, 2)).assertIsDisplayed()
        names.forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("Add eBooks").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Convert").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Clear all").performScrollTo().performClick()
        composeRule.onNodeWithText("No books selected").assertIsDisplayed()
        composeRule.onNodeWithText("Convert").assertIsNotEnabled()
    }

    @Test
    @ConversionSmokeTest
    fun selectedBooksCanBeRemovedBySwipingInEitherDirection() {
        val names = listOf("First.epub", "Second.epub", "Third.epub")
        composeRule.runOnIdle {
            val vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
            vm.inputUris = names.indices.map { Uri.parse("content://test/$it") }
            vm.inputFileNames = names
        }
        composeRule.onNodeWithText(names[0]).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(names[0]).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.books_selected, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(names[1]).performScrollTo().performTouchInput { swipeRight() }
        composeRule.onNodeWithText(names[1]).assertDoesNotExist()
        composeRule.onNodeWithText(names[2]).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(names[2]).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("No books selected").assertIsDisplayed()
        composeRule.onNodeWithText("Convert").assertIsNotEnabled()
    }

    @Test
    @ConversionSmokeTest
    fun convertActionStaysVisibleWhileScrollingOptions() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Auto", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Conversion options").performScrollTo().performClick()
        composeRule.onNodeWithText("Advanced").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove exact text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Convert").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    @ConversionSmokeTest
    fun optionGroupsStartCollapsedAndRetainEditedValues() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Auto", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Conversion options").performScrollTo().performClick()
        composeRule.onNodeWithText("Authors").assertDoesNotExist()
        composeRule.onNodeWithText("Base font size (pt)").assertDoesNotExist()
        composeRule.onNodeWithText("Top").assertDoesNotExist()
        composeRule.onNodeWithText("Extra arguments").assertDoesNotExist()
        composeRule.onNodeWithText("Metadata").performScrollTo().performClick()
        composeRule.onNodeWithText("Authors").performScrollTo().performTextInput("Test author")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.onNodeWithText("Metadata").performScrollTo().performClick()
        composeRule.onNodeWithText("Authors").assertDoesNotExist()
        composeRule.onNodeWithText("Metadata").performScrollTo().performClick()
        composeRule.onNodeWithText("Authors").performScrollTo().assertTextContains("Test author")
        composeRule.onNodeWithText("Metadata").performScrollTo().performClick()
        listOf("Typography" to "Base font size (pt)", "Margins (pt)" to "Top", "Advanced" to "Extra arguments").forEach { (group, field) ->
            composeRule.onNodeWithText(group).performScrollTo().performClick()
            composeRule.onNodeWithText(field).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(group).performScrollTo().performClick()
            composeRule.onNodeWithText(field).assertDoesNotExist()
        }
    }

    @Test
    @ConversionSmokeTest
    fun aboutDialogIsVerticallyCentered() {
        composeRule.onNodeWithContentDescription("About").performClick()
        composeRule.onNodeWithText("About eBook Converter").assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val bounds = Rect()
        automation.rootInActiveWindow.getBoundsInScreen(bounds)
        val height = context.resources.displayMetrics.heightPixels
        assertTrue("Dialog should be near screen center: $bounds", bounds.centerY() in (height * 0.4).toInt()..(height * 0.6).toInt())
    }

    @Test
    @ConversionSmokeTest
    fun backendSettingsControlOutputChoicesAndOptions() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Calibre", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Conversion options").performScrollTo().performClick()
        composeRule.onNodeWithText("Output Profile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("boko", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Backend: boko").assertIsDisplayed()
        composeRule.onNodeWithText("Options (Calibre only)").assertDoesNotExist()
        composeRule.onNodeWithText("Conversion options").assertDoesNotExist()
        composeRule.onNodeWithText("Output Profile").assertDoesNotExist()
        composeRule.onNodeWithText("Extra arguments").assertDoesNotExist()
        composeRule.onNodeWithText("Output Format").performScrollTo().performClick()
        composeRule.onNodeWithText("mobi").assertDoesNotExist()
        composeRule.onNodeWithText("docx").assertDoesNotExist()
        composeRule.onNodeWithText("md").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Calibre", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Output Profile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Output Format").performScrollTo().performClick()
        composeRule.onNodeWithText("kfx").assertDoesNotExist()
        composeRule.onNodeWithText("md").assertDoesNotExist()
        composeRule.onNodeWithText("mobi").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Auto", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Backend: Auto").performScrollTo().assertIsDisplayed()
    }

    @Test
    @ConversionSmokeTest
    fun selectionSkipsUnsupportedBooksAndAppendsSupportedOnes() {
        lateinit var vm: ConverterViewModel
        val original = Uri.parse("file:///books/original.epub")
        val supported = Uri.parse("file:///books/SECOND.EPUB")
        composeRule.runOnIdle {
            vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
            vm.inputUris = listOf(original)
            vm.inputFileNames = listOf("original.epub")
            vm.addSelectedUris(context, listOf(supported, Uri.parse("file:///books/unsupported.zip")))
        }
        composeRule.waitUntil { vm.status.resourceId != R.string.loading_selection }
        composeRule.runOnIdle {
            assertEquals(listOf(original, supported), vm.inputUris)
            assertEquals(listOf("original.epub", "SECOND.EPUB"), vm.inputFileNames)
            assertEquals(StatusMessage(R.string.status_selected_skipped, listOf("2", "1")), vm.status)
            assertTrue(vm.canConvert)
            vm.addSelectedUris(context, listOf(Uri.parse("file:///books/unsupported.zip")))
        }
        composeRule.waitUntil { vm.status.resourceId != R.string.loading_selection }
        composeRule.runOnIdle {
            assertEquals(listOf(original, supported), vm.inputUris)
            assertEquals(StatusMessage(R.string.status_unsupported_selection), vm.status)
        }
    }

    @Test
    @ConversionSmokeTest
    fun recreationPreservesSelectionOptionsAndPendingOutputs() {
        val directory = File(context.filesDir, "conversion-runs/test-${UUID.randomUUID()}").apply { mkdirs() }
        val output = File(directory, "book.epub").apply { writeText("pending output") }
        val source = Uri.parse("content://books/source.epub")
        try {
            composeRule.runOnIdle {
                val vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
                vm.inputUris = listOf(source)
                vm.inputFileNames = listOf("source.epub")
                vm.authors = "Retained author"
                vm.outputFiles = listOf(output)
                vm.internalWorkDirectory = directory
                vm.pendingSave = true
                vm.status = StatusMessage(R.string.status_done, isSuccess = true)
            }

            composeRule.activityRule.scenario.recreate()

            composeRule.onNodeWithText("Save").assertIsDisplayed().assertIsEnabled()
            composeRule.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled()
            composeRule.onNodeWithContentDescription("Settings").assertIsNotEnabled()
            composeRule.runOnIdle {
                val vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
                assertEquals(listOf(source), vm.inputUris)
                assertEquals(listOf("source.epub"), vm.inputFileNames)
                assertEquals("Retained author", vm.authors)
                assertEquals(listOf(output), vm.outputFiles)
                assertEquals("pending output", output.readText())
                assertTrue(vm.pendingSave)
            }
            composeRule.onNodeWithContentDescription("Cancel").performClick()
            composeRule.onNodeWithText("Convert").assertIsEnabled()
            assertFalse(directory.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @ConversionSmokeTest
    fun corruptAndMissingContentUrisDoNotDiscardSuccessfulBooks() = runBlocking<Unit> {
        val directory = File(context.filesDir, "conversion-runs/test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "valid.epub"))
            val corrupt = File(directory, "corrupt.epub").apply { writeText("Not an EPUB") }
            val missing = File(directory, "missing.epub")
            val originals = listOf(source, corrupt).associateWith { it.readBytes() }
            for (backend in listOf(ConversionBackend.AUTO, ConversionBackend.BOKO)) {
                for ((index, books) in listOf(listOf(corrupt), listOf(missing), listOf(source, corrupt, missing)).withIndex()) {
                    val runDirectory = File(directory, "${backend.name}-$index")
                    val uris = books.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
                    val worker = ConversionWorker(context, runDirectory, uris, books.map { it.name }, "epub", emptyList(), backend)

                    val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }

                    if (source in books) {
                        assertTrue(result.status.text(context), result.status.isSuccess)
                        assertEquals(R.string.status_done_partial, result.status.resourceId)
                        assertEquals(listOf("1", "2"), result.status.args.take(2))
                        assertTrue(result.status.args.last().contains("corrupt.epub"))
                        assertTrue(result.status.args.last().contains("missing.epub"))
                        val output = result.files.single()
                        assertEquals("valid.epub", output.name)
                        ZipFile(output).use { assertNotNull(it.getEntry("mimetype")) }
                        assertFalse(File(runDirectory, "1/corrupt.epub").exists())
                        assertFalse(File(runDirectory, "2/missing.epub").exists())
                        books.indices.forEach { assertFalse(File(runDirectory, "$it/source").exists()) }
                    } else {
                        assertTrue(result.status.text(context), result.status.isError)
                        assertTrue(result.files.isEmpty())
                        assertFalse(runDirectory.exists())
                    }
                    originals.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun metadataOptionsPreserveQuotedValuesAndOnlyApplyEpubVersionToEpub() {
        val directory = File(context.filesDir, "conversion-runs/test-${UUID.randomUUID()}").apply { mkdirs() }
        lateinit var vm: ConverterViewModel
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            for (format in listOf("epub", "txt")) {
                composeRule.runOnIdle {
                    vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
                    vm.inputUris = listOf(uri)
                    vm.inputFileNames = listOf(source.name)
                    vm.outputFormat = format
                    vm.epubVersion = if (format == "epub") "3" else "invalid-for-epub"
                    vm.title = "Form title"
                    vm.authors = "Option Author"
                    vm.extraArgs = "--title \"Quoted title & notes\" --publisher \u201cQuoted publisher\u201d"
                    vm.convert(context)
                }
                composeRule.waitUntil(90_000) { !vm.isConverting }
                composeRule.runOnIdle {
                    assertTrue(vm.status.text(context), vm.status.isSuccess)
                    assertTrue(vm.pendingSave)
                    val output = vm.outputFiles.single()
                    if (format == "epub") {
                        ZipFile(output).use { zip ->
                            val opf = zip.entries().asSequence().first { it.name.endsWith(".opf") }
                            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                            val document = zip.getInputStream(opf).use { factory.newDocumentBuilder().parse(it) }
                            assertEquals("3.0", document.documentElement.getAttribute("version"))
                            val namespace = "http://purl.org/dc/elements/1.1/"
                            assertEquals("Quoted title & notes", document.getElementsByTagNameNS(namespace, "title").item(0).textContent)
                            assertEquals("Quoted publisher", document.getElementsByTagNameNS(namespace, "publisher").item(0).textContent)
                            assertEquals("Option Author", document.getElementsByTagNameNS(namespace, "creator").item(0).textContent)
                        }
                    } else {
                        assertTrue(output.readText().contains("Book text."))
                    }
                    vm.discardPendingSave()
                }
            }
        } finally {
            composeRule.runOnIdle { ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java].discardPendingSave() }
            directory.deleteRecursively()
        }
    }

    @Test
    fun batchOptionsPreserveIndividualTitlesAndApplySharedAuthors() {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        lateinit var vm: ConverterViewModel
        try {
            val sources = listOf("First", "Second").map { File(directory, "$it.txt").apply { writeText("Book text.") } }
            composeRule.runOnIdle {
                vm = ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java]
                vm.inputUris = sources.map(Uri::fromFile)
                vm.inputFileNames = sources.map { it.name }
                vm.title = "Must not replace individual titles"
                vm.authors = "Batch Author"
                vm.convert(context)
            }
            composeRule.waitUntil(90_000) { !vm.isConverting }
            composeRule.runOnIdle {
                assertTrue(vm.status.text(context), vm.status.isSuccess)
                assertEquals(2, vm.outputFiles.size)
                for ((source, output) in sources.zip(vm.outputFiles)) {
                    ZipFile(output).use { zip ->
                        val opf = zip.entries().asSequence().first { it.name.endsWith(".opf") }
                        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                        val document = zip.getInputStream(opf).use { factory.newDocumentBuilder().parse(it) }
                        val namespace = "http://purl.org/dc/elements/1.1/"
                        assertEquals(source.nameWithoutExtension, document.getElementsByTagNameNS(namespace, "title").item(0).textContent)
                        assertEquals("Batch Author", document.getElementsByTagNameNS(namespace, "creator").item(0).textContent)
                    }
                }
            }
        } finally {
            composeRule.runOnIdle { ViewModelProvider(composeRule.activity)[ConverterViewModel::class.java].discardPendingSave() }
            directory.deleteRecursively()
        }
    }

    @Test
    @ConversionSmokeTest
    fun sameFormatConversionKeepsOutputsNamedLikeTemporaryFiles() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            for (name in listOf("input.epub", "intermediate.epub")) {
                val source = makeEpub(File(directory, name))
                val worker = ConversionWorker(context, File(directory, "run-$name"), listOf(Uri.fromFile(source)), listOf(name), "epub", emptyList())
                val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
                assertTrue(result.status.text(context), result.status.isSuccess)
                assertTrue("Converted $name must still exist", result.files.single().isFile)
                assertTrue(source.exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun linearizeTablesPreservesCellText() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val worker = ConversionWorker(context, File(directory, "run"), listOf(Uri.fromFile(source)), listOf(source.name), "epub", listOf("--linearize-tables"))
            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
            assertTrue(result.status.text(context), result.status.isSuccess)
            var foundCell = false
            ZipFile(result.files.single()).use { zip ->
                for (entry in zip.entries().asSequence().filter { it.name.endsWith(".xhtml") || it.name.endsWith(".html") }) {
                    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    val document = zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
                    assertEquals(0, document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "table").length)
                    if (document.documentElement.textContent.contains("Cell marker")) foundCell = true
                }
            }
            assertTrue("Table text must survive conversion", foundCell)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @ConversionSmokeTest
    fun kfxConversionLoadsNativeBackend() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val worker = ConversionWorker(context, File(directory, "run"), listOf(Uri.fromFile(source)), listOf(source.name), "kfx", emptyList())
            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
            assertTrue(result.status.text(context), result.status.isSuccess)
            assertEquals("kfx", result.files.single().extension)
            assertTrue(result.files.single().length() > 0)
            assertFalse(File(directory, "run/0/intermediate.epub").exists())
            assertTrue(source.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun directBokoExportsAllAdvertisedFormatsWithoutCalibreScratch() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "source.epub"))
            for (format in ConversionBackend.BOKO.outputFormats) {
                val runDirectory = File(directory, format)
                val worker = ConversionWorker(context, runDirectory, listOf(Uri.fromFile(source)), listOf(source.name), format, emptyList(), backend = ConversionBackend.BOKO)
                val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
                assertTrue(result.status.text(context), result.status.isSuccess)
                assertTrue(result.files.single().length() > 0)
                assertFalse("Direct boko must not initialize Calibre scratch storage", File(runDirectory, "tmp").exists())
                if (format == "md") assertTrue(result.files.single().readText().contains("Book text."))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun autoCombinesBackendsForKfxInputAndOutput() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(directory, "source.txt").apply { writeText("Chapter 1\n\nBook text.") }
            val toKfx = ConversionWorker(context, File(directory, "kfx"), listOf(Uri.fromFile(source)), listOf(source.name), "kfx", listOf("--title", "Auto Book"), backend = ConversionBackend.AUTO)
            val kfx = withTimeout(90_000) { withContext(Dispatchers.Main) { toKfx.run() } }
            assertTrue(kfx.status.text(context), kfx.status.isSuccess)
            val toMobi = ConversionWorker(context, File(directory, "mobi"), listOf(Uri.fromFile(kfx.files.single())), listOf("source.kfx"), "mobi", listOf("--title", "Updated title"), backend = ConversionBackend.AUTO)
            val mobi = withTimeout(90_000) { withContext(Dispatchers.Main) { toMobi.run() } }
            assertTrue(mobi.status.text(context), mobi.status.isSuccess)
            val toEpub = ConversionWorker(context, File(directory, "epub"), listOf(Uri.fromFile(mobi.files.single())), listOf("source.mobi"), "epub", emptyList(), backend = ConversionBackend.BOKO)
            val epub = withTimeout(90_000) { withContext(Dispatchers.Main) { toEpub.run() } }
            assertTrue(epub.status.text(context), epub.status.isSuccess)
            ZipFile(epub.files.single()).use { zip ->
                val opf = zip.entries().asSequence().first { it.name.endsWith(".opf") }
                val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                val document = zip.getInputStream(opf).use { factory.newDocumentBuilder().parse(it) }
                assertEquals("Updated title", document.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title").item(0).textContent)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun autoPreservesFilenameDerivedTitle() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(directory, "Original.txt").apply { writeText("Book text.") }
            val worker = ConversionWorker(context, File(directory, "run"), listOf(Uri.fromFile(source)), listOf(source.name), "epub", emptyList())
            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
            assertTrue(result.status.text(context), result.status.isSuccess)
            ZipFile(result.files.single()).use { zip ->
                val opf = zip.entries().asSequence().first { it.name.endsWith(".opf") }
                val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                val document = zip.getInputStream(opf).use { factory.newDocumentBuilder().parse(it) }
                assertEquals("Original", document.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title").item(0).textContent)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pdfInputKeepsTitleOutlineAndCover() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makePdf(File(directory, "source.pdf"))
            val worker = ConversionWorker(context, File(directory, "run"), listOf(Uri.fromFile(source)), listOf(source.name), "epub", emptyList())
            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
            assertTrue(result.status.text(context), result.status.isSuccess)
            ZipFile(result.files.single()).use { zip ->
                fun text(suffix: String) = zip.getInputStream(zip.entries().asSequence().first { it.name.endsWith(suffix) }).reader().readText()
                val opf = text(".opf")
                assertTrue(opf, opf.contains("PDF Title</dc:title>"))
                assertTrue(opf, opf.contains("name=\"cover\""))
                assertTrue(text(".ncx").contains("Chapter 3"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun workerRejectsUnsupportedBackendRequests() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val runDirectory = File(directory, "run")
            val uris = listOf(Uri.fromFile(source))
            for (worker in listOf(
                ConversionWorker(context, runDirectory, uris, listOf("source.docx"), "epub", emptyList(), backend = ConversionBackend.BOKO),
                ConversionWorker(context, runDirectory, uris, listOf("source.epub"), "kfx", emptyList(), backend = ConversionBackend.CALIBRE),
                ConversionWorker(context, runDirectory, uris, listOf("source.epub"), "epub", listOf("--title", "Unsupported"), backend = ConversionBackend.BOKO),
            )) {
                val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
                assertTrue(result.status.text(context), result.status.isError)
                assertTrue(result.files.isEmpty())
                assertFalse(runDirectory.exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @ConversionSmokeTest
    fun workerDeathDiscardsOutputsPreservesOriginalsAndAllowsAnotherRun() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val largeSource = makeEpub(File(directory, "large.epub"), 20_000)
            val originals = listOf(source, largeSource).associateWith { it.readBytes() }
            val runDirectory = File(directory, "run")
            var killedPid: Int? = null
            val worker = ConversionWorker(
                context,
                runDirectory,
                listOf(Uri.fromFile(source), Uri.fromFile(largeSource)),
                listOf(source.name, largeSource.name),
                "epub",
                emptyList(),
            ) { progress ->
                if (progress.resourceId == R.string.status_converting_progress && progress.args == listOf("1", "2") && killedPid == null) {
                    assertTrue("First output must exist before worker death", File(runDirectory, "0/source.epub").isFile)
                    val process = context.getSystemService(ActivityManager::class.java).runningAppProcesses.single { it.processName == "${context.packageName}:conversion" }
                    assertNotEquals("Never kill the test process", Process.myPid(), process.pid)
                    killedPid = process.pid
                    Process.killProcess(process.pid)
                }
            }

            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }

            assertNotNull("Worker process must have been killed", killedPid)
            assertTrue(result.status.text(context), result.status.isError)
            assertFalse(result.status.isSuccess)
            assertTrue(result.files.isEmpty())
            assertFalse(runDirectory.exists())
            originals.forEach { (file, bytes) -> assertArrayEquals("Original must be unchanged: ${file.name}", bytes, file.readBytes()) }

            val next = ConversionWorker(context, File(directory, "next"), listOf(Uri.fromFile(source)), listOf(source.name), "epub", emptyList())
            val nextResult = withTimeout(90_000) { withContext(Dispatchers.Main) { next.run() } }
            assertTrue(nextResult.status.text(context), nextResult.status.isSuccess)
            assertTrue(nextResult.files.single().isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @ConversionSmokeTest
    fun cancellingBatchDiscardsCompletedOutputsAndAllowsAnotherRun() = runBlocking<Unit> {
        val directory = File(context.filesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        val existingScratch = engineScratchDirectories()
        try {
            val source = makeEpub(File(directory, "source.epub"))
            val largeSource = makeEpub(File(directory, "large.epub"), 20_000)
            val runDirectory = File(directory, "run")
            lateinit var worker: ConversionWorker
            worker = ConversionWorker(
                context,
                runDirectory,
                listOf(Uri.fromFile(source), Uri.fromFile(largeSource)),
                listOf(source.name, largeSource.name),
                "epub",
                emptyList(),
            ) { progress -> if (progress.resourceId == R.string.status_converting_progress && progress.args == listOf("1", "2")) worker.cancel() }
            val result = withTimeout(90_000) { withContext(Dispatchers.Main) { worker.run() } }
            assertEquals(StatusMessage(R.string.status_cancelled), result.status)
            assertTrue(result.files.isEmpty())
            assertFalse(runDirectory.exists())
            assertTrue(source.exists())
            assertTrue(largeSource.exists())
            assertEquals("Engine scratch files must be cleaned up", emptySet<String>(), engineScratchDirectories() - existingScratch)

            val next = ConversionWorker(context, File(directory, "next"), listOf(Uri.fromFile(source)), listOf(source.name), "epub", emptyList())
            val nextResult = withTimeout(90_000) { withContext(Dispatchers.Main) { next.run() } }
            assertTrue(nextResult.status.text(context), nextResult.status.isSuccess)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun engineScratchDirectories(): Set<String> = context.cacheDir.walkTopDown()
        .filter { it.isDirectory && it.name.contains("_tmp_") }
        .map { it.absolutePath }.toSet()

    private fun makePdf(file: File): File {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val canvas = Python.getInstance().getModule("reportlab.pdfgen.canvas").callAttr("Canvas", file.absolutePath)
        canvas.callAttr("setTitle", "PDF Title")
        for (number in 1..3) {
            canvas.callAttr("bookmarkPage", "chapter$number")
            canvas.callAttr("addOutlineEntry", "Chapter $number", "chapter$number")
            canvas.callAttr("drawString", 72, 720, "Chapter $number text")
            canvas.callAttr("showPage")
        }
        canvas.callAttr("save")
        return file
    }

    private fun makeEpub(file: File, paragraphs: Int = 1): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            val entries = mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test Book</dc:title><dc:language>en</dc:language><dc:identifier id="uid">test-book-001</dc:identifier><dc:creator>Test Author</dc:creator></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
                "chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><h1>Chapter</h1><table><tr><td>Cell marker</td></tr></table>${"<p>Book text.</p>".repeat(paragraphs)}</body></html>""",
            )
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
