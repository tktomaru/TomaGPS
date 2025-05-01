package jp.tukutano.tomagps.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1 回のツーリングを表すメタ情報
 *   - dateMillis : 開始日時を epochMillis で保持
 *   - thumbnail  : サムネイル画像の Uri を文字列保存（null 可）
 */
@Entity(tableName = "journey_logs")
data class JourneyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateMillis: Long,
    val distanceKm: Double,
    val thumbnail: String?
)