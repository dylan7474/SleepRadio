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

/**
 * Keyless public RSS feeds (no API key, no account), all BBC News feeds.
 *
 * Terms (checked 2026-09-20, BBC Terms of Use section 15 "Metadata and RSS feeds",
 * https://www.bbc.co.uk/usingthebbc/terms-of-use/#15metadataandrssfeeds): personal use is
 * fine provided the feed isn't changed, the BBC is credited as "BBC News" / bbc.co.uk/news
 * (text plus a hyperlink nearby) and no BBC logos are used; **business use needs the BBC's
 * permission and may carry a fee**. So: this must stay a free, non-commercial app, and the
 * credit must stay — spoken in every bulletin ([buildBulletinBody]) and shown with a link in
 * the Broadcast voice dialog and About.
 */
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
