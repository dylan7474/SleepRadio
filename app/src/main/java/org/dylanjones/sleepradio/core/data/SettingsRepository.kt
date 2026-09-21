package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.audio.AmbientPattern

// RadioStation lives in this package (SourceModels.kt).

/** Number of saveable ambient PATTERN slots. */
const val AMBIENT_PATTERN_SLOTS = 3

/**
 * App-wide user preferences, backed by DataStore.
 */
interface SettingsRepository {
    /** Persisted SAF tree URI for the audiobooks root folder, or null. */
    val audiobooksTreeUri: Flow<String?>

    suspend fun setAudiobooksTreeUri(uri: String)

    /** Persisted SAF tree URI for a music root folder, or null. */
    val musicTreeUri: Flow<String?>

    suspend fun setMusicTreeUri(uri: String)

    /** Last-used ambient mix, restored on launch. Null before anything is saved. */
    val ambient: Flow<AmbientPattern?>

    suspend fun setAmbient(pattern: AmbientPattern)

    /** The [AMBIENT_PATTERN_SLOTS] saved PATTERN slots (null = empty). */
    val ambientPatterns: Flow<List<AmbientPattern?>>

    suspend fun setAmbientPattern(index: Int, pattern: AmbientPattern?)

    /** Sleep-timer duration in minutes (default 30). The running timer is not persisted. */
    val sleepDurationMin: Flow<Int>

    suspend fun setSleepDurationMin(minutes: Int)

    /** User-added radio stations (manual entry or from the online directory),
     *  in the order they were added. */
    val customStations: Flow<List<RadioStation>>

    suspend fun addCustomStation(station: RadioStation)

    suspend fun removeCustomStation(id: String)

    /**
     * Which voice the Broadcast DJ uses: [BROADCAST_VOICE_OFF] (music-only, no
     * spoken links — the default), [BROADCAST_VOICE_STOCK] or
     * [BROADCAST_VOICE_PERSONAL]. Persisted; the pack must still be installed
     * under `filesDir/tts/<id>/` for a non-off value to actually speak.
     */
    val broadcastVoice: Flow<String>

    suspend fun setBroadcastVoice(id: String)

    /**
     * Which voice reads the news bulletins: [NEWS_VOICE_SAME] (default — whatever the DJ
     * uses), [BROADCAST_VOICE_STOCK] or [BROADCAST_VOICE_PERSONAL]. Lets a listener keep a
     * personal voice for the DJ but have the downloaded stock voice read the news.
     */
    val broadcastNewsVoice: Flow<String>

    suspend fun setBroadcastNewsVoice(id: String)

    /** How chatty the auto-DJ is: a [org.dylanjones.sleepradio.core.broadcast.Chattiness] id. */
    val broadcastChattiness: Flow<String>

    suspend fun setBroadcastChattiness(id: String)

    /** DJ voice level, 0..1, applied on top of the master VOL. Default 1. */
    val broadcastAnnouncerVolume: Flow<Float>

    suspend fun setBroadcastAnnouncerVolume(value: Float)

    /** DJ speech rate: 1.0 = the voice's natural rate, higher is faster. Default 1. */
    val broadcastAnnouncerSpeed: Flow<Float>

    suspend fun setBroadcastAnnouncerSpeed(value: Float)

    /**
     * News-reading speech rate, separate from the DJ's: the news voice often sounds more rushed
     * than the DJ at the same rate. 1.0 = the voice's natural rate. Default 0.75.
     */
    val broadcastNewsSpeed: Flow<Float>

    suspend fun setBroadcastNewsSpeed(value: Float)

    /** Persisted SAF tree URI for the folder holding the user's jingle files, or null. */
    val jinglesTreeUri: Flow<String?>

    suspend fun setJinglesTreeUri(uri: String)

    /** Whether the Broadcast DJ drops in a jingle between tracks. Default false. */
    val broadcastJingleEnabled: Flow<Boolean>

    suspend fun setBroadcastJingleEnabled(enabled: Boolean)

    /** Tracks between jingles, 1..10 (only used when [broadcastJingleEnabled]). Default 4. */
    val broadcastJingleEvery: Flow<Int>

    suspend fun setBroadcastJingleEvery(tracks: Int)

    /** 70s-style DJ hook lines before track intros (bundled pool). Off by default. */
    val broadcastDjHooks: Flow<Boolean>

    suspend fun setBroadcastDjHooks(enabled: Boolean)

    /**
     * News bulletins in the Broadcast: top stories around :00, softer stories around :30,
     * read in the gap between tracks. Off by default — it is the only Broadcast feature
     * that touches the network.
     */
    val broadcastNewsEnabled: Flow<Boolean>

    suspend fun setBroadcastNewsEnabled(enabled: Boolean)

    /** No news bulletins overnight (11 pm to 6 am). On by default — this is a sleep station. */
    val broadcastNewsQuietHours: Flow<Boolean>

    suspend fun setBroadcastNewsQuietHours(enabled: Boolean)

    /**
     * The VOL and BAL knob positions (0..1), restored on launch so a restart doesn't reset
     * them. Null until first saved. Stored in the settings store, so it is part of backup/restore.
     */
    val mixerLevels: Flow<MixerLevels?>

    suspend fun setMixerLevels(levels: MixerLevels)

    /**
     * How brightly the lit lamps and windows on the keys shine, 0.2..1 (default 1). Lower is easier
     * on the eyes in a dark bedroom.
     */
    val lampBrightness: Flow<Float>

    suspend fun setLampBrightness(value: Float)

    /**
     * How the channel buttons are shown: four at a time (default) or one big button at a time for
     * people who find the small ones hard to see. Backed up with the other settings.
     */
    val channelButtonMode: Flow<ChannelButtonMode>

    suspend fun setChannelButtonMode(mode: ChannelButtonMode)

    /** Screen rotation: follow the phone's auto-rotate (default), or forced portrait / landscape. Backed up. */
    val screenRotation: Flow<ScreenRotation>

    suspend fun setScreenRotation(mode: ScreenRotation)

    /** Quiet hours start / end, minutes since midnight (defaults 23:00 and 06:00). */
    val broadcastNewsQuietStartMin: Flow<Int>
    val broadcastNewsQuietEndMin: Flow<Int>

    suspend fun setBroadcastNewsQuietStartMin(minutes: Int)
    suspend fun setBroadcastNewsQuietEndMin(minutes: Int)

    // --- Studio-skin VU meter output-latency compensation (Phase 16) ---

    /** True = pick the delay by the live output route; false = use [vuDelayCustomMs]. Default true. */
    val vuSyncAuto: Flow<Boolean>

    suspend fun setVuSyncAuto(auto: Boolean)

    /** VU meter delay (ms) for the phone speaker / wired output. Default 120. */
    val vuDelayPhoneMs: Flow<Int>

    suspend fun setVuDelayPhoneMs(ms: Int)

    /** VU meter delay (ms) for a Bluetooth output. Default 260. */
    val vuDelayBluetoothMs: Flow<Int>

    suspend fun setVuDelayBluetoothMs(ms: Int)

    /** VU meter delay (ms) used when [vuSyncAuto] is false. Default 150. */
    val vuDelayCustomMs: Flow<Int>

    suspend fun setVuDelayCustomMs(ms: Int)

    /**
     * Every current preference, keyed by its DataStore key name. Values are
     * whatever primitive type DataStore itself stores (String, Boolean, Int,
     * Long, Float or Set<String>). For [org.dylanjones.sleepradio.core.backup.BackupManager]
     * only — not meant for anything else to read.
     */
    suspend fun exportAll(): Map<String, Any>

    /**
     * Replace every preference with [values] (as produced by [exportAll]).
     * Clears anything not present in [values]. For
     * [org.dylanjones.sleepradio.core.backup.BackupManager] only.
     */
    suspend fun importAll(values: Map<String, Any>)
}

/** Upper clamp for every VU-sync delay (ms). */
const val VU_DELAY_MAX_MS = 500

const val BROADCAST_VOICE_OFF = "off"
const val BROADCAST_VOICE_STOCK = "stock"
const val BROADCAST_VOICE_PERSONAL = "personal"

/** News reader voice: use the same voice as the DJ. */
const val NEWS_VOICE_SAME = "same"

/** The VOL knob ([volume]) and BAL knob ([balance]) positions, both 0..1. */
data class MixerLevels(val volume: Float, val balance: Float)
