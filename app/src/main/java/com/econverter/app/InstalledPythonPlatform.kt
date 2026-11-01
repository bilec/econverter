package com.econverter.app

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

internal class InstalledPythonPlatform(private val context: Context) : AndroidPlatform(context) {
    private val libraryDirectory = File(context.applicationInfo.nativeLibraryDir)
    private val assetDirectory = File(context.filesDir, "chaquopy")
    private val manifest = context.assets.open("chaquopy/native-libraries/$ABI.json").bufferedReader().use { it.readText() }

    override fun getPath(): String {
        val path = super.getPath()
        val mapping = JSONObject(manifest)
        for (relativePath in mapping.keys()) {
            if (!relativePath.startsWith("bootstrap-native/$ABI/")) continue
            val libraryName = mapping.getString(relativePath)
            require(!relativePath.startsWith('/') && relativePath.split('/').none { it == ".." })
            require(libraryName == File(libraryName).name)
            val library = libraryDirectory.resolve(libraryName)
            check(library.isFile) { "Missing installed Python library: $libraryName" }
            val destination = assetDirectory.resolve(relativePath)
            if (destination.canonicalPath == library.canonicalPath) continue
            try {
                Os.remove(destination.absolutePath)
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.ENOENT) throw error
            }
            Os.symlink(library.canonicalPath, destination.absolutePath)
        }
        return path
    }

    override fun onStart(python: Python) {
        val globals = python.getModule("builtins").callAttr("dict")
        val loader = context.assets.open("chaquopy_native_loader.py").bufferedReader().use { it.readText() }
        python.getModule("builtins").callAttr("exec", loader, globals)
        globals.callAttr("__getitem__", "install").call(libraryDirectory.canonicalPath, assetDirectory.canonicalPath, python.getModule("json").callAttr("loads", manifest))
        super.onStart(python)
    }
}
