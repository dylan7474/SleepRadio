package org.dylanjones.sleepradio.core.news

import java.time.LocalDateTime

/** A bulletin that is due: which [slot], and its [key] (the clock mark it belongs to, so it is read once). */
data class DueNews(val slot: NewsSlot, val key: String, val mark: LocalDateTime)

/**
 * When the on-the-hour (top stories) and half-past (soft stories) bulletins are read.
 *
 * Bulletins are only ever read in the *gap between tracks* — a track is never cut off —
 * so a bulletin can't land exactly on :00/:30. Instead each mark has a window: it opens
 * [EARLY_MIN] minutes before the mark (so a gap just short of it still reads it, worded
 * from the real clock as "coming up to ten o'clock") and closes [LATE_MIN] minutes after
 * it, to the end of that minute (after which the mark is skipped — a stale bulletin is
 * worse than none). Each mark is read at most once.
 *
 * Pure and clock-injected. Not thread-safe — driven from the main thread.
 */
class NewsSchedule {
    private val read = HashSet<String>()

    /** The bulletin to read in a gap at [now], or null if none is due (or it was already read). */
    fun dueAt(now: LocalDateTime): DueNews? = windowAt(now, EARLY_MIN)

    /**
     * The bulletin to start preparing at [now]: the same marks as [dueAt] but opening
     * [PREP_LEAD_MIN] earlier, since fetching and synthesising a bulletin takes several
     * seconds and only a track start can trigger it.
     */
    fun prepAt(now: LocalDateTime): DueNews? = windowAt(now, PREP_LEAD_MIN)

    /** Record that [due] has been read, so neither [dueAt] nor [prepAt] returns it again. */
    fun markRead(due: DueNews) {
        read += due.key
    }

    private fun windowAt(now: LocalDateTime, leadMin: Int): DueNews? {
        val minuteOfHalf = now.minute % 30
        val mark = when {
            minuteOfHalf <= LATE_MIN -> now.withMinute(now.minute - minuteOfHalf)
            30 - minuteOfHalf <= leadMin -> now.withMinute(now.minute - minuteOfHalf).plusMinutes(30)
            else -> return null
        }.withSecond(0).withNano(0)
        val key = mark.toString()
        if (key in read) return null
        val slot = if (mark.minute == 0) NewsSlot.TOP_OF_HOUR else NewsSlot.HALF_PAST
        return DueNews(slot, key, mark)
    }

    companion object {
        const val EARLY_MIN = 2
        const val LATE_MIN = 8
        const val PREP_LEAD_MIN = 15
    }
}
