package org.dylanjones.sleepradio.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelButtonModeTest {
    @Test fun `grid is two pages of four, big is eight pages of one`() {
        assertEquals(2, ChannelButtonMode.GRID.pageCount)
        assertEquals(4, ChannelButtonMode.GRID.perPage)
        assertEquals(8, ChannelButtonMode.BIG.pageCount)
        assertEquals(1, ChannelButtonMode.BIG.perPage)
    }

    @Test fun `full screen is eight pages of one, like big`() {
        assertEquals(8, ChannelButtonMode.FULL.pageCount)
        assertEquals(1, ChannelButtonMode.FULL.perPage)
        assertEquals((0 until 8).toList(), (0 until 8).map(ChannelButtonMode.FULL::pageOf))
        assertEquals(ChannelButtonMode.FULL, ChannelButtonMode.fromId("full"))
    }

    @Test fun `every channel sits on exactly one page in both modes`() {
        for (mode in ChannelButtonMode.entries) {
            val seen = (0 until mode.pageCount).flatMap { mode.channelsOnPage(it).toList() }
            assertEquals("$mode", (0 until PRESET_COUNT).toList(), seen)
            for (i in 0 until PRESET_COUNT) assertEquals("$mode $i", true, i in mode.channelsOnPage(mode.pageOf(i)))
        }
    }

    @Test fun `page of a channel per mode`() {
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), (0 until 8).map(ChannelButtonMode.GRID::pageOf))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), (0 until 8).map(ChannelButtonMode.BIG::pageOf))
    }

    @Test fun `page of an out-of-range channel is clamped, never crashes the pager`() {
        assertEquals(1, ChannelButtonMode.GRID.pageOf(99))
        assertEquals(7, ChannelButtonMode.BIG.pageOf(99))
        assertEquals(0, ChannelButtonMode.BIG.pageOf(-3))
    }

    @Test fun `unknown or missing saved values fall back to the grid`() {
        assertEquals(ChannelButtonMode.GRID, ChannelButtonMode.fromId(null))
        assertEquals(ChannelButtonMode.GRID, ChannelButtonMode.fromId("huge"))
        assertEquals(ChannelButtonMode.BIG, ChannelButtonMode.fromId("big"))
        assertEquals(ChannelButtonMode.GRID, ChannelButtonMode.fromId("grid"))
    }
}
