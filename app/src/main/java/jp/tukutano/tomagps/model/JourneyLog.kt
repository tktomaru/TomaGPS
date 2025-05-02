package jp.tukutano.tomagps.model

import android.net.Uri
import jp.tukutano.tomagps.db.JourneyLogEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class JourneyLog(
    val id: Long,
    val title: String,
    val date: LocalDateTime,
    val distanceKm: Double,
    val thumbnail: Uri?
)

// 拡張関数で相互変換
fun JourneyLogEntity.toDomain() = JourneyLog(
    id = id,
    title = title,
    date = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault()),
    distanceKm = distanceKm,
    thumbnail = thumbnail?.let { Uri.parse(it) }
)

fun JourneyLog.toEntity() = JourneyLogEntity(
    id         = id,
    title      = title,
    dateMillis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    distanceKm = distanceKm,
    thumbnail  = thumbnail?.toString()
)