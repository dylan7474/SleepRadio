package org.dylanjones.sleepradio.core.news

/**
 * Which bulletin: top stories on the hour, softer stories at half past.
 * (Decided with the user 2026-09-20 — see Phase 10 in the plan doc.)
 */
enum class NewsSlot { TOP_OF_HOUR, HALF_PAST }

/** One story from a feed. [title] is the raw feed text — tidy it with [tidyHeadline] before speaking. */
data class NewsHeadline(
    val title: String,
    val summary: String = "",
    val pubDateMs: Long? = null,
    /** Feed name, used to interleave sources so one feed can't dominate a bulletin. */
    val source: String = "",
)

data class NewsFeed(val name: String, val url: String)

/** Keyless public RSS feeds (no API key, no account). */
object NewsFeeds {
    val TOP_STORIES = listOf(
        NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/rss.xml"),
    )
    val SOFT_STORIES = listOf(
        NewsFeed("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
        NewsFeed("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
        NewsFeed("BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml"),
        NewsFeed("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml"),
    )

    fun forSlot(slot: NewsSlot): List<NewsFeed> = when (slot) {
        NewsSlot.TOP_OF_HOUR -> TOP_STORIES
        NewsSlot.HALF_PAST -> SOFT_STORIES
    }
}
