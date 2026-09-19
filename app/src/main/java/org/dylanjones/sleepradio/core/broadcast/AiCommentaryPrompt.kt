package org.dylanjones.sleepradio.core.broadcast

import java.time.LocalTime

/**
 * Persona + tone constraints for Phase 18's on-device AI DJ commentary (see
 * [org.dylanjones.sleepradio.core.ai.DjCommentaryEngine]). Kept as plain text
 * so it reads exactly as sent to the model — no template escaping.
 */
internal val DJ_SYSTEM_INSTRUCTION: String = """
You are the late-night announcer on "Sleep Radio", a calm internet station
people listen to while falling asleep. When asked, speak ONE short, natural
link between two songs, as if reading it live on air right now.

Tone: warm, relaxed, understated. A little dry wit is welcome. Never loud,
never excited, no exclamation marks, no hype.

Rules:
- One or two short sentences, well under 40 words total.
- Spell out any number in words — never write digits (say "eleven", not "11").
- Reply with ONLY the line to be spoken. No quotation marks, no stage
  directions like "(pause)", no preamble like "Sure, here's a line:".
""".trimIndent()

/**
 * Builds the user-turn prompt for one DJ link: the track that just finished,
 * the one coming up, and (optionally) a couple of earlier tracks the model
 * may casually call back to. Pure and deterministic apart from [now]/the
 * caller's track data, so it's unit-testable without AICore.
 */
internal fun buildDjCommentaryPrompt(
    previous: BroadcastTrack?,
    next: BroadcastTrack?,
    now: LocalTime,
    recentHistory: List<String> = emptyList(),
): String = buildString {
    append(if (now.hour in 0..4) "It's the middle of the night. " else "It's late evening. ")
    if (previous != null) {
        append("The song that just finished was \"${previous.title}\" by ${previous.artist}. ")
    }
    if (next != null) {
        append("The next song is \"${next.title}\" by ${next.artist}. ")
    }
    if (recentHistory.isNotEmpty()) {
        append("Earlier tonight you already played: ${recentHistory.joinToString("; ")}. ")
        append("You may make a brief callback to one of these if it fits naturally, but don't force it. ")
    }
    append("Say the link now.")
}

private const val MIN_AI_LINE_LENGTH = 6
private const val MAX_AI_LINE_LENGTH = 260

/**
 * Cleans and sanity-checks a raw model response before it's ever handed to
 * TTS. Returns null for anything that doesn't look like one clean spoken
 * line — the caller falls back to the already-installed template line, so
 * being strict here costs nothing but a slightly-less-often AI line.
 */
internal fun sanitizeAiLine(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    // Collapse to a single line and normalise internal whitespace.
    text = text.replace(Regex("\\s+"), " ").trim()
    // Strip one layer of wrapping quotes the model sometimes adds despite
    // being told not to ("Line here." -> Line here.).
    if (text.length >= 2 && text.first() in "\"'“" && text.last() in "\"'”") {
        text = text.substring(1, text.length - 1).trim()
    }
    if (text.length < MIN_AI_LINE_LENGTH || text.length > MAX_AI_LINE_LENGTH) return null
    // A markdown/code artefact or a refusal-shaped reply is not speakable.
    if (text.any { it == '*' || it == '#' || it == '`' } ) return null
    return normalizeForSpeech(text)
}
