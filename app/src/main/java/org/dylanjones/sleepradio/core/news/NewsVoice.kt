package org.dylanjones.sleepradio.core.news

import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_OFF
import org.dylanjones.sleepradio.core.data.NEWS_VOICE_SAME
import org.dylanjones.sleepradio.core.tts.VoicePack
import org.dylanjones.sleepradio.core.tts.VoicePackResolver

/**
 * The voice pack that should read the news, given the two settings.
 *
 * [newsSetting] = [NEWS_VOICE_SAME] follows the DJ's voice ([djSetting]); anything else
 * names a pack id. If the DJ is Off (music-only) but news is on, or the chosen pack has
 * since been removed, fall back to whatever voice is installed rather than reading
 * nothing — the listener asked for a bulletin. Null only when no voice is installed at all.
 */
internal fun resolveNewsPack(
    resolver: VoicePackResolver,
    newsSetting: String,
    djSetting: String,
): VoicePack? {
    val wanted = if (newsSetting == NEWS_VOICE_SAME) djSetting else newsSetting
    val byChoice = wanted.takeIf { it != BROADCAST_VOICE_OFF && it != NEWS_VOICE_SAME }
        ?.let(resolver::byId)
    return byChoice ?: resolver.preferred()
}
