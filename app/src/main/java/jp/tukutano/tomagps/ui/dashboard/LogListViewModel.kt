package jp.tukutano.tomagps.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.model.JourneyLog
import jp.tukutano.tomagps.repository.JourneyLogRepository
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.service.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = JourneyLogRepository(
        AppDatabase.getInstance(app).journeyLogDao()
    )
    // GPSログ（TrackPoint）のリスト


    val logs: StateFlow<List<JourneyLog>> =
        repo.allLogs.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /** ログを削除 */
    fun deleteLog(log: JourneyLog) = viewModelScope.launch {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteById(log.id)      // ← I/O スレッドで実行
        }
    }
}