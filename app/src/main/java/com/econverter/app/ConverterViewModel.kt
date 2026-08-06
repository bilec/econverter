package com.econverter.app

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConverterViewModel : ViewModel() {
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
    var cover by mutableStateOf("")
    var baseFontSize by mutableStateOf("")
    var disableFontRescaling by mutableStateOf(false)
    var marginTop by mutableStateOf("")
    var marginBottom by mutableStateOf("")
    var marginLeft by mutableStateOf("")
    var marginRight by mutableStateOf("")
    var extraArgs by mutableStateOf("")
    var status by mutableStateOf("")
    var isConverting by mutableStateOf(false)
    var pendingSave by mutableStateOf(false)
    private var internalOutputFile: File? = null
    private var internalBatchFiles: List<File> = emptyList()
    private var internalInputFile: File? = null

    val isBatch: Boolean get() = inputFileNames.size > 1
    val hasSelection: Boolean get() = inputUris.isNotEmpty()
    val inputFileName: String get() = inputFileNames.firstOrNull() ?: ""

    fun purgeStaleTempFiles(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            context.filesDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        }
    }

    fun addSelectedUris(context: android.content.Context, newUris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val validPairs = newUris.map { uri -> uri to getFileName(context, uri) }
                .filter { isInputSupported(it.second) }

            val unsupportedCount = newUris.size - validPairs.size

            withContext(Dispatchers.Main) {
                if (validPairs.isNotEmpty()) {
                    inputUris = inputUris + validPairs.map { it.first }
                    inputFileNames = inputFileNames + validPairs.map { it.second }
                    status = if (unsupportedCount > 0) "Selected ${inputUris.size} eBooks ($unsupportedCount unsupported skipped)" else ""
                } else if (unsupportedCount > 0) {
                    status = "Unsupported format(s) selected"
                } else {
                    status = ""
                }
            }
        }
    }

    // ponytail: PDF excluded — needs poppler (input) and PyQt5 (output), unavailable on Android
    val inputFormats = setOf("epub", "mobi", "azw3", "azw4", "docx", "odt", "fb2", "html", "htmlz", "lrf", "pdb", "rtf", "txt", "djvu", "djv", "chm", "cbz", "cbr")

    // ponytail: only profiles people actually use, add more when asked
    val outputProfiles = listOf("default", "kindle", "kindle_pw3", "kindle_oasis", "kobo", "generic_eink", "generic_eink_hd", "tablet", "ipad", "ipad3", "nook", "sony")
    val outputFormats = listOf("epub", "mobi", "azw3", "docx", "fb2", "html", "htmlz", "lrf", "oeb", "txt", "txtz")

    fun isInputSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return ext.isNotEmpty() && ext in inputFormats
    }

    fun getOutputFileName(): String {
        return "${inputFileName.substringBeforeLast(".")}.$outputFormat"
    }

    fun convert(context: android.content.Context) {
        if (!hasSelection) return
        isConverting = true
        status = "Converting..."

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (isBatch) {
                    runBatchConversion(context, inputUris, inputFileNames)
                } else {
                    runConversion(context, inputUris.first(), inputFileName)
                }
            }
            status = result
            isConverting = false
            if (result.startsWith("Done")) {
                pendingSave = true
            }
        }
    }

    fun checkDeps() {
        viewModelScope.launch {
            status = "Checking dependencies..."
            val result = withContext(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val converter = py.getModule("converter")
                    converter.callAttr("check_deps_str").toJava(String::class.java)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            status = result
        }
    }

    fun saveToUri(context: android.content.Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = if (isBatch) internalBatchFiles.size else 1
            val saved = if (isBatch) {
                saveBatchToDirectoryUri(context, uri)
            } else {
                saveSingleToUri(context, uri)
            }
            cleanup()
            withContext(Dispatchers.Main) {
                status = if (saved) {
                    if (isBatch) "Saved $count eBooks to directory" else "Saved: ${getOutputFileName()}"
                } else {
                    "Error: could not write file(s)"
                }
                pendingSave = false
            }
        }
    }

    private fun saveSingleToUri(context: android.content.Context, uri: Uri): Boolean {
        val file = internalOutputFile ?: return false
        return context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            true
        } ?: false
    }

    private fun saveBatchToDirectoryUri(context: android.content.Context, dirUri: Uri): Boolean {
        if (internalBatchFiles.isEmpty()) return false
        val treeId = DocumentsContract.getTreeDocumentId(dirUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, treeId)
        var successCount = 0

        for (file in internalBatchFiles) {
            val ext = file.extension
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val newFileUri = try {
                DocumentsContract.createDocument(context.contentResolver, parentDocumentUri, mimeType, file.name)
            } catch (e: Exception) {
                null
            } ?: continue

            context.contentResolver.openOutputStream(newFileUri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                successCount++
            }
        }
        return successCount > 0
    }

    private fun cleanup() {
        internalInputFile?.delete()
        internalOutputFile?.delete()
        internalBatchFiles.forEach { it.delete() }
        internalOutputFile = null
        internalBatchFiles = emptyList()
        internalInputFile = null
        pendingSave = false
    }

    private fun buildExtraArgs(): List<String> {
        val args = mutableListOf<String>()
        if (outputProfile != "default") args += listOf("--output-profile", outputProfile)
        if (smartenPunctuation) args += "--smarten-punctuation"
        if (outputFormat == "epub" && epubVersion != "2") args += listOf("--epub-version", epubVersion)
        // ponytail: Title & Cover omitted in batch mode to prevent overwriting metadata on distinct books
        if (!isBatch && title.isNotBlank()) args += listOf("--title", title)
        if (authors.isNotBlank()) args += listOf("--authors", authors)
        if (publisher.isNotBlank()) args += listOf("--publisher", publisher)
        if (comments.isNotBlank()) args += listOf("--comments", comments)
        if (!isBatch && cover.isNotBlank()) args += listOf("--cover", cover)
        if (baseFontSize.isNotBlank()) args += listOf("--base-font-size", baseFontSize)
        if (disableFontRescaling) args += "--disable-font-rescaling"
        if (marginTop.isNotBlank()) args += listOf("--margin-top", marginTop)
        if (marginBottom.isNotBlank()) args += listOf("--margin-bottom", marginBottom)
        if (marginLeft.isNotBlank()) args += listOf("--margin-left", marginLeft)
        if (marginRight.isNotBlank()) args += listOf("--margin-right", marginRight)
        // ponytail: free-text extra args for power users, split on whitespace
        if (extraArgs.isNotBlank()) args += extraArgs.trim().split("\\s+".toRegex())
        return args
    }

    private fun runConversion(context: android.content.Context, uri: Uri, fileName: String): String {
        return try {
            cleanup()
            val inputFile = copyUriToInternal(context, uri, fileName)
            internalInputFile = inputFile
            val outFile = File(context.filesDir, getOutputFileName())

            val py = Python.getInstance()
            val module = py.getModule("converter")
            val cliArgs = buildExtraArgs()
            val pyArgs = mutableListOf<Any>(inputFile.absolutePath, outFile.absolutePath)
            pyArgs.addAll(cliArgs)
            val result = module.callAttr("convert", *pyArgs.toTypedArray())

            val success = result.callAttr("__getitem__", "success").toBoolean()
            val message = result.callAttr("__getitem__", "message").toString()

            if (success) {
                internalOutputFile = outFile
                "Done — choose where to save"
            } else {
                "Error: $message"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun runBatchConversion(context: android.content.Context, uris: List<Uri>, fileNames: List<String>): String {
        return try {
            cleanup()
            val py = Python.getInstance()
            val module = py.getModule("converter")
            val cliArgs = buildExtraArgs()

            val convertedFiles = mutableListOf<File>()
            var failCount = 0

            for (i in uris.indices) {
                val uri = uris[i]
                val fileName = fileNames[i]
                val currentOutName = "${fileName.substringBeforeLast(".")}.$outputFormat"
                val tmpIn = copyUriToInternal(context, uri, fileName)
                val tmpOut = File(context.filesDir, currentOutName)

                val pyArgs = mutableListOf<Any>(tmpIn.absolutePath, tmpOut.absolutePath)
                pyArgs.addAll(cliArgs)

                val result = module.callAttr("convert", *pyArgs.toTypedArray())
                val success = result.callAttr("__getitem__", "success").toBoolean()

                if (success && tmpOut.exists() && tmpOut.length() > 0) {
                    convertedFiles += tmpOut
                } else {
                    failCount++
                    tmpOut.delete()
                }

                tmpIn.delete()
            }

            if (convertedFiles.isNotEmpty()) {
                internalBatchFiles = convertedFiles
                if (failCount == 0) {
                    "Done (${convertedFiles.size} books converted) — choose directory to save"
                } else {
                    "Done (${convertedFiles.size} converted, $failCount failed) — choose directory to save"
                }
            } else {
                "Error: All $failCount conversions failed"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun copyUriToInternal(context: android.content.Context, uri: Uri, fileName: String): File {
        val tmp = File(context.filesDir, "$fileName.tmp")
        val target = File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tmp.renameTo(target)
        return target
    }
}
