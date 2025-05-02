package jp.tukutano.tomagps.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(log: JourneyLogEntity): Long   // ← 生成した ID を返す

    @Query("SELECT * FROM journey_logs ORDER BY dateMillis DESC")
    fun getAll(): Flow<List<JourneyLogEntity>>

    @Query("SELECT * FROM journey_logs WHERE id = :id")
    fun getById(id: Long): Flow<JourneyLogEntity?>

    @Delete
    fun delete(log: JourneyLogEntity)

    @Query("DELETE FROM journey_logs WHERE id = :id")
    fun deleteById(id: Long): Int
}