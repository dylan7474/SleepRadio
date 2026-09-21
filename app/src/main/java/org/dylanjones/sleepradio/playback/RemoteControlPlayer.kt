package org.dylanjones.sleepradio.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player the media session exposes to the outside world: Bluetooth remotes and headsets, car
 * kits, the lock screen and the notification all control playback through it.
 *
 * NEXT and PREVIOUS are routed to the app's own transport ([onNext] / [onPrevious]) instead of the
 * raw ExoPlayer's queue, so they do exactly what the on-screen keys do: skip a Broadcast to a fresh
 * pick, jump ±1 minute in an audiobook or podcast, and step through an album. The raw player has
 * only one item loaded for a Broadcast, radio station or podcast episode, so it reports no next /
 * previous at all — remotes would otherwise show dead buttons that do nothing.
 *
 * Only [seekToNext] / [seekToPrevious] are intercepted. The app's own controller uses the
 * `...MediaItem` variants (and plain seeks), so it goes straight through to the player and cannot
 * loop back into the transport.
 */
@UnstableApi
internal class RemoteControlPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun seekToNext() = onNext()

    override fun seekToPrevious() = onPrevious()
}
