package jp.tukutano.tomagps.service

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

// ルート記録用のデータクラス
data class TrackPoint(val lat: Double, val lng: Double, val time: Long)

class TrackingService(private val app: Application) {

    // 位置取得用クライアント
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(app)

    // 取得した軌跡を貯めるリスト
    private val _path = mutableListOf<TrackPoint>()
    val path: List<TrackPoint> get() = _path

    // ロケーションコールバック
    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { loc ->
                _path.add(TrackPoint(loc.latitude, loc.longitude, loc.time))
            }
        }
    }

    fun startTracking() {
        // 1) 権限チェック
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Activity 側でリクエストさせるか、ここで例外／戻り値で知らせる
            throw SecurityException("位置情報の権限がありません")
        }

        // 2) リクエスト設定
        val req = LocationRequest.create().apply {
            interval = 2000             // 正常更新間隔：2秒
            fastestInterval = 1000      // 最短更新間隔：1秒
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        // 3) 位置情報取得開始
        fusedClient.requestLocationUpdates(req, locCallback, app.mainLooper)
    }

    fun stopTracking() {
        fusedClient.removeLocationUpdates(locCallback)
    }
}