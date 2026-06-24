package com.econverter.app

import android.net.Uri
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
    var inputUri by mutableStateOf<Uri?>(null)
    var inputFileName by mutableStateOf("")
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
    private var internalInputFile: File? = null

    // ponytail: PDF excluded — needs poppler (input) and PyQt5 (output), unavailable on Android
    val inputFormats = setOf("epub", "mobi", "azw3", "azw4", "docx", "odt", "fb2", "html", "htmlz", "lit", "lrf", "pdb", "pml", "rb", "rtf", "snb", "tcr", "txt", "djvu", "djv", "chm", "cbz", "cbr")

    // ponytail: only profiles people actually use, add more when asked
    val outputProfiles = listOf("default", "kindle", "kindle_pw3", "kindle_oasis", "kobo", "generic_eink", "generic_eink_hd", "tablet", "ipad", "ipad3", "nook", "sony")
    val outputFormats = listOf("epub", "mobi", "azw3", "docx", "fb2", "htmlz", "html", "lit", "lrf", "oeb", "pdb", "pml", "rb", "rtf", "snb", "tcr", "txt", "txtz")

    fun isInputSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return ext.isNotEmpty() && ext in inputFormats
    }

    fun getOutputFileName(): String {
        return "${inputFileName.substringBeforeLast(".")}.$outputFormat"
    }

    fun convert(context: android.content.Context) {
        val uri = inputUri ?: return
        isConverting = true
        status = "Converting..."

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runConversion(context, uri)
            }
            status = result
            isConverting = false
            if (result.startsWith("Done")) {
                pendingSave = true
            }
        }
    }

    fun saveToUri(context: android.content.Context, uri: Uri) {
        val file = internalOutputFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val saved = context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                true
            } ?: false
            cleanup()
            withContext(Dispatchers.Main) {
                status = if (saved) "Saved: ${getOutputFileName()}" else "Error: could not write file"
                pendingSave = false
            }
        }
    }

    private fun cleanup() {
        internalInputFile?.delete()
        internalOutputFile?.delete()
        internalInputFile = null
        internalOutputFile = null
        pendingSave = false
    }

    private fun buildExtraArgs(): List<String> {
        val args = mutableListOf<String>()
        if (outputProfile != "default") args += listOf("--output-profile", outputProfile)
        if (smartenPunctuation) args += "--smarten-punctuation"
        if (outputFormat == "epub" && epubVersion != "2") args += listOf("--epub-version", epubVersion)
        if (title.isNotBlank()) args += listOf("--title", title)
        if (authors.isNotBlank()) args += listOf("--authors", authors)
        if (publisher.isNotBlank()) args += listOf("--publisher", publisher)
        if (comments.isNotBlank()) args += listOf("--comments", comments)
        if (cover.isNotBlank()) args += listOf("--cover", cover)
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

    private fun runConversion(context: android.content.Context, uri: Uri): String {
        return try {
            cleanup()
            val inputFile = copyUriToInternal(context, uri, inputFileName)
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
