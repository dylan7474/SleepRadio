package org.dylanjones.sleepradio.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SourceSlotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SleepRadioDatabase : RoomDatabase() {
    abstract fun sourceSlotDao(): SourceSlotDao
}
