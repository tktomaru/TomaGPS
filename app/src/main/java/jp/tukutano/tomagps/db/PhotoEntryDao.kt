package jp.tukutano.tomagps.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<PhotoEntryEntity>)

    @Query("SELECT * FROM photo_entries WHERE logId = :logId ORDER BY timestamp ASC")
    fun getByLogId(logId: Long): Flow<List<PhotoEntryEntity>>
}