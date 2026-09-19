# SleepRadio

A bedside audio player for Android. It plays **one main source** — a local music
folder, an audiobook, an internet‑radio station, or a **self‑hosted "Broadcast
Radio" station** that auto‑DJs your music folder with an offline text‑to‑speech
presenter — and layers **two ambient channels** underneath it: procedural
coloured noise and binaural beats. A sleep timer fades and stops the main source
on schedule; the ambient channels keep playing.

Built for a Pixel 9, in Kotlin + Jetpack Compose — also verified running on a
Galaxy S8+ (Android 9). Three selectable skins —
**Neon** (cyberpunk cyan/magenta) and **Industrial** (brushed steel, blue/amber)
share one control layout; **Studio** (warm hi‑fi console) puts the controls in a
single row and adds a pair of analogue L/R VU meters.

---

## Features

**Channel A — main source**
- Local music from a folder you pick (SAF), any sub‑folder with audio files is an album
- Audiobooks (SAF folder of books) with per‑book resume, saved every 5 s and on pause
- Internet radio: a bundled starter set, **manual add** (name + URL), or **browse the
  online directory** (radio‑browser.info)
- ICY / Shoutcast now‑playing metadata for radio, with a scrolling marquee for long titles
- Embedded cover art (no image library — `MediaMetadataRetriever` / `loadThumbnail`)

**Broadcast Radio — the auto‑DJ**
- Assign a preset to **📻 SleepRadio broadcast**: it self‑selects tracks from your
  music folder (recency‑ and artist‑spaced shuffle) and plays them back to back
- An **offline text‑to‑speech presenter** (Piper / sherpa‑onnx, fully on‑device)
  reads a short link in the gap before some tracks — a time‑of‑day welcome, then
  "that was … / coming up …", the occasional station ident, and a spoken time
  check that reads the clock as it will be when *heard*, not when it was prepared
- **Voice**: *Off* (music only), a **stock voice** (one‑tap download or file
  import), or **your own** cloned Piper voice, imported on your device and never
  uploaded or committed
- **Announcer level & speed** sliders — the DJ voice rides on top of the VOL knob,
  and reads slower or faster (70–130 %) to taste; the voice is EQ'd for clarity on
  small / in‑car speakers (high‑pass, low‑mid cut, presence lift)
- **Chattiness**: a link every 1 / 2 / 3 / 5 tracks — *Maximum* introduces and
  back‑announces every single track
- **Jingles**: point it at a folder of your own station idents / stings and one
  drops in every N tracks (1–10), shuffled with no immediate repeat and sequenced
  back‑announce → jingle → next‑track intro
- **Wind‑down**: once the sleep timer is armed the DJ eases off (and jingles
  stop), then goes silent for the last few minutes while the music fades
- **Even levels**: every rotation track and jingle is loudness‑matched on the fly —
  a quick decode‑scan of the file about to play sets a per‑item gain (up *or* down,
  with a soft limiter), so wildly inconsistent masters don't jump in volume.
  A 3‑track lookahead keeps picks pre‑scanned ahead of the one playing, so a few
  skips in a row each land on an already‑levelled track instead of falling back
  to unity gain while a fresh scan catches up. Nothing is pre‑scanned or written
  to storage
- **Tight segues**: that same scan finds each track's *and jingle's* trailing
  digital black (and any leading silence) and clips it, so the DJ comes in
  right after the music instead of after a dead gap — with guards so a real
  quiet fade‑out is left alone
- A track that won't decode is skipped instead of stalling the show
- **Clear speech**: numbers and dates in a track/artist string are spoken as
  words, not digit‑by‑digit — "1984" reads as "nineteen eighty‑four", not
  "one nine eight four". A small rule‑based normalizer (no model, no network),
  the same idea the spoken clock already used, just applied to track text too
- **Ready before you tap**: the TTS voice, the jingle loudness scans, and the
  track pool are all pre‑warmed in the background as soon as Broadcast is
  configured — not only once the preset is tapped — so starting the show
  doesn't pay for that prep cold. A small dot on the LIVE preset shows once
  it would start instantly
- No API keys, works with no network once the voice is installed (the only
  network use is the one‑time voice download, which is also avoidable)

> **Naming your files for Broadcast Radio.** Until the tags‑not‑filename item
> below lands, the auto‑DJ reads track/artist straight off the **filename**
> (a full per‑file tag read over a real library is the slow path this was
> specifically written to avoid). It strips a leading track number only when
> it's followed by punctuation: `^\s*\d{1,3}\s*[-._)]+\s*` — so
> `NN - Title.ext` (e.g. `04 - Idiot Wind.mp3`) displays as **Idiot Wind**,
> while a plain space (`04 Idiot Wind.mp3`) is left untouched and a title that
> genuinely starts with a number (`50 Ways to Leave Your Lover`) is never
> mistaken for one. That punctuation requirement is deliberate, not a bug —
> loosening it to match a bare space would start stripping real title text.
> Point a library‑tagging tool (beets + AcoustID/MusicBrainz fingerprinting
> works well) at `NN - Title.ext` as its target filename format and this all
> just works; embedded tags (artist/album/title) can be anything correct,
> since only the filename feeds the DJ.

**Channel B — coloured noise**
- White · pink · brown · blue · deep space · ambient (pink + slow LFO)
- Procedural, on its own `AudioTrack` + writer thread; BAL knob crossfades A ↔ B

**Channel C — binaural beats**
- Sleep 3 Hz · Meditate 4.5 Hz · Relax 6 Hz · Focus 10 Hz presets, plus a level slider
- Stereo detuned sines; sits outside the BAL crossfade

**Mixer & timer**
- **VOL** = master gain, **BAL** = equal‑power A ↔ B crossfade with a perceptual
  taper, so knob travel near each extreme gets finer control over the quiet channel
  instead of being crammed into a few degrees
- Sleep timer (5–90 min): fades Channel A over the last 20 s, then pauses it — B and C
  continue
- Ambient survives the sleep timer *and* an app‑swipe (dedicated foreground service);
  stops itself on headphone/BT unplug
- 3 saveable **PATTERN** slots (a noise + binaural combo); the last mix is restored on launch
- Keeps the screen awake while you set things up; lets it sleep once the timer is running

**Elsewhere**
- Live audio‑reactive visualiser strip (`Visualizer` on the output mix; decorative
  fallback if `RECORD_AUDIO` is declined) — replaced by the VU meters on the Studio skin
- **Studio skin**: single control row + two analogue L/R VU meters driven by the
  music and the DJ voice (not the ambient channels), with an output‑latency
  "sync" setting per output route and an optional mic auto‑calibration that
  beeps until it converges
- Navigation drawer: Now playing · Ambient mix · Sleep timer · Radio stations ·
  Broadcast voice · skin · VU meter sync · Backup & restore · About
- **Backup & restore**: zips DataStore settings, source slots, audiobook/podcast
  progress, and any installed voice packs into one file you choose where to
  keep, and restores them back live. SAF folder grants (Music/Audiobooks/
  Jingles) can't be backed up — Android revokes those on reinstall regardless —
  so a restore tells you which folders to re‑pick instead of silently pointing
  at a URI it can no longer read
- Portrait‑locked, edge‑to‑edge, predictive back, TalkBack labels on the controls

---

## Build & run

Requires the Android SDK (compileSdk 37) and a JDK 17+ (Android Studio's bundled JBR works).

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (mixer math, sleep scale, settings codecs, broadcast selector /
# show-clock / DJ scripts / spoken time, voice-pack resolve & install,
# loudness gain / voice-EQ biquads)
./gradlew :app:testDebugUnitTest

# Signed release APK (R8 minified).
# assembleRelease pulls lint-gradle; add the -x flags to skip it offline.
./gradlew :app:assembleRelease \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
```

The APK is **arm64‑v8a only** (`abiFilters`) and ~36 MB — the sherpa‑onnx TTS
native libs (ONNX Runtime + eSpeak‑NG) are ~31 MB of that and can't be a
post‑install download. App code minifies to ~4.5 MB. Voice models are **not** in
the APK: the stock voice downloads on first use of Broadcast mode (or import a
`.zip`), a personal voice is import‑only.

Release signing reads `keystore.properties` at the repo root (git‑ignored, alongside
the `.jks`); without it the release build falls back to debug signing.

Distribution is sideload — `adb install -r app/build/outputs/apk/…/app-*.apk`.

`tools/build-stock-voice.sh` builds the stock‑voice `.zip` that Broadcast mode
downloads (Piper `en_GB-southern_english_female-low` + English‑only
`espeak-ng-data`, ~1.8 MB), and prints its SHA‑256 for
`VoicePackInstaller.STOCK_SHA256`.

### Stack

Kotlin 2.2.10 · AGP 9.3.2 · KSP 2.2.10‑2.0.2 · Compose BOM 2026.02.01 ·
Media3 1.9.4 · Hilt 2.60.1 · Room 2.8.4 · DataStore 1.2.1 · coroutines 1.11.0 ·
sherpa‑onnx 1.13.4 (Piper TTS, via JitPack) · `minSdk 28`, `target/compileSdk 37`.

### Layout

```
core/audio      MixerState/Controller, NoiseGenerator, BinauralGenerator, AmbientPattern,
                GainAudioProcessor (broadcast levelling + VU peak metering) + TrackProbe
                (JIT loudness gain + edge-silence trim), VoiceEq (DJ voice EQ),
                VuCalibrator (VU-meter mic latency calibration)
core/broadcast  BroadcastSelector, ShowClock, DjScriptBuilder (auto-DJ scripting)
core/data       SourceModels, SettingsRepository (DataStore), RadioDirectory, Room DB
core/design     AppSkin / SkinColors / PlayerLayout, Neon · Industrial · Studio skins,
                RotaryKnob, SkinComponents
core/tts        OfflineTtsEngine (sherpa-onnx), DjVoicePlayer, VoicePack + install/resolve
feature/player  PlayerRoute / PlayerViewModel / PlayerScreen + dialogs, VuMeters,
                VuSyncDialog, BroadcastVoiceViewModel
feature/root    RootViewModel (skin selection)
media           MusicRepository (SAF folder walk + jingle folder), AudiobookRepository
playback        PlaybackService (Media3, Channel A, + gain stage + VU level sampler),
                PlaybackConnection (+ broadcast segue: links, jingles, time checks),
                AmbientPlaybackService (B/C)
tools/          build-stock-voice.sh
```

Full build history and design notes live in the commit log; the original phased
plan covered Phase 0 → Phase 8 (foundation → shippable v1) plus Phase 9
(Broadcast Radio), all done.

---

## Roadmap (post‑v1)

Phase 9 (Broadcast Radio) has landed, plus a run of post‑v1 Broadcast work:
Maximum chattiness, announcer level/speed, bring‑your‑own jingles, spoken time
checks projected from the decoded track length, decode‑error skip, just‑in‑time
per‑track loudness levelling, a fixed DJ‑voice EQ, and track‑edge silence
trimming — with a couple of first‑launch fixes (the Broadcast preset now
responds to the first tap after a cold start). Also the **Studio skin** —
single control row and analogue L/R VU meters, output‑latency synced per route
with a mic auto‑calibration. Most recently, edge‑silence trimming was
extended to jingles: the whole jingle folder is pre‑scanned at broadcast
start (a handful of short files, cheap to do up front) so every jingle plays
clipped, same as a rotation track — with the startup jingle's own scan
awaited on its own ahead of the rest, since it plays within a few seconds of
the welcome line and otherwise loses the race for a decoder against the
whole folder scanning at once. Since then: **Android 9+ / Galaxy S8+
compatibility** (`minSdk` 31 → 28, verified end‑to‑end on a physical S8+, not
just a Pixel 9); **Backup & restore** for settings, source slots,
audiobook/podcast progress and voice packs (SAF folder grants can't be backed
up — Android revokes those on reinstall regardless — so a restore just tells
you which folders to re‑pick); a **speech normalizer** so numbers and dates
in track text are spoken as words instead of digit‑by‑digit; and a pass on
**Broadcast start‑up latency** — a widened loudness look‑ahead (survives a
few skips in a row without falling back to unlevelled audio) had made cold
starts slower, traced to unthrottled decode concurrency starving the TTS
voice load of CPU on weaker hardware, fixed with a decode‑concurrency cap
plus pre‑warming the voice/jingles/track‑pool in the background as soon as
Broadcast is configured — not only once the preset is tapped — with a small
"ready" dot on the LIVE preset once that prep is done. Also fixed: every
restored Float setting (announcer volume/speed) corrupted DataStore on
restore — JSON has no Float type, so the value silently got stored under the
wrong typed preference key, crash‑looping the app the next time Broadcast
read it. Nothing below is committed — it's the shortlist for after more real
bedside use.

**Audio & sources**
- [ ] Auto‑reconnect for dropped radio streams (backoff + a "reconnecting…" state)
- [ ] Broadcast: pull track/artist from embedded tags, not the album‑folder name
      (also the prerequisite for spoken track facts). Until then, name files
      `NN - Title.ext` — see the note under Broadcast Radio above for why.
- [ ] Broadcast: talk over the intro — duck the bed under the DJ link instead of
      a hard cut (edge‑silence trimming already tightens the gap)
- [ ] Make the DJ‑voice EQ adjustable — a "voice tone" (dark ↔ bright) or
      Bass/Presence control in the Broadcast voice dialog
- [ ] Extend loudness levelling + silence trimming to ordinary folder playback,
      not just the Broadcast rotation; real‑time loudness for internet radio
      (nothing to pre‑scan there)
- [ ] Weather & news readouts for the Broadcast DJ (keyless sources, opt‑in,
      inert offline)
- [ ] Per‑station now‑playing providers — e.g. Radio Paradise's JSON API for cover art and
      exact track timing, ICY staying the generic fallback
- [ ] "Stop at end of chapter / track" option for audiobooks (deferred from the sleep timer)
- [ ] Playback speed for audiobooks again, if it turns out to be missed
- [ ] Fade‑*in* on start, and a short crossfade when switching sources

**On‑device AI (exploratory)**
- [x] DJ "personality" beyond reading track names — on‑device Gemini Nano
      (ML Kit GenAI Prompt API, via AICore) generates the occasional richer
      LINK‑gap commentary on top of (never instead of) the template pool,
      with its own off‑by‑default toggle in the Broadcast voice dialog
      ("AI commentary (experimental)"), a download step when the model isn't
      on‑device yet, and a hard timeout that falls back to the already‑queued
      template line if the model is slow, unavailable, or gives an unusable
      reply. Never touches the spoken clock, idents, or a jingle‑split gap —
      only a plain link. **Built and run on a Pixel 9, 2026‑09‑19** —
      Broadcast plays normally through the template‑only fallback (confirmed
      via logcat across several track transitions, no crashes), and the
      toggle correctly stays off with "Not available on this device" shown.
      The actual AI‑generated line couldn't be exercised yet: this Pixel 9
      currently reports Gemini Nano as unavailable despite AICore/Play
      Services both being installed and current — a device‑side rollout/
      provisioning gate, not a bug here. See Phase 18 in the plan doc.
- [ ] AICore/Gemini Nano as an *optional* enhancement layer on top of the
      rule‑based speech normalizer, for mispronunciations a fixed rule table
      can't reasonably cover — never a requirement: only a narrow set of
      devices support it (recent Pixels, some Samsung flagships via Play
      Services), so the S8+ and most other hardware always uses the
      rule‑based path. The DJ‑personality feature above already built the
      shared "is a model available / run a prompt / fall back cleanly"
      plumbing this would reuse (`DjCommentaryEngine`) — this item is now
      just wiring it into the normalizer's output, not new infrastructure.

**Ambient**
- [ ] A real settings screen: custom binaural carrier/beat, an independent noise level,
      the noise palette — replacing the interim "Ambient mix" dialog
- [ ] More noise models (rain, ocean, fan) — still procedural where possible
- [ ] "Ramp down" ambient too: an optional slow taper on B/C over N hours

**UX & platform**
- [ ] Studio skin polish: engraved VU scale numbers, a tidier needle rest pose,
      and a separate (smaller) sync delay for the DJ‑voice path
- [ ] Home‑screen widget and/or a quick‑settings tile (start last mix, toggle sleep timer)
- [ ] The Industrial "PATTERN board" as a fuller mixer screen (PATTERN 1–3 + a morph knob)
- [ ] Lock‑screen / notification controls polish; media‑button (headset) handling
- [ ] Alarm side: wake to a chosen source at a set time
- [ ] Reduced‑motion honouring (kill the marquee / radar / pulsing glow), 120 fps pass
- [ ] Themed‑icon + widget previews; a proper app screenshot set

**Engineering**
- [ ] Compose UI tests + both‑skin screenshot tests (only unit tests exist today)
- [ ] Re‑try `isShrinkResources` with a `res/raw/keep.xml` for the font resource it strips
- [ ] Baseline profile for faster cold start
- [ ] Crash/ANR reporting hook (opt‑in)

---

## Credits

- Online station directory: **[radio‑browser.info](https://www.radio-browser.info/)**
  (community‑run, public domain)
- On‑device TTS: **[sherpa‑onnx](https://github.com/k2-fsa/sherpa-onnx)** (Apache‑2.0),
  **[Piper](https://github.com/rhasspy/piper)** VITS voices (MIT),
  **[eSpeak‑NG](https://github.com/espeak-ng/espeak-ng)** phonemiser (GPL‑3.0‑or‑later)
- The stock voice is Piper's `en_GB-southern_english_female-low` (dataset CC‑BY‑SA 4.0)

Everything else is first‑party.

---

## License

**GPL‑3.0‑or‑later** — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Broadcast Radio mode does on‑device text‑to‑speech through sherpa‑onnx, which
statically links **eSpeak‑NG (GPL‑3.0‑or‑later)** for phonemisation. A released
APK therefore contains GPL code, so the whole project is GPL‑3.0‑or‑later. No
voice model ships in the APK or lives in this repo.
