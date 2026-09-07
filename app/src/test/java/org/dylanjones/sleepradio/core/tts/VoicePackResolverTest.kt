package org.dylanjones.sleepradio.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoicePackResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun root() = tmp.newFolder("tts")

    /** Create `<root>/<id>/` with the requested pieces. */
    private fun makePack(
        root: File,
        id: String,
        model: Boolean = true,
        tokens: Boolean = true,
        data: Boolean = true,
        emptyModel: Boolean = false,
    ): File {
        val dir = File(root, id).apply { mkdirs() }
        if (model) File(dir, VoicePack.MODEL_NAME).writeText(if (emptyModel) "" else "onnx-bytes")
        if (tokens) File(dir, VoicePack.TOKENS_NAME).writeText("a 1\n")
        if (data) File(dir, VoicePack.DATA_DIR_NAME).mkdirs()
        return dir
    }

    @Test
    fun `nothing installed - preferred is null`() {
        assertNull(VoicePackResolver(root()).preferred())
    }

    @Test
    fun `resolution order - personal beats stock beats other`() {
        val root = root()
        makePack(root, "zzz-custom")
        makePack(root, VoicePackResolver.ID_STOCK)
        makePack(root, VoicePackResolver.ID_PERSONAL)
        val ids = VoicePackResolver(root).installed().map { it.id }
        assertEquals(
            listOf(VoicePackResolver.ID_PERSONAL, VoicePackResolver.ID_STOCK, "zzz-custom"),
            ids,
        )
        assertEquals(VoicePackResolver.ID_PERSONAL, VoicePackResolver(root).preferred()?.id)
    }

    @Test
    fun `missing model or tokens - not a pack`() {
        val root = root()
        makePack(root, "no-model", model = false)
        makePack(root, "no-tokens", tokens = false)
        makePack(root, "empty-model", emptyModel = true)
        assertTrue(VoicePackResolver(root).installed().isEmpty())
    }

    @Test
    fun `model-only personal borrows the stock espeak data`() {
        val root = root()
        makePack(root, VoicePackResolver.ID_STOCK)
        makePack(root, VoicePackResolver.ID_PERSONAL, data = false)

        val personal = VoicePackResolver(root).byId(VoicePackResolver.ID_PERSONAL)
        assertTrue(personal != null)
        assertEquals(
            File(File(root, VoicePackResolver.ID_STOCK), VoicePack.DATA_DIR_NAME),
            personal!!.dataDir,
        )
    }

    @Test
    fun `model-only personal with no stock - not installed`() {
        val root = root()
        makePack(root, VoicePackResolver.ID_PERSONAL, data = false)
        assertFalse(VoicePackResolver(root).isInstalled(VoicePackResolver.ID_PERSONAL))
    }

    @Test
    fun `no json - default sample rate`() {
        val root = root()
        makePack(root, VoicePackResolver.ID_STOCK)
        assertEquals(
            VoicePack.DEFAULT_SAMPLE_RATE,
            VoicePackResolver(root).byId(VoicePackResolver.ID_STOCK)?.sampleRate,
        )
    }
}
