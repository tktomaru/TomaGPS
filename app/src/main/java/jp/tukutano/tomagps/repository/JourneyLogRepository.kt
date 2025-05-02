package jp.tukutano.tomagps.repository

import jp.tukutano.tomagps.db.JourneyLogDao
import jp.tukutano.tomagps.model.JourneyLog
import jp.tukutano.tomagps.model.toDomain
import jp.tukutano.tomagps.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JourneyLogRepository(
    private val dao: JourneyLogDao
) {

    /** すべてのログを日時降順でストリーム提供 */
    val allLogs: Flow<List<JourneyLog>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    /** ID 指定で 1 件取得（null になり得るので Flow<JourneyLog?>） */
    fun getById(id: Long): Flow<JourneyLog?> =
        dao.getById(id).map { it?.toDomain() }

    /** 追加または更新して生成 ID を返す */
    fun upsert(log: JourneyLog): Long =
        dao.insert(log.toEntity())

    /** 削除 */
    fun delete(log: JourneyLog) =
        dao.delete(log.toEntity())

    fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}