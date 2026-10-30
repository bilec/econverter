package com.econverter.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val PREFERENCES_NAME = "settings"
private const val BACKEND_PREFERENCE_KEY = "backend"
private const val CONVERSION_RUNS_DIRECTORY = "conversion-runs"
private const val MAX_HISTORY_ENTRIES = 20
private const val SHARED_RUN_RETENTION_MILLIS = 3_600_000L

data class StatusMessage(
    @StringRes val resourceId: Int = 0,
    val args: List<String> = emptyList(),
    val isError: Boolean = false,
    val isSuccess: Boolean = false,
    val progress: Float? = null,
) {
    fun text(context: Context): String = if (resourceId == 0) "" else context.getString(resourceId, *args.toTypedArray())

    fun toBundle(): Bundle = Bundle().apply {
        putInt("statusResource", resourceId)
        putStringArrayList("statusArgs", ArrayList(args))
        putBoolean("statusError", isError)
        putBoolean("statusSuccess", isSuccess)
        progress?.let { putFloat("statusProgress", it) }
    }

    companion object {
        fun fromBundle(bundle: Bundle): StatusMessage = if (bundle.containsKey("statusResource")) {
            StatusMessage(bundle.getInt("statusResource"), bundle.getStringArrayList("statusArgs").orEmpty(), bundle.getBoolean("statusError"), bundle.getBoolean("statusSuccess"), if (bundle.containsKey("statusProgress")) bundle.getFloat("statusProgress") else null)
        } else {
            StatusMessage(R.string.status_worker_missing_result, isError = true)
        }
    }
}

data class HistoryEntry(
    val outputFormat: String,
    val status: StatusMessage,
    val timestamp: Long,
    val uris: List<Uri> = emptyList(),
    val fileNames: List<String> = emptyList(),
    val backend: ConversionBackend = ConversionBackend.AUTO,
)

class ConverterViewModel : ViewModel() {
    var backend by mutableStateOf(ConversionBackend.AUTO)
        private set
    var inputUris by mutableStateOf<List<Uri>>(emptyList())
    var inputFileNames by mutableStateOf<List<String>>(emptyList())
    var outputFormat by mutableStateOf("epub")
    var outputProfile by mutableStateOf("default")
    var smartenPunctuation by mutableStateOf(false)
    var epubVersion by mutableStateOf("2")
    var title by mutableStateOf("")
    var authors by mutableStateOf("")
    var publisher by mutableStateOf("")
    var comments by mutableStateOf("")
    var baseFontSize by mutableStateOf("")
    var disableFontRescaling by mutableStateOf(false)
    var marginTop by mutableStateOf("")
    var marginBottom by mutableStateOf("")
    var marginLeft by mutableStateOf("")
    var marginRight by mutableStateOf("")
    var extraArgs by mutableStateOf("")
    var removeText by mutableStateOf("")
    var status by mutableStateOf(StatusMessage())
    var isConverting by mutableStateOf(false)
    var isCancelling by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var pendingSave by mutableStateOf(false)
    val history = mutableStateListOf<HistoryEntry>()
    internal var outputFiles: List<File> = emptyList()
    internal var internalWorkDirectory: File? = null
    private var worker: ConversionWorker? = null
    private val startedAt = System.currentTimeMillis()
    private var backendPreferenceLoaded = false

    val isBatch: Boolean get() = inputFileNames.size > 1
    val hasSelection: Boolean get() = inputUris.isNotEmpty()
    val inputFileName: String get() = inputFileNames.firstOrNull() ?: ""
    val unsupportedInputFileNames: List<String> get() = inputFileNames.filterNot(::isInputSupported)
    val canConvert: Boolean get() = hasSelection && inputUris.size == inputFileNames.size && unsupportedInputFileNames.isEmpty() && outputFormat in outputFormats
    val conversionProgress: Float?
        get() = when {
            isSaving -> null
            pendingSave -> 1f
            isConverting -> status.progress
            else -> null
        }
    val shouldAutoDismissStatus: Boolean
        get() = status.resourceId != 0 && status.resourceId != R.string.loading_selection && !status.isError && !isConverting && !isSaving && !pendingSave

    fun selectBackend(value: ConversionBackend, context: android.content.Context? = null) {
        if (isConverting || isSaving || pendingSave) return
        backend = value
        if (outputFormat !in outputFormats) outputFormat = "epub"
        status = StatusMessage()
        context?.getSharedPreferences(PREFERENCES_NAME, 0)?.edit()?.putString(BACKEND_PREFERENCE_KEY, value.name)?.apply()
    }

    fun loadBackendPreference(context: android.content.Context) {
        if (backendPreferenceLoaded) return
        backendPreferenceLoaded = true
        val saved = context.getSharedPreferences(PREFERENCES_NAME, 0).getString(BACKEND_PREFERENCE_KEY, null)
        selectBackend(ConversionBackend.entries.firstOrNull { it.name == saved } ?: ConversionBackend.AUTO)
    }

    fun clearSelection() {
        if (isConverting || isSaving || pendingSave) return
        inputUris = emptyList()
        inputFileNames = emptyList()
        status = StatusMessage()
    }

    fun removeSelectedUri(uri: Uri) {
        if (isConverting || isSaving || pendingSave) return
        val index = inputUris.indexOf(uri)
        if (index == -1) return
        inputUris = inputUris.filterIndexed { i, _ -> i != index }
        inputFileNames = inputFileNames.filterIndexed { i, _ -> i != index }
        status = StatusMessage()
    }

    fun selectHistory(entry: HistoryEntry) {
        if (isConverting || isSaving || pendingSave || entry.uris.isEmpty()) return
        inputUris = entry.uris.toList()
        inputFileNames = entry.fileNames.toList()
        outputFormat = entry.outputFormat
        selectBackend(entry.backend)
    }

    fun purgeStaleTempFiles(context: android.content.Context, cutoffMillis: Long = startedAt) {
        viewModelScope.launch(Dispatchers.IO) {
            File(context.filesDir, CONVERSION_RUNS_DIRECTORY).listFiles()?.forEach { file ->
                if (file != internalWorkDirectory && file.lastModified() < cutoffMillis) {
                    file.deleteRecursively()
                }
            }
        }
    }

    fun addSelectedUris(context: android.content.Context, newUris: List<Uri>) {
        if (isConverting || isSaving || pendingSave) return
        status = StatusMessage(R.string.loading_selection)
        viewModelScope.launch(Dispatchers.IO) {
            val pairs = newUris.map { uri -> uri to getFileName(context, uri) }

            withContext(Dispatchers.Main) {
                if (isConverting || isSaving || pendingSave) return@withContext
                val validPairs = pairs.filter { isInputSupported(it.second) }
                val unsupportedCount = newUris.size - validPairs.size
                if (validPairs.isNotEmpty()) {
                    inputUris = inputUris + validPairs.map { it.first }
                    inputFileNames = inputFileNames + validPairs.map { it.second }
                }
                status = when {
                    validPairs.isNotEmpty() && unsupportedCount > 0 -> StatusMessage(R.string.status_selected_skipped, listOf(inputUris.size.toString(), unsupportedCount.toString()))
                    validPairs.isEmpty() && unsupportedCount > 0 -> StatusMessage(R.string.status_unsupported_selection)
                    else -> StatusMessage()
                }
            }
        }
    }

    val inputFormats: Set<String> get() = backend.inputFormats

    // shortcut: fixed profile list; extend for additional devices.
    val outputProfiles = listOf("default", "kindle", "kindle_pw3", "kindle_oasis", "kobo", "generic_eink", "generic_eink_hd", "tablet", "ipad", "ipad3", "nook", "sony")

    val outputFormats: List<String> get() = backend.outputFormats

    fun isInputSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return ext.isNotEmpty() && ext in inputFormats
    }

    fun convert(context: android.content.Context) {
        if (!hasSelection || isConverting || isSaving || pendingSave) return
        if (!canConvert) {
            status = StatusMessage(R.string.status_unsupported_conversion, listOf(backend.label), isError = true)
            return
        }
        cleanup()
        purgeStaleTempFiles(context, System.currentTimeMillis() - SHARED_RUN_RETENTION_MILLIS)
        val uris = inputUris.toList()
        val names = inputFileNames.toList()
        val format = outputFormat
        val selectedBackend = backend
        val args = if (selectedBackend == ConversionBackend.BOKO) emptyList() else buildExtraArgs()
        val directory = File(context.filesDir, "$CONVERSION_RUNS_DIRECTORY/${UUID.randomUUID()}")
        internalWorkDirectory = directory
        isConverting = true
        isCancelling = false
        status = StatusMessage(R.string.status_converting)
        val currentWorker = ConversionWorker(context, directory, uris, names, format, args, selectedBackend) { status = it }
        worker = currentWorker
        viewModelScope.launch {
            try {
                val result = currentWorker.run()
                status = result.status
                outputFiles = result.files
                pendingSave = result.files.isNotEmpty()
                history.add(HistoryEntry(format, result.status, System.currentTimeMillis(), uris, names, selectedBackend))
                if (history.size > MAX_HISTORY_ENTRIES) history.removeAt(0)
            } finally {
                worker = null
                isConverting = false
                isCancelling = false
            }
        }
    }

    fun createShareIntent(context: Context): Intent? {
        val files = outputFiles.filter(File::isFile)
        if (isSaving || isConverting || !pendingSave || files.isEmpty()) return null
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
        // shortcut: detach work dir with 1h mtime retention instead of tracking receiver completion; upgrade if huge batches fill storage.
        internalWorkDirectory?.setLastModified(System.currentTimeMillis())
        internalWorkDirectory = null
        val ext = files.first().extension.lowercase()
        val shareIntent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (ext == "epub") "application/epub+zip" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        return Intent.createChooser(shareIntent, null)
    }

    fun cancelConversion() {
        if (!isConverting || isCancelling) return
        isCancelling = true
        status = StatusMessage(R.string.cancelling)
        worker?.cancel()
    }

    fun discardPendingSave() {
        if (isSaving) return
        cleanup()
        status = StatusMessage(R.string.status_save_cancelled)
    }

    fun saveToUri(context: android.content.Context, uri: Uri) {
        if (isSaving || isConverting || !pendingSave) return
        isSaving = true
        val files = outputFiles
        val batch = isBatch || files.size > 1
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                when {
                    !saveToDirectoryUri(context, uri, files) -> StatusMessage(R.string.status_save_failed, isError = true)
                    batch -> StatusMessage(R.string.status_saved_batch, listOf(files.size.toString()), isSuccess = true)
                    else -> StatusMessage(R.string.status_saved_file, listOf(files.first().name), isSuccess = true)
                }
            } catch (error: Exception) {
                StatusMessage(R.string.status_error, listOf(error.message.orEmpty()), isError = true)
            }
            cleanup()
            withContext(Dispatchers.Main) {
                status = result
                pendingSave = false
                isSaving = false
            }
        }
    }

    internal fun saveToDirectoryUri(context: android.content.Context, dirUri: Uri, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        val treeId = DocumentsContract.getTreeDocumentId(dirUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, treeId)
        val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, treeId)
        val existingNames = mutableSetOf<String>()
        context.contentResolver.query(
            childDocumentsUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                existingNames += cursor.getString(nameIndex)
            }
        }
        var successCount = 0

        for (file in files) {
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            val targetName = uniqueFileName(file.name, existingNames)
            val newFileUri = try {
                DocumentsContract.createDocument(context.contentResolver, parentDocumentUri, mimeType, targetName)
            } catch (e: Exception) {
                null
            } ?: continue

            context.contentResolver.openOutputStream(newFileUri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                successCount++
                existingNames += targetName
            }
        }
        return successCount > 0
    }

    private fun uniqueFileName(fileName: String, existingNames: Set<String>): String {
        val extensionStart = fileName.lastIndexOf('.')
        val baseName = if (extensionStart > 0) fileName.substring(0, extensionStart) else fileName
        val extension = if (extensionStart > 0) fileName.substring(extensionStart) else ""
        var index = 1
        var candidate = fileName
        while (candidate in existingNames) {
            candidate = "$baseName($index)$extension"
            index++
        }
        return candidate
    }

    private fun cleanup() {
        internalWorkDirectory?.deleteRecursively()
        outputFiles = emptyList()
        internalWorkDirectory = null
        pendingSave = false
    }

    override fun onCleared() {
        worker?.cancel()
        if (!isConverting && !isSaving) cleanup()
        super.onCleared()
    }

    private fun buildExtraArgs(): List<String> {
        val args = mutableListOf<String>()
        if (outputProfile != "default") args += listOf("--output-profile", outputProfile)
        if (smartenPunctuation) args += "--smarten-punctuation"
        if (outputFormat == "epub" && epubVersion != "2") args += listOf("--epub-version", epubVersion)
        // Preserve each book's own title during batch conversion.
        if (!isBatch && title.isNotBlank()) args += listOf("--title", title)
        if (authors.isNotBlank()) args += listOf("--authors", authors)
        if (publisher.isNotBlank()) args += listOf("--publisher", publisher)
        if (comments.isNotBlank()) args += listOf("--comments", comments)
        if (baseFontSize.isNotBlank()) args += listOf("--base-font-size", baseFontSize)
        if (disableFontRescaling) args += "--disable-font-rescaling"
        if (marginTop.isNotBlank()) args += listOf("--margin-top", marginTop)
        if (marginBottom.isNotBlank()) args += listOf("--margin-bottom", marginBottom)
        if (marginLeft.isNotBlank()) args += listOf("--margin-left", marginLeft)
        if (marginRight.isNotBlank()) args += listOf("--margin-right", marginRight)
        // Keep double-quoted values together, including typographic quotes.
        if (extraArgs.isNotBlank()) args += Regex("[\"“]([^\"”]*)[\"”]|\\S+").findAll(extraArgs).map { it.groups[1]?.value ?: it.value }
        if (removeText.isNotBlank()) args += listOf("--remove-text", removeText)
        return args
    }
}
