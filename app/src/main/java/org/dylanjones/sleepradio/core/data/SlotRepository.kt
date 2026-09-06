package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.dylanjones.sleepradio.core.data.db.SourceSlotDao
import org.dylanjones.sleepradio.core.data.db.toDomain
import org.dylanjones.sleepradio.core.data.db.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Persisted source-preset slots (Room-backed). Survives app kill. */
@Singleton
class SlotRepository @Inject constructor(
    private val dao: SourceSlotDao,
) {
    /** The [PRESET_COUNT] slots, indexed; null where nothing is assigned. */
    val slots: Flow<List<SourceSlot?>> = dao.observeAll().map { rows ->
        val byIndex = rows.associateBy { it.slotIndex }
        List(PRESET_COUNT) { i -> byIndex[i]?.toDomain() }
    }

    suspend fun assign(slot: SourceSlot) = dao.upsert(slot.toEntity())

    suspend fun clear(index: Int) = dao.clear(index)
}
