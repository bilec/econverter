package com.econverter.app

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.github.bilec.boko.Boko
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class ConversionResult(val status: String, val files: List<File> = emptyList())

class ConversionWorker(
    context: Context,
    private val directory: File,
    private val uris: List<Uri>,
    private val names: List<String>,
    private val format: String,
    private val args: List<String>,
    private val backend: ConversionBackend = ConversionBackend.AUTO,
    private val onProgress: (String) -> Unit = {},
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var remote: Messenger? = null
    private var cancelled = false
    private var completed = false

    fun cancel() {
        if (completed) return
        cancelled = true
        handler.post {
            try {
                remote?.send(Message.obtain(null, ConversionService.CANCEL))
            } catch (_: RemoteException) {
            }
        }
    }

    suspend fun run(): ConversionResult {
        if (cancelled) {
            directory.deleteRecursively()
            return ConversionResult("Cancelled")
        }
        return suspendCancellableCoroutine { continuation ->
            var bound = false
            var linked = false
            var binder: IBinder? = null
            lateinit var connection: ServiceConnection
            lateinit var death: IBinder.DeathRecipient

            fun finish(result: ConversionResult) {
                if (completed) return
                completed = true
                if (linked) binder?.unlinkToDeath(death, 0)
                if (bound) context.unbindService(connection)
                remote = null
                if (cancelled || !continuation.isActive || result.files.isEmpty()) {
                    directory.deleteRecursively()
                }
                if (continuation.isActive) continuation.resume(if (cancelled) ConversionResult("Cancelled") else result)
            }

            death = IBinder.DeathRecipient {
                handler.post { finish(ConversionResult(if (cancelled) "Cancelled" else "Error: conversion worker stopped")) }
            }
            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    when (message.what) {
                        ConversionService.PROGRESS -> if (!cancelled) onProgress(message.data.getString("status").orEmpty())

                        ConversionService.DONE -> finish(
                            ConversionResult(
                                message.data.getString("status") ?: "Error: missing conversion result",
                                message.data.getStringArrayList("files").orEmpty().map { File(it) },
                            ),
                        )
                    }
                    true
                },
            )
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    binder = service
                    remote = Messenger(service)
                    try {
                        service.linkToDeath(death, 0)
                        linked = true
                        val message = Message.obtain(null, if (cancelled) ConversionService.CANCEL else ConversionService.RUN)
                        message.replyTo = reply
                        message.data = Bundle().apply {
                            putString("directory", directory.absolutePath)
                            putStringArrayList("uris", ArrayList(uris.map { it.toString() }))
                            putStringArrayList("names", ArrayList(names))
                            putString("format", format)
                            putString("backend", backend.name)
                            putStringArrayList("args", ArrayList(args))
                        }
                        remote?.send(message)
                    } catch (_: RemoteException) {
                        finish(ConversionResult("Error: could not start conversion worker"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    finish(ConversionResult("Error: conversion worker disconnected"))
                }

                override fun onBindingDied(name: ComponentName) {
                    finish(ConversionResult("Error: conversion worker binding died"))
                }

                override fun onNullBinding(name: ComponentName) {
                    finish(ConversionResult("Error: conversion worker unavailable"))
                }
            }
            continuation.invokeOnCancellation { cancel() }
            try {
                bound = context.bindService(Intent(context, ConversionService::class.java), connection, Context.BIND_AUTO_CREATE)
                if (!bound) finish(ConversionResult("Error: could not bind conversion worker"))
            } catch (error: Exception) {
                finish(ConversionResult("Error: ${error.message}"))
            }
        }
    }
}

class ConversionService : Service() {
    companion object {
        const val RUN = 1
        const val CANCEL = 2
        const val PROGRESS = 3
        const val DONE = 4
    }

    private var started = false
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                CANCEL -> Process.killProcess(Process.myPid())

                RUN -> if (!started) {
                    started = true
                    val data = message.data
                    val reply = message.replyTo
                    Thread {
                        val result = try {
                            convertBooks(data, reply)
                        } catch (error: Exception) {
                            ConversionResult("Error: ${error.message}")
                        }
                        send(
                            reply,
                            DONE,
                            Bundle().apply {
                                putString("status", result.status)
                                putStringArrayList("files", ArrayList(result.files.map { it.absolutePath }))
                            },
                        )
                    }.start()
                }
            }
            true
        },
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun send(reply: Messenger, code: Int, data: Bundle) {
        try {
            reply.send(Message.obtain(null, code).apply { this.data = data })
        } catch (_: RemoteException) {
            Process.killProcess(Process.myPid())
        }
    }

    private fun convertBooks(data: Bundle, reply: Messenger): ConversionResult {
        val directory = File(requireNotNull(data.getString("directory")))
        val uris = requireNotNull(data.getStringArrayList("uris"))
        val names = requireNotNull(data.getStringArrayList("names"))
        val format = requireNotNull(data.getString("format"))
        val args = data.getStringArrayList("args").orEmpty()
        val backend = ConversionBackend.entries.firstOrNull { it.name == data.getString("backend") } ?: error("Unknown conversion backend")
        require(uris.isNotEmpty() && uris.size == names.size) { "Invalid book selection" }
        require(format in backend.outputFormats) { "Unsupported output for ${backend.label}: $format" }
        require(names.all { it.substringAfterLast('.', "").lowercase() in backend.inputFormats }) { "Unsupported input for ${backend.label}" }
        require(backend != ConversionBackend.BOKO || args.isEmpty()) { "Calibre options are unavailable with boko" }
        val files = mutableListOf<File>()
        val errors = mutableListOf<String>()

        for (index in uris.indices) {
            send(reply, PROGRESS, Bundle().apply { putString("status", "Converting... $index/${uris.size} done") })
            val bookDirectory = File(directory, index.toString()).apply { check(mkdirs()) { "Could not create temporary directory" } }
            val name = File(names[index]).name
            val inputDirectory = File(bookDirectory, "source").apply { check(mkdir()) { "Could not create input directory" } }
            val input = File(inputDirectory, "${name.substringBeforeLast('.')}.${name.substringAfterLast('.').lowercase()}")
            val output = File(bookDirectory, "${name.substringBeforeLast('.').ifBlank { "book" }}.$format")
            val stages = File(inputDirectory, "stages")
            val intermediate = File(stages, "intermediate.epub")
            val normalized = File(stages, "normalized.epub")
            try {
                val stream = contentResolver.openInputStream(Uri.parse(uris[index])) ?: error("Could not read $name")
                stream.use { source -> input.outputStream().use { source.copyTo(it) } }
                when (backend) {
                    ConversionBackend.BOKO -> convertWithBoko(input, output)

                    ConversionBackend.CALIBRE -> convertWithCalibre(input, output, args, directory)

                    ConversionBackend.AUTO -> {
                        check(stages.mkdir()) { "Could not create conversion staging directory" }
                        val calibreInput = if (input.extension !in ConversionBackend.CALIBRE.inputFormats) {
                            convertWithBoko(input, normalized)
                            normalized
                        } else {
                            input
                        }
                        val calibreOutput = if (format in ConversionBackend.CALIBRE.outputFormats) output else intermediate
                        convertWithCalibre(calibreInput, calibreOutput, args, directory)
                        if (calibreOutput != output) convertWithBoko(intermediate, output)
                    }
                }
                check(output.isFile && output.length() > 0) { "Converter produced no output" }
                files += output
            } catch (error: Exception) {
                output.delete()
                errors += "$name: ${error.message}"
            } finally {
                input.delete()
                intermediate.delete()
                normalized.delete()
            }
        }
        val status = when {
            files.isEmpty() -> "Error: ${errors.joinToString("\n") }"
            uris.size == 1 -> "Done - choose where to save"
            errors.isEmpty() -> "Done (${files.size} books converted) - choose directory to save"
            else -> "Done (${files.size} converted, ${errors.size} failed) - choose directory to save\n${errors.joinToString("\n") }"
        }
        return ConversionResult(status, files)
    }

    private fun convertWithBoko(input: File, output: File) {
        try {
            Boko.convert(input.absolutePath, output.absolutePath)
        } catch (error: LinkageError) {
            throw IllegalStateException("boko backend unavailable: ${error.message}", error)
        }
    }

    private fun convertWithCalibre(input: File, output: File, args: List<String>, directory: File) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val python = Python.getInstance()
        val scratch = File(directory, "tmp").apply { check(isDirectory || mkdirs()) { "Could not create scratch directory" } }
        python.getModule("tempfile").put("tempdir", scratch.absolutePath)
        val result = python.getModule("ebook_converter_lib").callAttr("convert", input.absolutePath, output.absolutePath, *args.toTypedArray())
        check(result.callAttr("__getitem__", "success").toBoolean()) { result.callAttr("__getitem__", "message").toString() }
        check(output.isFile && output.length() > 0) { "Calibre produced no output" }
    }
}
