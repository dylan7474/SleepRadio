package org.dylanjones.sleepradio.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import org.dylanjones.sleepradio.core.data.PodcastProgress

/** Per-episode resume position + played state. Keyed by the episode's own
 *  [episodeGuid] (stable across feed re-fetches); [feedId] lets a whole
 *  show's progress be pulled at once to drive the episode list / preset
 *  resolution ([org.dylanjones.sleepradio.core.data.pickPodcastEpisode]). */
@Entity(tableName = "podcast_progress")
data class PodcastProgressEntity(
    @PrimaryKey val episodeGuid: String,
    val feedId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

@Dao
interface PodcastProgressDao {

    @Query("SELECT * FROM podcast_progress WHERE episodeGuid = :episodeGuid")
    suspend fun get(episodeGuid: String): PodcastProgressEntity?

    @Query("SELECT * FROM podcast_progress WHERE feedId = :feedId")
    suspend fun forFeed(feedId: String): List<PodcastProgressEntity>

    @Upsert
    suspend fun upsert(row: PodcastProgressEntity)
}

fun PodcastProgressEntity.toDomain() = PodcastProgress(
    episodeGuid = episodeGuid,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAt = updatedAt,
)
