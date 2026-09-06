package org.dylanjones.sleepradio.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType

@Entity(tableName = "source_slots")
data class SourceSlotEntity(
    @PrimaryKey val slotIndex: Int,
    val type: String,
    val refId: String,
    val label: String,
    val sublabel: String,
    val artworkUri: String?,
)

fun SourceSlotEntity.toDomain() = SourceSlot(
    index = slotIndex,
    type = SourceType.valueOf(type),
    refId = refId,
    label = label,
    sublabel = sublabel,
    artworkUri = artworkUri,
)

fun SourceSlot.toEntity() = SourceSlotEntity(
    slotIndex = index,
    type = type.name,
    refId = refId,
    label = label,
    sublabel = sublabel,
    artworkUri = artworkUri,
)
