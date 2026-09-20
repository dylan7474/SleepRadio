package org.dylanjones.sleepradio.core.news

import org.dylanjones.sleepradio.media.child
import org.dylanjones.sleepradio.media.children
import org.dylanjones.sleepradio.media.parsePubDate
import org.dylanjones.sleepradio.media.parseXmlSecurely
import org.dylanjones.sleepradio.media.stripHtml
import org.dylanjones.sleepradio.media.text
import org.w3c.dom.Element

/**
 * Parse an RSS 2.0 news feed into headlines, in document order. Items with no
 * title are skipped; a channel-less document throws (caller treats it as a
 * failed fetch). Pure — no Android dependency, testable on the host JVM.
 */
internal fun parseNewsRss(xml: String, source: String): List<NewsHeadline> {
    val doc = parseXmlSecurely(xml)
    val channel = doc.getElementsByTagName("channel").item(0) as? Element
        ?: error("no <channel> in feed")
    return channel.children("item").mapNotNull { item ->
        val title = item.child("title")?.text().orEmpty().trim()
        if (title.isBlank()) return@mapNotNull null
        NewsHeadline(
            title = title,
            summary = stripHtml(item.child("description")?.text().orEmpty()),
            pubDateMs = parsePubDate(item.child("pubDate")?.text()),
            source = source,
        )
    }
}
