package jp.tukutano.tomagps.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.model.JourneyLog
import jp.tukutano.tomagps.repository.JourneyLogRepository
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.service.TrackPoint
import kotlinx.coroutines.flow.Flow

class LogListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = JourneyLogRepository(
        AppDatabase.getInstance(app).journeyLogDao()
    )
    // GPSログ（TrackPoint）のリスト
    val logs: Flow<List<JourneyLog>> = repo.allLogs
}