package jp.tukutano.tomagps

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import jp.tukutano.tomagps.db.JourneyLogDao
import jp.tukutano.tomagps.db.JourneyLogEntity
import jp.tukutano.tomagps.db.PhotoEntryDao
import jp.tukutano.tomagps.db.PhotoEntryEntity
import jp.tukutano.tomagps.db.TrackPointDao
import jp.tukutano.tomagps.db.TrackPointEntity

/**
 * ───────────────────────────────────────────────────────────────
 *  AppDatabase
 *  ・TrackPointEntity  … 走行ルートの緯度経度＋タイムスタンプ
 *  ・JourneyLogEntity  … 1 回のツーリング全体のメタ情報
 *  各 Dao はアプリ全体で 1 つのシングルトン DB から取得できます。
 * ───────────────────────────────────────────────────────────────
 */
@Database(
    entities = [
        TrackPointEntity::class,
        JourneyLogEntity::class,
        PhotoEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** 走行ルート用 DAO */
    abstract fun trackPointDao(): TrackPointDao

    /** ツーリングログ用 DAO */
    abstract fun journeyLogDao(): JourneyLogDao

    abstract fun photoEntryDao(): PhotoEntryDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * アプリケーション全体で共有する DB インスタンスを取得
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tomagps_db"
                )
                    // スキーマ変更時に強制再構築（本番ではマイグレーションを推奨）
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}