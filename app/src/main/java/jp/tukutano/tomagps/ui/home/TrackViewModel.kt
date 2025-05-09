package jp.tukutano.tomagps.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.model.JourneyLog
import jp.tukutano.tomagps.repository.JourneyLogRepository
import jp.tukutano.tomagps.repository.PhotoEntryRepository
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.service.PhotoEntry
import jp.tukutano.tomagps.service.TrackPoint
import jp.tukutano.tomagps.service.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

class TrackViewModel(app: Application,
                     private val resumeLogId: Long?) : AndroidViewModel(app) {

    // Database & Repositories
    private val database by lazy { AppDatabase.getInstance(app) }
    private val trackRepo by lazy { TrackPointRepository(database.trackPointDao()) }
    private val logRepo by lazy { JourneyLogRepository(database.journeyLogDao()) }
    private val photoRepo by lazy { PhotoEntryRepository(database.photoEntryDao()) }
    private val service = TrackingService(app)

    // Tracking state
    private val _path = MutableStateFlow<List<TrackPoint>>(emptyList())
    val path: StateFlow<List<TrackPoint>> = _path.asStateFlow()

    val distanceKm: StateFlow<Double> = path
        .map { it.calculateDistanceKm() }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()
    private var timerJob: Job? = null

    // Photo attachments buffer
    private val photoBuffer = mutableListOf<PhotoEntry>()
    private val _photosFlow = MutableStateFlow<List<PhotoEntry>>(emptyList())
    val photos: StateFlow<List<PhotoEntry>> = _photosFlow.asStateFlow()


    init {
        // 再開時：DB から過去ポイントをロード
        resumeLogId?.let { id ->
            trackRepo.getPointsByLog(id)
                .onEach { pts ->
                    _path.value = pts
                    service.setInitialPath(pts)
                }
                .launchIn(viewModelScope)
        }
    }

    /** Start tracking and UI updates */
    fun startTracking() {
        service.startTracking()
        observePathUpdates()
        startTimer()
    }

    /** Stop, save log, points, and photos */
    fun stopAndSaveLog(title: String, thumbnail: Uri?) {
        viewModelScope.launch {
            service.stopTracking()
            timerJob?.cancel()
            val points = service.path
            if (points.size < 2) return@launch

            val logId = saveJourneyLog(title, thumbnail, points)
            saveTrackPointsAndPhotos(logId, points)
            clearSession()
        }
    }

    /** Add a photo to current session */
    fun addPhoto(entry: PhotoEntry) {
        photoBuffer += entry
        _photosFlow.value = photoBuffer.toList()
    }

    /** Remove a photo by URI */
    fun removePhoto(uri: Uri) {
        // Remove from buffer
        photoBuffer.removeAll { it.uri == uri }
        // Update flow so observers see removal
        _photosFlow.value = photoBuffer.toList()
        // (B)――バックグラウンドでDBからも削除
        viewModelScope.launch(Dispatchers.IO) {
            photoRepo.deleteByUri(uri)
        }
    }

    private fun observePathUpdates() {
        viewModelScope.launch {
            while (true) {
                _path.value = service.path
                delay(1000)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                _elapsed.value = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    private suspend fun saveJourneyLog(
        title: String,
        thumbnail: Uri?,
        points: List<TrackPoint>
    ): Long = withContext(Dispatchers.IO) {
        val log = JourneyLog(
            id = 0,
            title = title,
            date = LocalDateTime.now(),
            distanceKm = points.calculateDistanceKm(),
            thumbnail = thumbnail
        )
        logRepo.upsert(log)
    }

    private suspend fun saveTrackPointsAndPhotos(
        logId: Long,
        points: List<TrackPoint>
    ) = withContext(Dispatchers.IO) {
        trackRepo.savePath(logId, points)
        photoRepo.savePhotos(logId, photoBuffer)
    }

    private fun clearSession() {
        photoBuffer.clear()
        _photosFlow.value = emptyList()
    }

    // --- Utility extensions ---
    private fun List<TrackPoint>.calculateDistanceKm(): Double {
        if (size < 2) return 0.0
        return windowed(2)
            .sumOf { (a, b) -> haversine(a.lat, a.lng, b.lat, b.lng) / 1000.0 }
    }

    private fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val R = 6371e3
        val φ1 = lat1.toRadians()
        val φ2 = lat2.toRadians()
        val Δφ = (lat2 - lat1).toRadians()
        val Δλ = (lon2 - lon1).toRadians()
        return 2 * R * asin(
            sqrt(
                sin(Δφ / 2).pow(2) +
                        cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
            )
        )
    }

    private fun Double.toRadians() = Math.toRadians(this)

}
