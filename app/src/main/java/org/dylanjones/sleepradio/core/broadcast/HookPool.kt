package org.dylanjones.sleepradio.core.broadcast

import kotlin.random.Random

/**
 * A shuffle-bag of DJ "hook" lines: every hook is used once before any is
 * repeated, and the first hook of a fresh shuffle is never the one just
 * spoken. Plain class, not thread-safe — driven from the single playback
 * thread like [DjScriptBuilder].
 */
class HookPool(hooks: List<String>, private val rng: Random = Random.Default) {
    private val all: List<String> = hooks.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    private val bag: ArrayDeque<String> = ArrayDeque()
    private var last: String? = null

    val size: Int get() = all.size

    /** Next hook, or null when the pool is empty. */
    fun next(): String? {
        if (all.isEmpty()) return null
        if (bag.isEmpty()) {
            val shuffled = all.shuffled(rng)
            bag.addAll(shuffled)
            if (all.size > 1 && bag.first() == last) bag.addLast(bag.removeFirst())
        }
        return bag.removeFirst().also { last = it }
    }
}

/** Parses a hooks asset: one hook per line; blank lines and `#` comments are skipped. */
fun parseHooks(text: String): List<String> =
    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
