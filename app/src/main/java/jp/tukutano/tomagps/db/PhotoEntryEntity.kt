package jp.tukutano.tomagps.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_entries")
data class PhotoEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val uri: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long
)