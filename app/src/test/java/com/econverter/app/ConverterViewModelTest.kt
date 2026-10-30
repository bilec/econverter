package com.econverter.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConverterViewModelTest {
    @Test
    fun startupCleanupRemovesOldRunsButPreservesNewRunsAndOriginals() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        val runs = File(context.filesDir, "conversion-runs").apply { mkdirs() }
        val stale = File(runs, "stale-${UUID.randomUUID()}").apply { mkdirs() }
        val recent = File(runs, "recent-${UUID.randomUUID()}")
        val original = File(context.filesDir, "original-${UUID.randomUUID()}.epub").apply { writeText("original book") }
        try {
            File(stale, "source").mkdirs()
            File(stale, "source/input.epub").writeText("temporary input")
            File(stale, "partial.kfx").writeText("unfinished output")
            assertTrue(stale.setLastModified(1L))
            val vm = ConverterViewModel()
            assertTrue(recent.mkdirs())
            val currentOutput = File(recent, "converted.epub").apply { writeText("current output") }
            assertTrue(recent.setLastModified(System.currentTimeMillis() + 60_000L))

            vm.purgeStaleTempFiles(context)
            vm.viewModelScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }

            assertFalse(stale.exists())
            assertEquals("current output", currentOutput.readText())
            assertEquals("original book", original.readText())
        } finally {
            stale.deleteRecursively()
            recent.deleteRecursively()
            original.delete()
        }
    }

    @Test
    fun statusAndHistoryResolveUsingTheCurrentLanguage() {
        val context = RuntimeEnvironment.getApplication()
        val vm = ConverterViewModel()
        vm.discardPendingSave()
        val entry = HistoryEntry("epub", vm.status, 1)

        for ((language, expected) in mapOf("en" to "Save cancelled", "cs" to "Ukládání zrušeno", "sk" to "Ukladanie zrušené", "pl" to "Anulowano zapis")) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
            assertEquals(expected, vm.status.text(localized))
            assertEquals(expected, entry.status.text(localized))
        }
    }

    @Test
    fun conversionProgressUsesWorkerUpdatesAndCompletesBeforeSaving() {
        val vm = ConverterViewModel()
        assertNull(vm.conversionProgress)
        vm.isConverting = true
        vm.status = StatusMessage(R.string.status_converting)
        assertNull(vm.conversionProgress)
        for (progress in listOf(0f, 0.5f)) {
            vm.status = StatusMessage(R.string.status_converting_progress, progress = progress)
            assertEquals(progress, vm.conversionProgress)
        }
        vm.isConverting = false
        vm.status = StatusMessage(R.string.status_done, isSuccess = true)
        vm.pendingSave = true
        assertEquals(1f, vm.conversionProgress)
        assertFalse(vm.shouldAutoDismissStatus)
        vm.isSaving = true
        assertNull(vm.conversionProgress)
        assertFalse(vm.shouldAutoDismissStatus)
        vm.isSaving = false
        vm.discardPendingSave()
        assertNull(vm.conversionProgress)
        assertTrue(vm.shouldAutoDismissStatus)
        vm.status = StatusMessage(R.string.status_error, listOf("boom"), isError = true)
        assertFalse(vm.shouldAutoDismissStatus)
    }

    @Test
    fun workerStatusTransportPreservesArgumentsAndClassification() {
        val context = RuntimeEnvironment.getApplication()
        for (message in listOf(
            StatusMessage(R.string.status_converting_progress, listOf("1", "3"), progress = 1f / 3),
            StatusMessage(R.string.status_error, listOf("detail & #section\nsecond line"), isError = true),
            StatusMessage(R.string.status_done, isSuccess = true),
        )) {
            val restored = StatusMessage.fromBundle(message.toBundle())
            assertEquals(message, restored)
            assertEquals(message.text(context), restored.text(context))
        }
        assertTrue(StatusMessage.fromBundle(android.os.Bundle()).isError)
    }

    @Test
    fun bugReportMatchesTemplateAndPreservesEncodedDiagnostics() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val vm = ConverterViewModel()
        vm.inputFileNames = listOf("book & notes.epub")
        vm.outputFormat = "kfx"
        vm.status = StatusMessage(R.string.status_error, listOf("invalid #section & title"), isError = true)

        for (extraArgs in listOf("", "--title \"A&B\"")) {
            vm.extraArgs = extraArgs

            reportOnGitHub(activity, vm)

            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            val uri = requireNotNull(intent.data)
            assertEquals("https", uri.scheme)
            assertEquals("github.com", uri.host)
            assertEquals("/bilec/econverter/issues/new", uri.path)
            assertEquals("bug", uri.getQueryParameter("labels"))
            assertEquals("Conversion failed: epub \u2192 kfx", uri.getQueryParameter("title"))
            assertEquals(
                listOf(
                    "## Bug Report",
                    "",
                    "- **Steps to reproduce:**",
                    "- **Expected result:**",
                    "- **Error:** ${vm.status.text(activity)}",
                    "- **Input file:** ${vm.inputFileName}",
                    "- **Output format:** ${vm.outputFormat}",
                    "- **Backend:** ${vm.backend.label}",
                    "- **Output profile:** ${vm.outputProfile}",
                    "- **Device:** ${Build.MANUFACTURER} ${Build.MODEL}",
                    "- **Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "- **Extra args:** $extraArgs",
                    "",
                    "Remove personal information before submitting.",
                ).joinToString("\n", postfix = "\n"),
                uri.getQueryParameter("body"),
            )
        }
    }

    @Test
    @Config(sdk = [25])
    fun singleAndBatchSavesNumberDuplicatesBeforeEveryExtensionWithoutOverwriting() {
        val context = RuntimeEnvironment.getApplication()
        val sourceDirectory = File(context.cacheDir, "save-test-source").apply { mkdirs() }
        val destination = File(context.cacheDir, "save-test-destination").apply { mkdirs() }
        val authority = "com.econverter.app.save-test"
        val provider = SaveTestDocumentsProvider(destination, authority, shadowOf(context.contentResolver))
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                this.authority = authority
                exported = true
                grantUriPermissions = true
                readPermission = Manifest.permission.MANAGE_DOCUMENTS
                writePermission = Manifest.permission.MANAGE_DOCUMENTS
            },
        )
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")
        val vm = ConverterViewModel()

        try {
            for (extension in vm.outputFormats + listOf("unknown", "EPUB", "part.epub", "")) {
                destination.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
                val sourceName = if (extension.isEmpty()) "book" else "book.$extension"
                val source = File(sourceDirectory, sourceName).apply { writeText("converted") }
                val baseName = source.nameWithoutExtension
                val suffix = if (source.extension.isEmpty()) "" else ".${source.extension}"
                File(destination, source.name).writeText("original")
                File(destination, "$baseName(1)$suffix").writeText("previous")

                assertTrue("Single save failed for '$extension'", vm.saveToDirectoryUri(context, treeUri, listOf(source)))
                assertEquals("converted", File(destination, "$baseName(2)$suffix").readText())

                assertTrue(vm.saveToDirectoryUri(context, treeUri, listOf(source, source)))
                assertEquals("converted", File(destination, "$baseName(3)$suffix").readText())
                assertEquals("converted", File(destination, "$baseName(4)$suffix").readText())
                assertEquals("original", File(destination, source.name).readText())
                assertEquals("previous", File(destination, "$baseName(1)$suffix").readText())
            }
        } finally {
            sourceDirectory.deleteRecursively()
            destination.deleteRecursively()
        }
    }

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

        val entry = HistoryEntry("mobi", StatusMessage(R.string.status_done, isSuccess = true), 1, listOf(Uri.parse("content://books/1")), listOf("book.docx"), ConversionBackend.CALIBRE)
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
        vm.status = StatusMessage(R.string.loading_selection)
        vm.history.add(HistoryEntry("epub", StatusMessage(R.string.status_done, isSuccess = true), 1))

        vm.clearSelection()

        assertFalse(vm.hasSelection)
        assertTrue(vm.inputFileNames.isEmpty())
        assertEquals(StatusMessage(), vm.status)
        assertEquals(1, vm.history.size)
    }

    @Test
    fun removingOneBookKeepsUriAndNamePairsAndHistory() {
        val vm = ConverterViewModel()
        val uris = (1..3).map { Uri.parse("content://books/$it") }
        vm.inputUris = uris
        vm.inputFileNames = listOf("same.epub", "same.epub", "last.epub")
        vm.history.add(HistoryEntry("epub", StatusMessage(R.string.status_done, isSuccess = true), 1))
        vm.status = StatusMessage(R.string.loading_selection)

        vm.removeSelectedUri(uris[1])

        assertEquals(listOf(uris[0], uris[2]), vm.inputUris)
        assertEquals(listOf("same.epub", "last.epub"), vm.inputFileNames)
        assertEquals(StatusMessage(), vm.status)
        assertEquals(1, vm.history.size)
        vm.removeSelectedUri(uris[1])
        assertEquals(2, vm.inputUris.size)
        vm.removeSelectedUri(uris[0])
        assertFalse(vm.isBatch)
        vm.removeSelectedUri(uris[2])
        assertFalse(vm.hasSelection)
        assertFalse(vm.canConvert)
        assertTrue(vm.inputFileNames.isEmpty())
    }

    @Test
    fun historyRestoresAllBooksAndOutputFormat() {
        val vm = ConverterViewModel()
        val uris = listOf(Uri.parse("content://books/1"), Uri.parse("content://books/2"))
        val names = listOf("first.epub", "second.epub")
        val entry = HistoryEntry("azw3", StatusMessage(R.string.status_done, isSuccess = true), 1, uris, names)

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
        val entry = HistoryEntry("txt", StatusMessage(R.string.status_done, isSuccess = true), 1, listOf(Uri.parse("content://books/2")), listOf("second.epub"))

        for (busyState in 0..2) {
            vm.isConverting = busyState == 0
            vm.isSaving = busyState == 1
            vm.pendingSave = busyState == 2
            vm.clearSelection()
            vm.removeSelectedUri(uri)
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

        assertEquals(StatusMessage(R.string.status_cancelled), result.status)
        assertFalse(directory.exists())
        assertEquals("source", original.readText())
        original.delete()
    }

    @Test
    fun invalidSelectionsCannotStartConversion() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://books/1")
        for ((uris, names) in listOf(
            listOf(uri) to emptyList(),
            listOf(uri, uri) to listOf("book.epub"),
            listOf(uri) to listOf("book.unknown"),
            listOf(uri) to listOf("book"),
        )) {
            val vm = ConverterViewModel()
            vm.inputUris = uris
            vm.inputFileNames = names

            assertFalse(vm.canConvert)
            vm.convert(context)

            assertTrue(vm.status.isError)
            assertFalse(vm.isConverting)
            assertNull(vm.internalWorkDirectory)
            assertTrue(vm.outputFiles.isEmpty())
        }
    }

    @Test
    fun addingBooksWhileBusyDoesNotChangeSelectionOrStatus() {
        val context = RuntimeEnvironment.getApplication()
        val vm = ConverterViewModel()
        val original = Uri.parse("content://books/original.epub")
        vm.inputUris = listOf(original)
        vm.inputFileNames = listOf("original.epub")
        val status = StatusMessage(R.string.status_converting)
        vm.status = status
        for (busyState in 0..2) {
            vm.isConverting = busyState == 0
            vm.isSaving = busyState == 1
            vm.pendingSave = busyState == 2

            vm.addSelectedUris(context, listOf(Uri.parse("content://books/new.epub")))

            assertEquals(listOf(original), vm.inputUris)
            assertEquals(listOf("original.epub"), vm.inputFileNames)
            assertEquals(status, vm.status)
        }
    }

    @Test
    @Config(sdk = [25])
    fun rejectedDocumentCreationAndDeniedWritesPreserveExistingFiles() {
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "save-failures-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "source/book.epub").apply {
            parentFile!!.mkdirs()
            writeText("converted")
        }
        val destination = File(directory, "destination").apply { mkdirs() }
        val original = File(destination, "book.epub").apply { writeText("original") }
        val authority = "com.econverter.app.save-failure-test"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")
        try {
            for (denyWrites in listOf(false, true)) {
                val provider = SaveTestDocumentsProvider(destination, authority, shadowOf(context.contentResolver), rejectedNames = if (denyWrites) emptySet() else setOf("book(1).epub"), denyWrites = denyWrites)
                provider.attachInfo(
                    context,
                    ProviderInfo().apply {
                        this.authority = authority
                        exported = true
                        grantUriPermissions = true
                        readPermission = Manifest.permission.MANAGE_DOCUMENTS
                        writePermission = Manifest.permission.MANAGE_DOCUMENTS
                    },
                )
                ShadowContentResolver.registerProviderInternal(authority, provider)
                val vm = ConverterViewModel()

                assertFalse(vm.saveToDirectoryUri(context, treeUri, emptyList()))
                if (denyWrites) {
                    assertThrows(SecurityException::class.java) { vm.saveToDirectoryUri(context, treeUri, listOf(source)) }
                } else {
                    assertFalse(vm.saveToDirectoryUri(context, treeUri, listOf(source)))
                    assertFalse(File(destination, "book(1).epub").exists())
                }
                assertEquals("original", original.readText())
                assertEquals("converted", source.readText())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @Config(sdk = [25], shadows = [UnavailableOutputStreamResolver::class])
    fun batchSaveContinuesPastRejectedDocumentsAndNullStreams() {
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "partial-save-${UUID.randomUUID()}").apply { mkdirs() }
        val blocked = File(directory, "blocked.epub").apply { writeText("blocked output") }
        val saved = File(directory, "saved.epub").apply { writeText("saved output") }
        val authority = "com.econverter.app.partial-save-test"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")
        try {
            for (nullStreams in listOf(false, true)) {
                val destination = File(directory, "destination-$nullStreams").apply { mkdirs() }
                val original = File(destination, "blocked.epub").apply { writeText("original") }
                val failedNames = setOf("blocked(1).epub")
                val provider = SaveTestDocumentsProvider(
                    destination,
                    authority,
                    shadowOf(context.contentResolver),
                    rejectedNames = if (nullStreams) emptySet() else failedNames,
                )
                provider.attachInfo(
                    context,
                    ProviderInfo().apply {
                        this.authority = authority
                        exported = true
                        grantUriPermissions = true
                        readPermission = Manifest.permission.MANAGE_DOCUMENTS
                        writePermission = Manifest.permission.MANAGE_DOCUMENTS
                    },
                )
                ShadowContentResolver.registerProviderInternal(authority, provider)
                Shadow.extract<UnavailableOutputStreamResolver>(context.contentResolver).unavailableUri = if (nullStreams) DocumentsContract.buildDocumentUriUsingTree(treeUri, "blocked(1).epub") else null
                val vm = ConverterViewModel()

                assertFalse(vm.saveToDirectoryUri(context, treeUri, listOf(blocked)))
                if (nullStreams) assertTrue(File(destination, "blocked(1).epub").delete())
                assertTrue(vm.saveToDirectoryUri(context, treeUri, listOf(blocked, saved)))

                assertEquals("saved output", File(destination, "saved.epub").readText())
                assertEquals("original", original.readText())
                assertEquals("blocked output", blocked.readText())
                assertEquals("saved output", saved.readText())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun shareIntentExposesSingleAndBatchOutputsAndPreservesSharedFilesAcrossCleanup() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        val runs = File(context.filesDir, "conversion-runs").apply { mkdirs() }
        val singleRun = File(runs, "single-${UUID.randomUUID()}").apply { mkdirs() }
        val batchRun = File(runs, "batch-${UUID.randomUUID()}").apply { mkdirs() }
        val unsharedRun = File(runs, "unshared-${UUID.randomUUID()}").apply { mkdirs() }
        val outsideFile = File(context.filesDir, "outside-${UUID.randomUUID()}.epub").apply { writeText("secret") }
        try {
            val vm = ConverterViewModel()
            assertNull(vm.createShareIntent(context))

            val singleOut = File(singleRun, "0/book.epub").apply {
                parentFile?.mkdirs()
                writeText("epub-data")
            }
            vm.internalWorkDirectory = singleRun
            vm.outputFiles = listOf(singleOut)
            vm.pendingSave = true
            val chooser = requireNotNull(vm.createShareIntent(context))
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val singleIntent = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, singleIntent.action)
            assertEquals("application/epub+zip", singleIntent.type)
            assertTrue(singleIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val singleUri = requireNotNull(singleIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("${context.packageName}.fileprovider", singleUri.authority)
            assertEquals("epub-data", context.contentResolver.openInputStream(singleUri)!!.bufferedReader().use { it.readText() })
            assertTrue(vm.pendingSave)

            // Dismissing pendingSave after sharing unlocks the UI without deleting shared files needed by the receiver.
            vm.discardPendingSave()
            assertFalse(vm.pendingSave)
            assertTrue(singleOut.exists())

            // Starting and discarding an unshared run deletes only the unshared run directory.
            val unsharedOut = File(unsharedRun, "0/temp.epub").apply {
                parentFile?.mkdirs()
                writeText("unshared")
            }
            vm.internalWorkDirectory = unsharedRun
            vm.outputFiles = listOf(unsharedOut)
            vm.pendingSave = true
            vm.discardPendingSave()
            assertFalse(unsharedRun.exists())
            assertTrue(singleOut.exists())

            // Batch sharing uses ACTION_SEND_MULTIPLE and still allows saving afterward.
            val batchFirst = File(batchRun, "0/first.kfx").apply {
                parentFile?.mkdirs()
                writeText("kfx-1")
            }
            val batchSecond = File(batchRun, "1/second.kfx").apply {
                parentFile?.mkdirs()
                writeText("kfx-2")
            }
            vm.internalWorkDirectory = batchRun
            vm.outputFiles = listOf(batchFirst, batchSecond)
            vm.pendingSave = true
            val batchChooser = requireNotNull(vm.createShareIntent(context))
            val batchIntent = requireNotNull(batchChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, batchIntent.action)
            assertEquals("application/octet-stream", batchIntent.type)
            val batchUris = requireNotNull(batchIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(listOf("kfx-1", "kfx-2"), batchUris.map { uri -> context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } })
            assertEquals(2, batchIntent.clipData?.itemCount)
            assertTrue(vm.pendingSave)

            // Files outside conversion-runs cannot be exposed through FileProvider.
            vm.internalWorkDirectory = context.filesDir
            vm.outputFiles = listOf(outsideFile)
            vm.pendingSave = true
            assertThrows(IllegalArgumentException::class.java) {
                vm.createShareIntent(context)
            }

            // Expired shared runs are cleaned up once past the retention cutoff, while recent shared runs remain readable.
            val cutoff = System.currentTimeMillis()
            assertTrue(singleRun.setLastModified(cutoff - 10_000L))
            assertTrue(batchRun.setLastModified(cutoff + 10_000L))
            vm.purgeStaleTempFiles(context, cutoff)
            vm.viewModelScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
            assertFalse(singleRun.exists())
            assertTrue(batchFirst.exists())
            assertEquals("secret", outsideFile.readText())
        } finally {
            singleRun.deleteRecursively()
            batchRun.deleteRecursively()
            unsharedRun.deleteRecursively()
            outsideFile.delete()
        }
    }

    @Implements(android.content.ContentResolver::class)
    class UnavailableOutputStreamResolver : ShadowContentResolver() {
        var unavailableUri: Uri? = null

        @Implementation
        public override fun openOutputStream(uri: Uri, mode: String): java.io.OutputStream? = if (uri == unavailableUri) null else super.openOutputStream(uri, mode)
    }

    private class SaveTestDocumentsProvider(
        private val directory: File,
        private val authority: String,
        private val resolver: ShadowContentResolver,
        private val rejectedNames: Set<String> = emptySet(),
        private val denyWrites: Boolean = false,
    ) : DocumentsProvider() {
        override fun onCreate() = true

        override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(emptyArray<String>())

        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).apply {
            addRow(arrayOf(documentId))
        }

        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).apply {
            directory.listFiles().orEmpty().forEach { addRow(arrayOf(it.name)) }
        }

        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            if (displayName in rejectedNames) throw java.io.IOException("Document creation rejected")
            val file = File(directory, displayName)
            check(file.createNewFile()) { "File already exists: $displayName" }
            val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, displayName)
            resolver.registerOutputStreamSupplier(uri) {
                if (denyWrites) {
                    object : java.io.OutputStream() {
                        override fun write(value: Int) = throw SecurityException("Write permission denied")
                    }
                } else {
                    file.outputStream()
                }
            }
            return displayName
        }

        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
    }
}
