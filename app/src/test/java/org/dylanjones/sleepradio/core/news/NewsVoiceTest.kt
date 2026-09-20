package org.dylanjones.sleepradio.core.news

import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_OFF
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_PERSONAL
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_STOCK
import org.dylanjones.sleepradio.core.data.NEWS_VOICE_SAME
import org.dylanjones.sleepradio.core.tts.VoicePackResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NewsVoiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pack(root: File, id: String) {
        val d = File(root, id).apply { mkdirs() }
        File(d, "model.onnx").writeText("x")
        File(d, "tokens.txt").writeText("x")
        File(d, "espeak-ng-data").mkdirs()
    }

    private fun resolver(vararg ids: String): VoicePackResolver {
        val root = tmp.newFolder()
        ids.forEach { pack(root, it) }
        return VoicePackResolver(root)
    }

    @Test
    fun `same follows the DJ voice`() {
        val r = resolver("stock", "personal")
        assertEquals("stock", resolveNewsPack(r, NEWS_VOICE_SAME, BROADCAST_VOICE_STOCK)?.id)
        assertEquals("personal", resolveNewsPack(r, NEWS_VOICE_SAME, BROADCAST_VOICE_PERSONAL)?.id)
    }

    @Test
    fun `explicit news voice wins over the DJ voice`() {
        val r = resolver("stock", "personal")
        assertEquals("stock", resolveNewsPack(r, BROADCAST_VOICE_STOCK, BROADCAST_VOICE_PERSONAL)?.id)
        assertEquals("personal", resolveNewsPack(r, BROADCAST_VOICE_PERSONAL, BROADCAST_VOICE_STOCK)?.id)
    }

    @Test
    fun `DJ off with same still reads the news using whatever is installed`() {
        // personal outranks stock in VoicePackResolver.preferred()
        assertEquals("personal", resolveNewsPack(resolver("stock", "personal"), NEWS_VOICE_SAME, BROADCAST_VOICE_OFF)?.id)
        assertEquals("stock", resolveNewsPack(resolver("stock"), NEWS_VOICE_SAME, BROADCAST_VOICE_OFF)?.id)
    }

    @Test
    fun `a removed chosen pack falls back to what is installed`() {
        // news wants personal, but only stock is installed any more
        assertEquals("stock", resolveNewsPack(resolver("stock"), BROADCAST_VOICE_PERSONAL, BROADCAST_VOICE_STOCK)?.id)
    }

    @Test
    fun `no voices installed means no reader`() {
        assertNull(resolveNewsPack(resolver(), NEWS_VOICE_SAME, BROADCAST_VOICE_STOCK))
    }
}
