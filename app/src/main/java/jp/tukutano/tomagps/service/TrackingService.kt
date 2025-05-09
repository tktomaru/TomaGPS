package jp.tukutano.tomagps.service

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

// ルート記録用のデータクラス
data class TrackPoint(val lat: Double, val lng: Double, val time: Long)

class TrackingService(private val app: Application) {

    private val _internalPath = mutableListOf<TrackPoint>()

    fun setInitialPath(points: List<TrackPoint>) {
        _internalPath.clear()
        _internalPath.addAll(points)
    }

    // 位置取得用クライアント
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(app)

    // 取得した軌跡を貯めるリスト
    val path: List<TrackPoint> get() = _internalPath

    // ロケーションコールバック
    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { loc ->
                _internalPath.add(TrackPoint(loc.latitude, loc.longitude, loc.time))
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
        // 新 API (Play Services 21.0.0+)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,    // バランスパワー GPS
            1_000L                               // 更新要求間隔：30 秒
        )
            .setMinUpdateIntervalMillis(1_000L)     // 最速更新間隔：1 秒
            // .setMaxUpdateDelayMillis(2_000L)   // 遅延許容上限を設定する場合
            // .setMaxUpdates(10)                 // 最大取得回数を設定する場合
            .build()

        // 3) 位置情報取得開始
        fusedClient.requestLocationUpdates(locationRequest, locCallback, app.mainLooper)
    }

    fun stopTracking() {
        fusedClient.removeLocationUpdates(locCallback)
    }
}