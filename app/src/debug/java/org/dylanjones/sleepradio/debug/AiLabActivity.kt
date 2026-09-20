package org.dylanjones.sleepradio.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.dylanjones.sleepradio.core.ai.DjCommentaryEngine
import org.dylanjones.sleepradio.core.broadcast.DJ_SYSTEM_INSTRUCTION
import org.dylanjones.sleepradio.core.broadcast.buildDjCommentaryPrompt
import org.dylanjones.sleepradio.core.broadcast.BroadcastTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import kotlin.random.Random

/**
 * DEBUG ONLY. Runs several prompt strategies for the AI DJ over a fixed list of
 * real library tracks and writes every result to
 * `<externalFiles>/ai_lab.jsonl`, so the variety/quality question can be
 * answered from data instead of from two live samples.
 *
 * Launch: `adb shell am start -n org.dylanjones.sleepradio/.debug.AiLabActivity`
 * Must stay in the foreground — AICore only serves foreground apps.
 */
class AiLabActivity : Activity() {

    private class T(val title: String, val artist: String, val album: String, val year: String, val genre: String) {
        fun bt() = BroadcastTrack(uri = "", title = title, artist = artist, album = album)
        fun facts(): String = buildList {
            if (album.isNotBlank()) add("from the album \"$album\"")
            if (year.isNotBlank()) add("tagged $year")
            if (genre.isNotBlank()) add("genre tag: $genre")
        }.joinToString(", ")
    }

    private class Variant(
        val name: String,
        val system: String,
        val temperature: Float,
        val topK: Int?,
        val prompt: (prev: T, next: T, angle: String, recentLines: List<String>) -> String,
        val angles: List<String>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val tv = TextView(this).apply { textSize = 18f; setPadding(48, 96, 48, 48); text = "AI lab starting…" }
        setContentView(tv)
        CoroutineScope(Dispatchers.Default).launch { run { msg -> runOnUiThread { tv.text = msg } } }
    }

    private suspend fun run(show: (String) -> Unit) {
        val tracks = assets.open("ai_lab_tracks.json").bufferedReader().readText().let { raw ->
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                T(o.getString("title"), o.getString("artist"), o.getString("album"), o.getString("year"), o.getString("genre"))
            }
        }
        val engine = DjCommentaryEngine()
        val status = engine.status()
        if (status != FeatureStatus.AVAILABLE) { show("Gemini Nano status=$status — aborting"); return }
        engine.warmup()

        val out = File(getExternalFilesDir(null), "ai_lab4.jsonl").apply { writeText("") }
        val variants = seventies()
        val pairs = 15
        val total = variants.size * pairs
        var done = 0
        for (v in variants) {
            val rng = Random(42)
            val recent = ArrayDeque<String>()
            for (i in 0 until pairs) {
                val prev = tracks[i]; val next = tracks[i + 1]
                val angle = v.angles.random(rng)
                val prompt = v.prompt(prev, next, angle, recent.toList())
                val t0 = System.currentTimeMillis()
                // AICore rate-limits (error 9 BUSY after ~40 calls in ~100s): pace + back off.
                var raw: String? = null
                for (attempt in 0..4) {
                    raw = withTimeoutOrNull(30_000) { engine.generateLink(v.system, prompt, v.temperature, v.topK) }
                    if (raw != null) break
                    Log.d("AiLab", "null on attempt $attempt, backing off")
                    show("AI lab: backing off (attempt $attempt)…")
                    kotlinx.coroutines.delay(30_000)
                }
                kotlinx.coroutines.delay(6_000)
                val ms = System.currentTimeMillis() - t0
                if (raw != null) { recent.addLast(raw.trim()); while (recent.size > 3) recent.removeFirst() }
                val row = JSONObject()
                    .put("variant", v.name).put("i", i)
                    .put("prev", "${prev.title} — ${prev.artist}").put("next", "${next.title} — ${next.artist}")
                    .put("angle", angle).put("ms", ms).put("out", raw ?: JSONObject.NULL)
                withContext(Dispatchers.IO) { out.appendText(row.toString() + "\n") }
                done++
                show("AI lab: $done / $total\n${v.name}\n\n${raw ?: "(null)"}")
                Log.d("AiLab", "${v.name} #$i (${ms}ms): $raw")
            }
        }
        show("AI lab DONE: $total lines → ${out.absolutePath}")
        Log.d("AiLab", "DONE")
        engine.close()
    }

    /** The 50 adlibs from assets/70s_radio_dj_adlibs.csv (ID,AdLib; quoted fields, may hold commas). */
    private val seventyLines: List<String> by lazy {
        val text = assets.open("70s_radio_dj_adlibs.csv").bufferedReader().readText().removePrefix("﻿")
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val f = StringBuilder(); var q = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                q && c == '"' && text.getOrNull(i + 1) == '"' -> { f.append('"'); i++ }
                c == '"' -> q = !q
                !q && c == ',' -> { row.add(f.toString()); f.clear() }
                !q && c == '\n' -> { row.add(f.toString().trimEnd('\r')); f.clear(); rows.add(row); row = mutableListOf() }
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row.add(f.toString()); rows.add(row) }
        rows.drop(1).mapNotNull { it.getOrNull(1)?.trim()?.takeIf { l -> l.isNotEmpty() } }
    }

    private fun seventies(): List<Variant> {
        fun sys(tone: String) = """
You are a 1970s British radio disc jockey, chatty and cheeky, like a Radio One or local-radio presenter. You speak one short link between two records. Start with a punchy hook in the voice of the examples, then introduce the next record by its title and artist. Total under 40 words. No hashtags, no emoji. Write numbers as words. Never invent facts about the record. $tone Reply with only the spoken line.
""".trim()
        fun prompt(p: T, n: T, ex: List<String>, r: List<String>) =
            "Examples of your voice (do not copy them word for word, write something new):\n" +
                ex.joinToString("\n") { "- $it" } +
                "\nThe record that just finished: \"${p.title}\" by ${p.artist}. The next record: \"${n.title}\" by ${n.artist}." +
                (if (r.isEmpty()) "" else " Your last links were: ${r.joinToString(" | ") { "\"$it\"" }}. Do not reuse their openings.") +
                " Say the link."
        val rng = Random(1)
        return listOf(
            Variant("F_70s_bright", sys("Keep the energy bright and cheerful."), 1.0f, 40,
                { p, n, _, r -> prompt(p, n, seventyLines.shuffled(rng).take(5), r) }, listOf("")),
            Variant("G_70s_hushed", sys("It is the middle of the night and listeners are falling asleep, so keep the same warm cheeky voice but hushed and gentle, with no shouting."), 1.0f, 40,
                { p, n, _, r -> prompt(p, n, seventyLines.shuffled(rng).take(5), r) }, listOf("")),
        )
    }

    private fun variants(): List<Variant> {
        val night = "It's the middle of the night. "
        val baseline = Variant(
            "A_baseline", DJ_SYSTEM_INSTRUCTION, 0.9f, null,
            { p, n, _, r -> buildDjCommentaryPrompt(p.bt(), n.bt(), LocalTime.of(2, 0), r.map { "x" }.take(0)) },
            listOf(""),
        )

        val fewShotSystem = """
You are the overnight announcer on "Sleep Radio", a calm station people fall asleep to. You speak one short link between two songs, live on air.

Every link MUST name the next song and its artist, spoken naturally. Never talk about tempo, pace, energy or mood shifts unless told to.

Style: warm, quiet, understated, a little dry humour. No exclamation marks, no hype. One or two short sentences, under 35 words. Write numbers as words. Reply with only the spoken line.

Examples of the range of what you might say:
- That was Fleetwood Mac. Here's Neil Young, with Harvest Moon, for anyone still awake.
- Quiet out there tonight. Next, Nina Simone, and Wild Is the Wind.
- Good company, that one. Now Joni Mitchell, and A Case of You.
- Bit late for a Tuesday, isn't it? Let's ease into Nick Drake, Pink Moon.
""".trim()
        val nameIt = Variant(
            "B_nameit_fewshot", fewShotSystem, 1.0f, 40,
            { p, n, _, _ -> "Song that just finished: \"${p.title}\" by ${p.artist}. Next song: \"${n.title}\" by ${n.artist}. Say the link." },
            listOf(""),
        )

        val angles = listOf(
            "Name the next song and artist, with one warm, plain remark.",
            "Name the next song and artist, and tie it lightly to the late hour.",
            "Back-announce the song that just ended by name, then introduce the next one.",
            "Name the next song and artist, with a gentle dry joke about its title.",
            "Name the next song and artist, then wish the listener something specific and sleepy.",
            "Introduce the next artist and song like an old friend dropping by.",
        )
        fun avoid(r: List<String>) = if (r.isEmpty()) "" else
            " Your last lines were: ${r.joinToString(" | ") { "\"$it\"" }}. Do not reuse their openings, phrasing, or structure."
        val angled = Variant(
            "C_angled_antirepeat", fewShotSystem, 1.0f, 40,
            { p, n, a, r -> "Song that just finished: \"${p.title}\" by ${p.artist}. Next song: \"${n.title}\" by ${n.artist}. Angle for this link: $a${avoid(r)} Say the link." },
            angles,
        )

        val groundedSystem = fewShotSystem + """

You may mention a fact about the song ONLY if it appears in the facts provided. Never invent trivia, quotes, chart positions, or history. If no fact fits, just introduce the song."""
        val grounded = Variant(
            "D_grounded_facts", groundedSystem, 1.0f, 40,
            { p, n, a, r ->
                val f = n.facts()
                "Song that just finished: \"${p.title}\" by ${p.artist}. Next song: \"${n.title}\" by ${n.artist}." +
                    (if (f.isNotBlank()) " Facts about the next song: $f." else "") +
                    " Angle for this link: $a${avoid(r)} Say the link."
            },
            angles + "Name the next song and artist and mention one fact from the facts given (album or year), if any.",
        )

        val personaSystem = """
You are Vince, the overnight host of "Sleep Radio". You're a retired ship's radio operator who took this graveyard shift for the company and the quiet. You talk to one insomniac listener at a time, like a friend on the phone at 2 a.m.: dry, kind, slightly rambling, never hyped. You have small running habits: you mention your flask of tea, the rain on the studio window, the neighbour's cat, the clock, a wry aside about the song title. You are not a comedian. You do not know facts about songs and never claim any.

Each time you speak, you say one or two short sentences (under 35 words) that ALWAYS include the next song's title and artist. Never comment on tempo, pace, energy or mood shifts. No exclamation marks. Numbers as words. Reply with only what Vince says aloud.
""".trim()
        val persona = Variant(
            "E_persona_vince", personaSystem, 1.0f, 40,
            { p, n, a, r -> "Just played: \"${p.title}\" by ${p.artist}. Coming up: \"${n.title}\" by ${n.artist}. Angle: $a${avoid(r)}" },
            angles + listOf(
                "Mention your flask of tea, then name the next song and artist.",
                "Mention the rain or the weather at the window, then name the next song and artist.",
                "Mention a small thing about the night, then name the next song and artist.",
            ),
        )
        return listOf(baseline, nameIt, angled, grounded, persona)
    }
}
