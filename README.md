# SleepRadio

A bedside audio player for Android. It plays **one main source** — a local music
folder, an audiobook, or an internet‑radio station — and layers **two ambient
channels** underneath it: procedural coloured noise and binaural beats. A sleep
timer fades and stops the main source on schedule; the ambient channels keep
playing.

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

**Channel B — coloured noise**
- White · pink · brown · blue · deep space · ambient (pink + slow LFO)
- Procedural, on its own `AudioTrack` + writer thread; BAL knob crossfades A ↔ B

**Channel C — binaural beats**
- Sleep 3 Hz · Meditate 4.5 Hz · Relax 6 Hz · Focus 10 Hz presets, plus a level slider
- Stereo detuned sines; sits outside the BAL crossfade

**Mixer & timer**
- **VOL** = master gain, **BAL** = equal‑power A ↔ B crossfade
- Sleep timer (5–90 min): fades Channel A over the last 20 s, then pauses it — B and C
  continue
- Ambient survives the sleep timer *and* an app‑swipe (dedicated foreground service);
  stops itself on headphone/BT unplug
- 3 saveable **PATTERN** slots (a noise + binaural combo); the last mix is restored on launch
- Keeps the screen awake while you set things up; lets it sleep once the timer is running

**Elsewhere**
- Live audio‑reactive visualiser strip (`Visualizer` on the output mix; decorative
  fallback if `RECORD_AUDIO` is declined)
- Navigation drawer: Now playing · Ambient mix · Sleep timer · Radio stations · skin · About
- Portrait‑locked, edge‑to‑edge, predictive back, TalkBack labels on the controls

---

## Build & run

Requires the Android SDK (compileSdk 37) and a JDK 17+ (Android Studio's bundled JBR works).

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (mixer math, sleep scale, settings codecs)
./gradlew :app:testDebugUnitTest

# Signed release APK (R8 minified, ~4.5 MB).
# assembleRelease pulls lint-gradle; add the -x flags to skip it offline.
./gradlew :app:assembleRelease \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
```

Release signing reads `keystore.properties` at the repo root (git‑ignored, alongside
the `.jks`); without it the release build falls back to debug signing.

Distribution is sideload — `adb install -r app/build/outputs/apk/…/app-*.apk`.

### Stack

Kotlin 2.2.10 · AGP 9.3.2 · KSP 2.2.10‑2.0.2 · Compose BOM 2026.02.01 ·
Media3 1.9.4 · Hilt 2.60.1 · Room 2.8.4 · DataStore 1.2.1 · coroutines 1.11.0 ·
`minSdk 31`, `target/compileSdk 37`.

### Layout

```
core/audio      MixerState/Controller, NoiseGenerator, BinauralGenerator, AmbientPattern
core/data       SourceModels, SettingsRepository (DataStore), RadioDirectory, Room DB
core/design     AppSkin / SkinColors, Neon & Industrial skins, RotaryKnob, SkinComponents
feature/player  PlayerRoute / PlayerViewModel / PlayerScreen + dialogs, AlbumArt, AudioVisualizer
feature/root    RootViewModel (skin selection)
playback        PlaybackService (Media3, Channel A), PlaybackConnection, AmbientPlaybackService (B/C)
```

Full build history and design notes live in the commit log; the original phased plan
covered Phase 0 → Phase 8 (foundation → shippable v1), all done.

---

## Roadmap — Phase 9 (post‑v1)

Nothing here is committed; it's the shortlist for after a few days of real bedside use.

**Audio & sources**
- [ ] Auto‑reconnect for dropped radio streams (backoff + a "reconnecting…" state)
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

Online station directory: **[radio‑browser.info](https://www.radio-browser.info/)**
(community‑run, public domain). Everything else is first‑party.
