package jp.tukutano.tomagps.repository

import jp.tukutano.tomagps.db.TrackPointDao
import jp.tukutano.tomagps.db.TrackPointEntity
import jp.tukutano.tomagps.service.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class TrackPointRepository(private val dao: TrackPointDao) {
    // Flow で全 TrackPoint を提供
    val allTrackPoints: Flow<List<TrackPoint>> = dao.getAll().map { entities ->
        entities.map { TrackPoint(it.lat, it.lng, it.time) }
    }

    /**
     * TrackPoint を Room Entity に変換して保存する
     *
     * @param logId 保存先の JourneyLog の ID
     * @param points ルートを構成する TrackPoint のリスト
     */
    suspend fun savePath(logId: Long, points: List<TrackPoint>) {
        // id は autoGenerate なので 0L、logId をセットしてエンティティ化
        val entities = points.map { point ->
            TrackPointEntity(
                id    = 0L,
                logId = logId,
                lat   = point.lat,
                lng   = point.lng,
                time  = point.time
            )
        }
        dao.insertAll(entities)
    }

    // 追加: 特定ログのポイント取得
    fun getPointsByLog(logId: Long): Flow<List<TrackPoint>> =
        dao.getPointsByLog(logId)
            .map { entities ->
                entities.map { TrackPoint(it.lat, it.lng, it.time) }
            }
}