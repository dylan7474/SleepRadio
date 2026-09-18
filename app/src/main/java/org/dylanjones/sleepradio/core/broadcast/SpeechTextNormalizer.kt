package org.dylanjones.sleepradio.core.broadcast

/**
 * Rewrites a track/artist string into a TTS-friendly spoken form before it's
 * handed to the (fully offline) Piper/sherpa-onnx voice. Numbers read
 * digit-by-digit and a handful of chronically-mispronounced words are the two
 * symptoms this exists for — the same problem [spokenTime] already solves for
 * the spoken clock, just applied to arbitrary title text instead of a fixed
 * `HH:mm`.
 *
 * Deliberately narrow, not a general-purpose text normalizer: it only
 * rewrites standalone number-like tokens (so "Highway 61" and "Apartment 23"
 * both still read naturally) and known-bad words, leaving everything else —
 * including most band/track names — untouched.
 */
internal fun normalizeForSpeech(text: String): String {
    if (text.isBlank()) return text
    val overridden = applyPronunciationOverrides(text)
    return NUMBER_TOKEN.replace(overridden) { normalizeNumberToken(it.value) }
}

/**
 * Case-insensitive whole-word/phrase fixes for specific chronically
 * mispronounced text. Empty by default — add real cases here as they're
 * found (key = the text as it appears, value = a respelling or correction
 * that reads right through espeak-ng; matched as a whole word/phrase,
 * case-insensitively, so casing in the replacement is what's actually said).
 */
private val PRONUNCIATION_OVERRIDES: Map<String, String> = mapOf()

private fun applyPronunciationOverrides(text: String): String {
    var out = text
    for ((bad, good) in PRONUNCIATION_OVERRIDES) {
        out = Regex("(?i)\\b${Regex.escape(bad)}\\b").replace(out, Regex.escapeReplacement(good))
    }
    return out
}

/** A run of digits, optionally with a trailing ordinal suffix (1st, 22nd…). */
private val NUMBER_TOKEN = Regex("\\d+(st|nd|rd|th)?", RegexOption.IGNORE_CASE)

private fun normalizeNumberToken(token: String): String {
    val ordinalSuffix = Regex("(st|nd|rd|th)$", RegexOption.IGNORE_CASE).find(token)?.value
    val digits = if (ordinalSuffix != null) token.dropLast(ordinalSuffix.length) else token
    val n = digits.toIntOrNull() ?: return token
    return if (ordinalSuffix != null) ordinalWords(n) else cardinalOrYearWords(digits, n)
}

/**
 * A bare 4-digit token with no leading zero reads as a year ("1984" -> nineteen
 * eighty-four); everything else (track numbers, catalogue numbers, street
 * numbers…) reads as a plain cardinal.
 */
private fun cardinalOrYearWords(digits: String, n: Int): String =
    if (digits.length == 4 && digits[0] != '0') yearWords(n) else cardinalWords(n)

private fun yearWords(year: Int): String {
    val century = year / 100
    val rest = year % 100
    return when {
        year in 2000..2009 ->
            if (rest == 0) "two thousand" else "two thousand and ${cardinalWords(rest)}"
        rest == 0 -> "${cardinalWords(century)} hundred"
        rest < 10 -> "${cardinalWords(century)} oh ${cardinalWords(rest)}"
        else -> "${cardinalWords(century)} ${cardinalWords(rest)}"
    }
}

private fun ordinalWords(n: Int): String {
    val cardinal = cardinalWords(n)
    val sep = when {
        cardinal.contains('-') -> "-"
        cardinal.contains(' ') -> " "
        else -> null
    } ?: return ordinalSuffixFor(cardinal)
    val head = cardinal.substringBeforeLast(sep)
    val tail = cardinal.substringAfterLast(sep)
    return "$head$sep${ordinalSuffixFor(tail)}"
}

private fun ordinalSuffixFor(word: String): String = ORDINAL_ENDINGS[word] ?: (word + "th")

private val ORDINAL_ENDINGS: Map<String, String> = mapOf(
    "one" to "first", "two" to "second", "three" to "third", "four" to "fourth",
    "five" to "fifth", "eight" to "eighth", "nine" to "ninth", "twelve" to "twelfth",
    "twenty" to "twentieth", "thirty" to "thirtieth", "forty" to "fortieth",
    "fifty" to "fiftieth", "sixty" to "sixtieth", "seventy" to "seventieth",
    "eighty" to "eightieth", "ninety" to "ninetieth", "hundred" to "hundredth",
    "thousand" to "thousandth",
)

private val ONES = arrayOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)
private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
)

/** Cardinal English for any non-negative int up to 999,999 — plenty for a
 * track/catalogue number or year; larger values fall back to plain digits. */
internal fun cardinalWords(n: Int): String {
    if (n < 0) return "minus ${cardinalWords(-n)}"
    if (n < 20) return ONES[n]
    if (n < 100) {
        val tens = TENS[n / 10]
        val rem = n % 10
        return if (rem == 0) tens else "$tens-${ONES[rem]}"
    }
    if (n < 1000) {
        val hundreds = "${ONES[n / 100]} hundred"
        val rem = n % 100
        return if (rem == 0) hundreds else "$hundreds and ${cardinalWords(rem)}"
    }
    if (n < 1_000_000) {
        val thousands = "${cardinalWords(n / 1000)} thousand"
        val rem = n % 1000
        return if (rem == 0) thousands else "$thousands ${cardinalWords(rem)}"
    }
    return n.toString()
}
