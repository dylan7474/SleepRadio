package org.dylanjones.sleepradio.core.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoicePackInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun install(root: File, id: String, zip: ByteArray): Boolean = runBlocking {
        VoicePackInstaller(root).installZip(id) { ByteArrayInputStream(zip) }
    }

    @Test
    fun `flat zip installs and renames the model`() {
        val root = tmp.newFolder("tts")
        val ok = install(
            root, "stock",
            zipOf(
                "en_GB-voice.onnx" to "MODEL".toByteArray(),
                "en_GB-voice.onnx.json" to """{"audio":{"sample_rate":16000}}""".toByteArray(),
                "tokens.txt" to "a 1\n".toByteArray(),
                "espeak-ng-data/en_dict" to "x".toByteArray(),
            ),
        )
        assertTrue(ok)
        val dir = File(root, "stock")
        assertTrue(File(dir, "model.onnx").readText() == "MODEL")
        assertTrue(File(dir, "model.onnx.json").isFile)
        assertTrue(File(dir, "tokens.txt").isFile)
        assertTrue(File(dir, "espeak-ng-data").isDirectory)
    }

    @Test
    fun `zip nested one directory down is flattened`() {
        val root = tmp.newFolder("tts")
        val ok = install(
            root, "personal",
            zipOf(
                "my-voice/model.onnx" to "M".toByteArray(),
                "my-voice/tokens.txt" to "a 1\n".toByteArray(),
            ),
        )
        assertTrue(ok)
        assertTrue(File(root, "personal/model.onnx").isFile)
        assertFalse(File(root, "personal/my-voice").exists())
    }

    @Test
    fun `zip without tokens fails and installs nothing`() {
        val root = tmp.newFolder("tts")
        val ok = install(root, "stock", zipOf("model.onnx" to "M".toByteArray()))
        assertFalse(ok)
        assertFalse(File(root, "stock").exists())
    }

    @Test
    fun `zip without any onnx fails`() {
        val root = tmp.newFolder("tts")
        val ok = install(root, "stock", zipOf("tokens.txt" to "a 1\n".toByteArray()))
        assertFalse(ok)
    }

    @Test
    fun `path-traversal entry is rejected`() {
        val root = tmp.newFolder("tts")
        val ok = install(
            root, "stock",
            zipOf(
                "../escape.onnx" to "EVIL".toByteArray(),
                "tokens.txt" to "a 1\n".toByteArray(),
            ),
        )
        assertFalse(ok)
        assertFalse(File(root.parentFile, "escape.onnx").exists())
    }

    @Test
    fun `model-only personal import copies stock's espeak data in`() {
        val root = tmp.newFolder("tts")
        install(
            root, "stock",
            zipOf(
                "a.onnx" to "M".toByteArray(),
                "tokens.txt" to "a 1\n".toByteArray(),
                "espeak-ng-data/en_dict" to "x".toByteArray(),
            ),
        )
        install(
            root, "personal",
            zipOf("my.onnx" to "P".toByteArray(), "tokens.txt" to "a 1\n".toByteArray()),
        )
        assertTrue(File(root, "personal/espeak-ng-data/en_dict").isFile)
    }

    @Test
    fun `reinstall replaces the previous pack`() {
        val root = tmp.newFolder("tts")
        install(root, "stock", zipOf("a.onnx" to "V1".toByteArray(), "tokens.txt" to "a 1\n".toByteArray()))
        install(root, "stock", zipOf("a.onnx" to "V2".toByteArray(), "tokens.txt" to "a 1\n".toByteArray()))
        assertEquals("V2", File(root, "stock/model.onnx").readText())
    }
}
