package org.dylanjones.sleepradio.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceSlotEntity::class,
        AudiobookProgressEntity::class,
        PodcastFeedEntity::class,
        PodcastProgressEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SleepRadioDatabase : RoomDatabase() {
    abstract fun sourceSlotDao(): SourceSlotDao
    abstract fun audiobookProgressDao(): AudiobookProgressDao
    abstract fun podcastFeedDao(): PodcastFeedDao
    abstract fun podcastProgressDao(): PodcastProgressDao
}
