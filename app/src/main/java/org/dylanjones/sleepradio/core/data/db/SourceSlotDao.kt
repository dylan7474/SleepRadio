package org.dylanjones.sleepradio.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceSlotDao {

    @Query("SELECT * FROM source_slots")
    fun observeAll(): Flow<List<SourceSlotEntity>>

    @Upsert
    suspend fun upsert(slot: SourceSlotEntity)

    @Query("DELETE FROM source_slots WHERE slotIndex = :index")
    suspend fun clear(index: Int)
}
