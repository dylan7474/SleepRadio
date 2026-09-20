package org.dylanjones.sleepradio.core.news

import org.dylanjones.sleepradio.core.broadcast.cardinalWords
import org.dylanjones.sleepradio.core.broadcast.normalizeForSpeech
import org.dylanjones.sleepradio.core.broadcast.spokenTime
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

private const val MIN_HEADLINE_CHARS = 20
private const val MAX_HEADLINE_CHARS = 170

// "Watch: …", "Live: …", "In pictures: …" — labels for the web page, not for speech.
private val LABEL_PREFIX = Regex(
    "^(watch|live|listen|video|in pictures|pictures|analysis|opinion|review|newscast|podcast)\\s*[:\\-–—]\\s*",
    RegexOption.IGNORE_CASE,
)

// Stories that only make sense on a screen.
private val NOT_SPEAKABLE = Regex(
    "\\b(live updates?|live blog|newsletter|quiz|how to watch|in pictures|photo gallery|" +
        "iplayer|bbc sounds|watch the full|watch live|listen live)\\b",
    RegexOption.IGNORE_CASE,
)

// "Golf: PGA Championship", "Tennis: US Open day two" — a section/event label (up to three
// words, a colon, up to four more), not a story. Real colon headlines have a longer second
// half ("Assisted dying: How laws differ around the world"), a quoted second half
// ("The Deadlifting Grandma: 'I'm a world champion'") or a quoted first half.
private val TOPIC_LABEL = Regex("^\\S+(?:\\s\\S+){0,2}:\\s(?![\'\"‘“])\\S+(?:\\s\\S+){0,3}[.!?]?$")

/**
 * Words that shouldn't reach a sleeper in the soft-stories bulletin. Deliberately blunt:
 * a false positive just skips one story, a miss wakes someone up.
 */
private val GRIM = Regex(
    "\\b(kill(s|ed|ing|er)?|dead|death|deaths|dies|died|dying|murder(s|ed|er)?|shot|shooting|stabb(ed|ing)|" +
        "terror(ist|ism)?|bomb(s|ing|ed)?|war|wars|attack(s|ed)?|crash(es|ed)?|victims?|abus(e|ed|er|ers)|" +
        "rape[sd]?|suicide|massacre|hostages?|missiles?|explosions?|fatal(ity|ities)?|casualt(y|ies)|" +
        "drones?|invasion|genocide|torture[dr]?|cancer|tumou?rs?)\\b",
    RegexOption.IGNORE_CASE,
)

internal fun isGrim(text: String): Boolean = GRIM.containsMatchIn(text)

/**
 * Turn a raw feed title into a sentence fit to read aloud, or null if the story isn't
 * speakable (a live blog, a quiz, a bare "Golf: PGA Championship" label, a promo for
 * video/iPlayer, too short/long to make sense). Rule-based on purpose:
 * it can only drop or trim words, never invent them — see the 2026-09-20 finding that
 * on-device AI can't run with the screen off, so news stays rule-based.
 */
internal fun tidyHeadline(raw: String): String? {
    var t = raw.replace(Regex("\\s+"), " ").trim()
    t = LABEL_PREFIX.replace(t, "").trim()
    if (t.length < MIN_HEADLINE_CHARS || t.length > MAX_HEADLINE_CHARS) return null
    if (NOT_SPEAKABLE.containsMatchIn(t) || TOPIC_LABEL.matches(t)) return null
    // A spaced dash is a page-layout pause; read it as a comma-length break.
    t = t.replace(Regex("\\s[-–—]\\s"), ", ")
    if (t.last() !in ".!?") t += "."
    return t
}

/** Stable identity for "have we read this already / is it a near-duplicate": first six words, letters only. */
internal fun newsKey(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
        .filter { it.isNotEmpty() }.take(6).joinToString(" ")

/**
 * Choose up to [max] headlines to read: newest first within each feed, feeds
 * interleaved (so one feed can't take every slot), un-speakable and already-read
 * stories dropped, near-duplicates collapsed, and — for [NewsSlot.HALF_PAST] only —
 * anything [isGrim] skipped. Returns tidied text.
 */
internal fun pickHeadlines(
    candidates: List<NewsHeadline>,
    slot: NewsSlot,
    alreadyRead: Set<String> = emptySet(),
    max: Int = 3,
): List<String> {
    val perFeed = candidates
        .groupBy { it.source }
        .values
        .map { list -> list.sortedByDescending { it.pubDateMs ?: Long.MIN_VALUE } }
    // Round-robin across feeds.
    val interleaved = buildList {
        var i = 0
        while (perFeed.any { i < it.size }) {
            perFeed.forEach { list -> list.getOrNull(i)?.let(::add) }
            i++
        }
    }
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (h in interleaved) {
        if (out.size >= max) break
        val tidy = tidyHeadline(h.title) ?: continue
        if (slot == NewsSlot.HALF_PAST && (isGrim(h.title) || isGrim(h.summary))) continue
        val key = newsKey(tidy)
        if (key in alreadyRead || !seen.add(key)) continue
        out += tidy
    }
    return out
}

// --- Making news text speakable -------------------------------------------------------
// normalizeForSpeech() only reads plain digit runs (fine for track titles). News is full of
// money, percentages and pence, which it would mangle ("£1m" -> "£onem"), so those are
// spelled out first.

private val MONEY = Regex("([£$€])(\\d[\\d,]*(?:\\.\\d+)?)\\s?(bn|billion|m|million|k|thousand)?\\b", RegexOption.IGNORE_CASE)
private val PERCENT = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s?%")
private val PENCE = Regex("\\b(\\d+)p\\b")
private val GROUPED_OR_DECIMAL = Regex("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+\\.\\d+")

/** "12,000" -> "twelve thousand"; "2.5" -> "two point five". Falls back to the digits if it can't. */
private fun numberWords(raw: String): String {
    val clean = raw.replace(",", "")
    val whole = clean.substringBefore('.')
    val n = whole.toIntOrNull() ?: return raw
    if (n > 999_999) return raw
    val intWords = cardinalWords(n)
    val frac = clean.substringAfter('.', "")
    return if (frac.isEmpty()) intWords
    else intWords + " point " + frac.map { cardinalWords(it - '0') }.joinToString(" ")
}

private fun scaleWord(s: String): String = when (s.lowercase()) {
    "bn", "billion" -> "billion"
    "m", "million" -> "million"
    else -> "thousand"
}

internal fun speakableNews(text: String): String {
    var t = text
    t = MONEY.replace(t) { m ->
        val unit = when (m.groupValues[1]) { "£" -> "pound" ; "$" -> "dollar" ; else -> "euro" }
        val scale = m.groupValues[3]
        val num = numberWords(m.groupValues[2])
        when {
            scale.isNotEmpty() -> "$num ${scaleWord(scale)} ${unit}s"
            m.groupValues[2] == "1" -> "one $unit"
            else -> "$num ${unit}s"
        }
    }
    t = PERCENT.replace(t) { "${numberWords(it.groupValues[1])} percent" }
    t = PENCE.replace(t) { if (it.groupValues[1] == "1") "one penny" else "${numberWords(it.groupValues[1])} pence" }
    t = GROUPED_OR_DECIMAL.replace(t) { numberWords(it.value) }
    t = t.replace(Regex("(?<=[A-Za-z])\\+"), "")   // "LGBTQ+" -> "LGBTQ"
    t = t.replace("&", " and ").replace(Regex("\\s+"), " ")
    return t.trim()
}

// Every bulletin names its source: the BBC's RSS terms (Terms of Use, section 15) require the
// feed to be credited as "BBC News". These lines are the spoken credit.
private val TOP_INTROS = listOf(
    "Here's the news from BBC News.",
    "The headlines, from BBC News.",
    "In the news, from BBC News.",
)
private val SOFT_INTROS = listOf(
    "And now, a few gentler stories from BBC News.",
    "A few softer stories from BBC News.",
    "Something a little lighter, from BBC News.",
)
private const val OUTRO = "Now, back to the music."

/**
 * The spoken bulletin: `<time line> <intro> <headline> <headline> … <outro>`, or null when
 * there's nothing to say. [timeLine] is the accurate spoken time ("It's just gone ten
 * o'clock.") — passed in so the caller can build it as late as possible.
 */
internal fun buildBulletin(
    slot: NewsSlot,
    headlines: List<String>,
    timeLine: String,
    rng: Random = Random.Default,
): String? = buildBulletinBody(slot, headlines, rng)?.let { "$timeLine $it" }

/** The bulletin without its time line: `<intro> <headline> … <outro>`; null when there's nothing to say. */
internal fun buildBulletinBody(
    slot: NewsSlot,
    headlines: List<String>,
    rng: Random = Random.Default,
): String? {
    if (headlines.isEmpty()) return null
    val intros = if (slot == NewsSlot.TOP_OF_HOUR) TOP_INTROS else SOFT_INTROS
    val stories = headlines.map { normalizeForSpeech(speakableNews(it)) }
    return "${intros[rng.nextInt(intros.size)]} ${signpost(stories, rng)} $OUTRO"
}

/**
 * Join [stories] with spoken signposts — "First up," … "Also," … "And finally," — so
 * that consecutive headlines don't blend into one run of sentences. A lone story gets
 * none. The middle ones are drawn without repeats.
 */
internal fun signpost(stories: List<String>, rng: Random = Random.Default): String {
    if (stories.size < 2) return stories.joinToString(" ")
    val middle = MIDDLE_SIGNPOSTS.shuffled(rng).iterator()
    return stories.mapIndexed { i, story ->
        val lead = when (i) {
            0 -> "First up,"
            stories.lastIndex -> "And finally,"
            else -> if (middle.hasNext()) middle.next() else "Also,"
        }
        "$lead $story"
    }.joinToString(" ")
}

private val MIDDLE_SIGNPOSTS = listOf("Also,", "And also,", "Meanwhile,", "In other news,", "Elsewhere,")

/**
 * The bulletin's time line, anchored to the [mark] (:00/:30) it belongs to so it reads like
 * a newsreader — "It's just gone half past five." — and stays literally true for a gap that
 * lands a few minutes either side. Only when the gap is late enough that "just gone" would
 * mislead does it fall back to the real clock, rounded like the DJ's.
 */
internal fun bulletinTimeLine(mark: LocalDateTime, now: LocalDateTime): String {
    val minutesPast = Duration.between(mark, now).toMinutes()
    val markWords = spokenTime(mark.toLocalTime())
    return when {
        minutesPast < 0 -> "It's coming up to $markWords."
        minutesPast <= JUST_GONE_MAX_MIN -> "It's just gone $markWords."
        else -> "It's ${spokenTime(now.toLocalTime())}."
    }
}

private const val JUST_GONE_MAX_MIN = 5L
