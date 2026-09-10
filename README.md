# SleepRadio

A bedside audio player for Android. It plays **one main source** — a local music
folder, an audiobook, an internet‑radio station, or a **self‑hosted "Broadcast
Radio" station** that auto‑DJs your music folder with an offline text‑to‑speech
presenter — and layers **two ambient channels** underneath it: procedural
coloured noise and binaural beats. A sleep timer fades and stops the main source
on schedule; the ambient channels keep playing.

Built for a Pixel 9, in Kotlin + Jetpack Compose. Two selectable visual skins
(**Neon** — cyberpunk cyan/magenta; **Industrial** — brushed steel, blue/amber),
one shared control layout.

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
  Nothing is pre‑scanned or written to storage
- A track that won't decode is skipped instead of stalling the show
- No API keys, works with no network once the voice is installed (the only
  network use is the one‑time voice download, which is also avoidable)

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
  fallback if `RECORD_AUDIO` is declined)
- Navigation drawer: Now playing · Ambient mix · Sleep timer · Radio stations ·
  Broadcast voice · skin · About
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
sherpa‑onnx 1.13.4 (Piper TTS, via JitPack) · `minSdk 31`, `target/compileSdk 37`.

### Layout

```
core/audio      MixerState/Controller, NoiseGenerator, BinauralGenerator, AmbientPattern,
                GainAudioProcessor + LoudnessProbe (broadcast levelling), VoiceEq (DJ voice EQ)
core/broadcast  BroadcastSelector, ShowClock, DjScriptBuilder (auto-DJ scripting)
core/data       SourceModels, SettingsRepository (DataStore), RadioDirectory, Room DB
core/design     AppSkin / SkinColors, Neon & Industrial skins, RotaryKnob, SkinComponents
core/tts        OfflineTtsEngine (sherpa-onnx), DjVoicePlayer, VoicePack + install/resolve
feature/player  PlayerRoute / PlayerViewModel / PlayerScreen + dialogs, BroadcastVoiceViewModel
feature/root    RootViewModel (skin selection)
media           MusicRepository (SAF folder walk + jingle folder), AudiobookRepository
playback        PlaybackService (Media3, Channel A, + loudness-levelling gain stage),
                PlaybackConnection (+ broadcast segue: links, jingles, time checks),
                AmbientPlaybackService (B/C)
tools/          build-stock-voice.sh
```

Full build history and design notes live in the commit log; the original phased
plan covered Phase 0 → Phase 8 (foundation → shippable v1) plus Phase 9
(Broadcast Radio), all done.

---

## Roadmap (post‑v1)

Phase 9 (Broadcast Radio) has landed, plus a round of post‑v1 Broadcast polish
(Maximum chattiness, announcer level/speed, bring‑your‑own jingles, time checks
timed to when they're heard, decode‑error skip, just‑in‑time loudness levelling,
DJ‑voice EQ). Nothing below is committed — it's the shortlist for after more real
bedside use.

**Audio & sources**
- [ ] Auto‑reconnect for dropped radio streams (backoff + a "reconnecting…" state)
- [ ] Broadcast: "talk over the intro" (duck the music under the link instead of a
      clean gap); pull track/artist from embedded tags, not the folder name
- [ ] Weather & news readouts for the Broadcast DJ (keyless sources, opt‑in,
      inert offline)
- [ ] Per‑station now‑playing providers — e.g. Radio Paradise's JSON API for cover art and
      exact track timing, ICY staying the generic fallback
- [ ] "Stop at end of chapter / track" option for audiobooks (deferred from the sleep timer)
- [ ] Playback speed for audiobooks again, if it turns out to be missed
- [ ] Fade‑*in* on start, and a short crossfade when switching sources

**Ambient**
- [ ] A real settings screen: custom binaural carrier/beat, an independent noise level,
      the noise palette — replacing the interim "Ambient mix" dialog
- [ ] More noise models (rain, ocean, fan) — still procedural where possible
- [ ] "Ramp down" ambient too: an optional slow taper on B/C over N hours

**UX & platform**
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
