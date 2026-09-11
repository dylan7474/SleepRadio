package org.dylanjones.sleepradio.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.data.PodcastFeed

/** A subscribed podcast show. Episodes are never persisted (Decision #20) —
 *  fetched live per open from [feedUrl] via
 *  [org.dylanjones.sleepradio.media.PodcastRepository]. */
@Entity(tableName = "podcast_feeds")
data class PodcastFeedEntity(
    @PrimaryKey val id: String,
    val feedUrl: String,
    val title: String,
    val artworkUrl: String?,
    val addedAt: Long,
)

@Dao
interface PodcastFeedDao {

    /** Subscribed shows, oldest-subscribed first. */
    @Query("SELECT * FROM podcast_feeds ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<PodcastFeedEntity>>

    @Upsert
    suspend fun upsert(row: PodcastFeedEntity)

    @Query("DELETE FROM podcast_feeds WHERE id = :id")
    suspend fun delete(id: String)
}

fun PodcastFeedEntity.toDomain() =
    PodcastFeed(id = id, feedUrl = feedUrl, title = title, artworkUrl = artworkUrl)

fun PodcastFeed.toEntity(addedAt: Long = System.currentTimeMillis()) = PodcastFeedEntity(
    id = id,
    feedUrl = feedUrl,
    title = title,
    artworkUrl = artworkUrl,
    addedAt = addedAt,
)
