package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.dylanjones.sleepradio.core.data.db.PodcastFeedDao
import org.dylanjones.sleepradio.core.data.db.toDomain
import org.dylanjones.sleepradio.core.data.db.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Subscribed podcast feeds (Room-backed), survives app kill. Episodes
 *  themselves are never persisted — see [PodcastFeedEntity]. */
@Singleton
class PodcastSubscriptionRepository @Inject constructor(
    private val dao: PodcastFeedDao,
) {
    val feeds: Flow<List<PodcastFeed>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun subscribe(feed: PodcastFeed) = dao.upsert(feed.toEntity())

    suspend fun unsubscribe(id: String) = dao.delete(id)
}
