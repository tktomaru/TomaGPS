package jp.tukutano.tomagps.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.model.JourneyLog
import jp.tukutano.tomagps.service.TrackPoint
import jp.tukutano.tomagps.service.TrackingService
import jp.tukutano.tomagps.repository.JourneyLogRepository
import jp.tukutano.tomagps.repository.PhotoEntryRepository
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.service.PhotoEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TrackViewModel(app: Application) : AndroidViewModel(app) {

    private val trackRepo = TrackPointRepository(
        AppDatabase.getInstance(app).trackPointDao()
    )
    private val repo = JourneyLogRepository(
        AppDatabase.getInstance(app).journeyLogDao()
    )
    private val service = TrackingService(app)  // 本番では ServiceConnection 推奨

    /** 生ルート */
    private val _path = MutableStateFlow<List<TrackPoint>>(emptyList())
    val path: StateFlow<List<TrackPoint>> = _path

    /** 距離 (km) */
    val distanceKm: StateFlow<Double> = _path
        .map { calcDistance(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    /** 経過秒 */
    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> get() = _elapsed

    private var timerJob: Job? = null

    /** 計測停止 → JourneyLog 保存 → TrackPoint 保存 */

    /** 計測停止 → JourneyLog & TrackPoint を DB に保存 */
//    fun stopAndSavePoints(title: String, thumbnail: Uri?) = viewModelScope.launch {
//        // 1) 計測停止＆タイマーキャンセル
//        service.stopTracking()
//        timerJob?.cancel()
//
//        val path = service.path
//        if (path.size < 2) return@launch
//
//        // 2) DB 書き込みは I/O スレッドでまとめて実行
//        withContext(Dispatchers.IO) {
//            // 2-1) JourneyLog を保存して logId を取得
//            val log = JourneyLog(
//                id = 0,
//                title = title,
//                date = LocalDateTime.now(),
//                distanceKm = calcDistance(path),
//                thumbnail = thumbnail
//            )
//            val logId = repo.upsert(log)
//
//            // 2-2) TrackPoint を保存
//            trackRepo.savePath(logId, path)
//        }
//    }
//
    /** 計測開始 */
    fun start() {
        service.startTracking()

        // 位置取り込み
        viewModelScope.launch {
            while (true) {
                _path.value = service.path
                delay(1000)
            }
        }

        // 経過時計
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                _elapsed.value = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    /** 2点間の距離を累積し km 単位で返す */
    private fun calcDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var dist = 0.0
        points.windowed(2).forEach { (a, b) ->
            dist += haversine(a.lat, a.lng, b.lat, b.lng)
        }
        return dist / 1000.0
    }

    /** Haversine formula (m 単位) */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3  // 地球半径 (m)
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)
        val a = sin(Δφ / 2).pow(2) +
                cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        return 2 * R * asin(sqrt(a))
    }

    // 現在セッションで撮った写真を一時保持
    private val db = AppDatabase.getInstance(app)
    private val photoRepo = PhotoEntryRepository(db.photoEntryDao())
    private val photoList = mutableListOf<PhotoEntry>()

    fun addPhoto(entry: PhotoEntry) {
        photoList.add(entry)
    }

    fun stopAndSavePoints(title: String, thumbnail: Uri?) = viewModelScope.launch {
        service.stopTracking()
        timerJob?.cancel()
        val path = service.path
        if (path.size < 2) return@launch

        withContext(Dispatchers.IO) {
            // 1) JourneyLog
            val log = JourneyLog(
                id = 0,
                title = title,
                date = LocalDateTime.now(),
                distanceKm = calcDistance(path),
                thumbnail = thumbnail
            )
            val logId = repo.upsert(log)

            // 2) TrackPoints
            trackRepo.savePath(logId, path)

            // 3) PhotoEntries（ここでDBに保存）
            photoRepo.savePhotos(logId, photoList)
        }
        // セッション後はリストクリア
        photoList.clear()
    }
}
