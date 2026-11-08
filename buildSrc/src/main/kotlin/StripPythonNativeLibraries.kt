import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val nativePythonLibraryPattern = Regex("\\.so($|\\.)")

// Chaquopy's importer finds native modules by zip listing, but the runtime loader symlinks
// to the installed JNI libraries without reading the entry content — so the .so payload
// inside .imy archives is duplicated. Ship zero-length placeholders instead.
fun stripPythonNativeLibraries(source: File, target: File) {
    ZipFile(source).use { zip ->
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            for (entry in zip.entries().asSequence()) {
                if (entry.isDirectory || !nativePythonLibraryPattern.containsMatchIn(entry.name)) {
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) zip.getInputStream(entry).copyTo(out)
                } else {
                    out.putNextEntry(ZipEntry(entry.name).apply {
                        method = ZipEntry.STORED
                        size = 0
                        compressedSize = 0
                        crc = 0
                    })
                }
                out.closeEntry()
            }
        }
    }
    val sourceEntries = ZipFile(source).use { zip -> zip.entries().asSequence().associate { it.name to it.crc } }
    ZipFile(target).use { zip ->
        for (entry in zip.entries().asSequence()) {
            check(entry.name in sourceEntries) { "Stripped archive ${target.name} lost entry ${entry.name}" }
            if (!entry.isDirectory && nativePythonLibraryPattern.containsMatchIn(entry.name)) {
                check(entry.size == 0L) { "Native library ${entry.name} in ${target.name} still has content" }
            } else {
                check(entry.crc == sourceEntries[entry.name]) { "Stripped archive ${target.name} corrupted entry ${entry.name}" }
            }
        }
        check(zip.size() == sourceEntries.size) { "Stripped archive ${target.name} lost entries" }
    }
}

abstract class StripPythonNativeLibraries : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        doLast { stripDirectory(inputDirectory.get().asFile, outputDirectory.get().asFile) }
    }

    companion object {
        fun stripDirectory(input: File, output: File) {
            output.deleteRecursively()
            input.walkTopDown().forEach { file ->
                val target = output.resolve(file.relativeTo(input))
                if (file.isDirectory) {
                    target.mkdirs()
                } else if (file.name.endsWith(".imy") && file.parentFile.name == "chaquopy") {
                    target.parentFile.mkdirs()
                    stripPythonNativeLibraries(file, target)
                } else {
                    target.parentFile.mkdirs()
                    file.copyTo(target)
                }
            }
        }
    }
}
