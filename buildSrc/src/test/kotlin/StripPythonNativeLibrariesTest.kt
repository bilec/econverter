import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class StripPythonNativeLibrariesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun makeArchive(entries: Map<String, ByteArray>) = folder.newFile().apply {
        ZipOutputStream(outputStream().buffered()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
    }

    @Test
    fun nativeLibrariesBecomeZeroLengthPlaceholders() {
        val source = makeArchive(mapOf(
            "stdlib/_ssl.cpython-311.so" to ByteArray(1000) { it.toByte() },
            "pypdfium2_raw/libpdfium.so" to ByteArray(2000) { (it % 7).toByte() },
        ))
        val target = folder.newFile()

        stripPythonNativeLibraries(source, target)

        ZipFile(target).use { zip ->
            for (entry in zip.entries().asSequence()) {
                assertEquals("${entry.name} must be a zero-length placeholder", 0, entry.size)
            }
        }
    }

    @Test
    fun nonNativeEntriesKeepTheirContent() {
        val pyc = ByteArray(500) { (it % 13).toByte() }
        val json = """{"a": 1}""".toByteArray()
        val source = makeArchive(mapOf(
            "ebook_converter/__init__.pyc" to pyc,
            "tzdata/data.json" to json,
            "lxml/etree.so" to ByteArray(100) { 1 },
        ))
        val target = folder.newFile()

        stripPythonNativeLibraries(source, target)

        ZipFile(target).use { zip ->
            assertArrayEquals(pyc, zip.getInputStream(zip.getEntry("ebook_converter/__init__.pyc")).use { it.readBytes() })
            assertArrayEquals(json, zip.getInputStream(zip.getEntry("tzdata/data.json")).use { it.readBytes() })
        }
    }

    @Test
    fun everyEntrySurvivesWithItsName() {
        val source = makeArchive(mapOf(
            "a/__init__.pyc" to byteArrayOf(1),
            "b/lib.so" to byteArrayOf(2),
            "c/d.txt" to byteArrayOf(3),
        ))
        val target = folder.newFile()

        stripPythonNativeLibraries(source, target)

        ZipFile(source).use { original ->
            ZipFile(target).use { stripped ->
                assertEquals(
                    original.entries().asSequence().map { it.name }.toSet(),
                    stripped.entries().asSequence().map { it.name }.toSet(),
                )
            }
        }
    }

    @Test
    fun unrelatedAssetsAreCopiedUnchanged() {
        val root = folder.newFolder("assets").apply {
            resolve("chaquopy").mkdir()
            resolve("chaquopy/cacert.pem").writeText("cert")
            resolve("chaquopy/app.imy").writeBytes(makeArchiveBytes(mapOf("app.so" to byteArrayOf(9))))
            resolve("other.bin").writeBytes(byteArrayOf(1, 2, 3))
        }
        val output = folder.newFolder("output")

        StripPythonNativeLibraries.stripDirectory(root, output)

        assertArrayEquals(byteArrayOf(1, 2, 3), output.resolve("other.bin").readBytes())
        assertEquals("cert", output.resolve("chaquopy/cacert.pem").readText())
        ZipFile(output.resolve("chaquopy/app.imy")).use { zip ->
            val entry = zip.getEntry("app.so")
            assertNotNull(entry)
            assertEquals(0, entry.size)
        }
    }

    @Test
    fun nestedDirectoriesAreRecreated() {
        val root = folder.newFolder("assets").apply {
            resolve("a/b/c").mkdirs()
            resolve("a/b/c/deep.txt").writeText("deep")
        }
        val output = folder.newFolder("output")

        StripPythonNativeLibraries.stripDirectory(root, output)

        assertTrue(output.resolve("a/b/c").isDirectory)
        assertEquals("deep", output.resolve("a/b/c/deep.txt").readText())
    }

    private fun makeArchiveBytes(entries: Map<String, ByteArray>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
}
