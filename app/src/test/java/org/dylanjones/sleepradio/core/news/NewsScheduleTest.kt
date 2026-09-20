package org.dylanjones.sleepradio.core.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class NewsScheduleTest {

    private fun at(h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 9, 20, h, m, s)

    @Test fun `top of the hour reads top stories`() =
        assertEquals(NewsSlot.TOP_OF_HOUR, NewsSchedule().dueAt(at(17, 3))?.slot)

    @Test fun `half past reads soft stories`() =
        assertEquals(NewsSlot.HALF_PAST, NewsSchedule().dueAt(at(17, 31))?.slot)

    @Test fun `window opens two minutes early and names the coming mark`() {
        val s = NewsSchedule()
        assertNull(s.dueAt(at(16, 57, 59)))
        val due = s.dueAt(at(16, 58))
        assertEquals(NewsSlot.TOP_OF_HOUR, due?.slot)
        assertEquals(s.dueAt(at(17, 4))?.key, due?.key)
    }

    @Test fun `window closes eight minutes after the mark`() {
        val s = NewsSchedule()
        assertEquals(NewsSlot.HALF_PAST, s.dueAt(at(17, 38, 59))?.slot)
        assertNull(s.dueAt(at(17, 39)))
        assertNull(s.dueAt(at(17, 50)))
    }

    @Test fun `each mark is read once`() {
        val s = NewsSchedule()
        val due = s.dueAt(at(17, 1))!!
        s.markRead(due)
        assertNull(s.dueAt(at(17, 5)))
        assertNull(s.prepAt(at(17, 5)))
        // the next mark is unaffected
        assertEquals(NewsSlot.HALF_PAST, s.dueAt(at(17, 29))?.slot)
    }

    @Test fun `marks are distinct across hours and days`() {
        val s = NewsSchedule()
        val a = s.dueAt(at(9, 0))!!
        val b = s.dueAt(at(10, 0))!!
        val c = s.dueAt(LocalDateTime.of(2026, 9, 21, 9, 0))!!
        assertNotEquals(a.key, b.key)
        assertNotEquals(a.key, c.key)
    }

    @Test fun `prep opens fifteen minutes before a mark, earlier than due`() {
        val s = NewsSchedule()
        assertNull(s.prepAt(at(16, 44)))
        assertEquals(NewsSlot.TOP_OF_HOUR, s.prepAt(at(16, 45))?.slot)
        assertNull(s.dueAt(at(16, 45)))
        assertEquals(s.dueAt(at(17, 0))?.key, s.prepAt(at(16, 45))?.key)
    }

    @Test fun `no bulletin in the middle of a gap between marks`() {
        val s = NewsSchedule()
        assertNull(s.dueAt(at(17, 20)))
        assertNull(s.prepAt(at(17, 12)))
    }

    @Test fun `crosses midnight`() {
        val s = NewsSchedule()
        assertEquals(NewsSlot.TOP_OF_HOUR, s.dueAt(at(23, 59))?.slot)
        assertEquals(NewsSlot.TOP_OF_HOUR, s.dueAt(LocalDateTime.of(2026, 9, 21, 0, 1))?.slot)
        assertEquals(s.dueAt(at(23, 59))?.key, s.dueAt(LocalDateTime.of(2026, 9, 21, 0, 1))?.key)
    }
}

class BulletinTimeLineTest {
    private val mark = LocalDateTime.of(2026, 9, 20, 17, 30)

    @Test fun `before the mark it is coming up to it`() =
        assertEquals("It's coming up to half past five.", bulletinTimeLine(mark, mark.minusMinutes(1)))

    @Test fun `just after the mark it is just gone it`() {
        assertEquals("It's just gone half past five.", bulletinTimeLine(mark, mark))
        assertEquals("It's just gone half past five.", bulletinTimeLine(mark, mark.plusMinutes(3)))
        assertEquals("It's just gone half past five.", bulletinTimeLine(mark, mark.plusMinutes(5)))
    }

    @Test fun `top of the hour is worded o'clock`() =
        assertEquals(
            "It's just gone six o'clock.",
            bulletinTimeLine(LocalDateTime.of(2026, 9, 20, 18, 0), LocalDateTime.of(2026, 9, 20, 18, 2)),
        )

    @Test fun `a late gap falls back to the real clock, not just gone`() {
        val line = bulletinTimeLine(mark, mark.plusMinutes(7))
        assertEquals("It's twenty-five minutes to six.", line)
    }
}
