package org.dylanjones.sleepradio.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 17 — [podcastFeedId] stability and the [pickPodcastEpisode] preset
 *  resolution (Decision #20: episodes stop at the end, no auto-advance, so a
 *  preset tap must pick up wherever the listener left off). */
class PodcastResumeTest {

    // --- podcastFeedId -----------------------------------------------------

    @Test
    fun `same url always gives the same id`() {
        val url = "https://example.com/feed.xml"
        assertEquals(podcastFeedId(url), podcastFeedId(url))
    }

    @Test
    fun `id is insensitive to case and a trailing slash`() {
        val a = podcastFeedId("https://Example.com/feed.xml/")
        val b = podcastFeedId("https://example.com/feed.xml")
        assertEquals(a, b)
    }

    @Test
    fun `different feeds get different ids`() {
        assertNotEquals(podcastFeedId("https://a.example/feed.xml"), podcastFeedId("https://b.example/feed.xml"))
    }

    // --- pickPodcastEpisode --------------------------------------------------

    private fun ep(guid: String) = PodcastEpisode(guid = guid, title = guid, audioUrl = "https://x/$guid.mp3")

    private fun progress(guid: String, positionMs: Long, completed: Boolean, updatedAt: Long) =
        PodcastProgress(guid, positionMs, durationMs = 1_800_000L, completed = completed, updatedAt = updatedAt)

    @Test
    fun `no episodes yields null`() {
        assertNull(pickPodcastEpisode(emptyList(), emptyMap()))
    }

    @Test
    fun `nothing played yet picks the newest episode from the top`() {
        val episodes = listOf(ep("e2"), ep("e1")) // newest-first, as the repo returns them
        val (episode, position) = pickPodcastEpisode(episodes, emptyMap())!!
        assertEquals("e2", episode.guid)
        assertEquals(0L, position)
    }

    @Test
    fun `resumes the most recently touched in-progress episode`() {
        val episodes = listOf(ep("e3"), ep("e2"), ep("e1"))
        val progress = mapOf(
            "e2" to progress("e2", positionMs = 300_000L, completed = false, updatedAt = 200L),
            "e1" to progress("e1", positionMs = 900_000L, completed = false, updatedAt = 100L),
        )
        val (episode, position) = pickPodcastEpisode(episodes, progress)!!
        assertEquals("e2", episode.guid) // touched more recently than e1
        assertEquals(300_000L, position)
    }

    @Test
    fun `skips completed episodes to the newest not yet heard`() {
        val episodes = listOf(ep("e3"), ep("e2"), ep("e1"))
        val progress = mapOf(
            "e3" to progress("e3", positionMs = 1_800_000L, completed = true, updatedAt = 300L),
        )
        val (episode, position) = pickPodcastEpisode(episodes, progress)!!
        assertEquals("e2", episode.guid)
        assertEquals(0L, position)
    }

    @Test
    fun `everything heard falls back to the newest episode`() {
        val episodes = listOf(ep("e2"), ep("e1"))
        val progress = mapOf(
            "e2" to progress("e2", positionMs = 1_800_000L, completed = true, updatedAt = 200L),
            "e1" to progress("e1", positionMs = 1_800_000L, completed = true, updatedAt = 100L),
        )
        val (episode, _) = pickPodcastEpisode(episodes, progress)!!
        assertEquals("e2", episode.guid)
    }

    @Test
    fun `a zero-position progress row does not count as in-progress`() {
        val episodes = listOf(ep("e2"), ep("e1"))
        val progress = mapOf("e2" to progress("e2", positionMs = 0L, completed = false, updatedAt = 999L))
        val (episode, position) = pickPodcastEpisode(episodes, progress)!!
        assertEquals("e2", episode.guid) // still the newest not-completed episode
        assertEquals(0L, position)
    }
}
