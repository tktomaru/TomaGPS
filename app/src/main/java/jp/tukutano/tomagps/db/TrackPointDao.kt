package jp.tukutano.tomagps.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points ORDER BY time ASC")
    fun getAll(): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE logId = :logId ORDER BY time ASC")
    fun getPointsByLog(logId: Long): Flow<List<TrackPointEntity>>
}
