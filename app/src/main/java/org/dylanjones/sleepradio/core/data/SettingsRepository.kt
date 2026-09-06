package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.design.SkinId

/**
 * App-wide user preferences. DataStore-backed implementation lands in Phase 2;
 * this interface exists so Phase 0 code can depend on the shape.
 */
interface SettingsRepository {
    /** Currently selected visual skin. Defaults to [SkinId.NEON] on first run. */
    val skin: Flow<SkinId>

    suspend fun setSkin(skin: SkinId)
}
