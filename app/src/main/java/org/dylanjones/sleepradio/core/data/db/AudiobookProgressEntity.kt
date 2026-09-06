package org.dylanjones.sleepradio.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import org.dylanjones.sleepradio.core.data.AudiobookProgress

@Entity(tableName = "audiobook_progress")
data class AudiobookProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val positionMs: Long,
    val updatedAt: Long,
)

@Dao
interface AudiobookProgressDao {

    @Query("SELECT * FROM audiobook_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): AudiobookProgressEntity?

    @Upsert
    suspend fun upsert(row: AudiobookProgressEntity)
}

fun AudiobookProgressEntity.toDomain() = AudiobookProgress(chapterIndex, positionMs)
