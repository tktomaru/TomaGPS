package jp.tukutano.tomagps.ui.detail

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.repository.JourneyLogRepository
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.repository.PhotoEntryRepository
import jp.tukutano.tomagps.service.PhotoEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogDetailActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    companion object {
        const val EXTRA_LOG_ID = "EXTRA_LOG_ID"
    }

    private lateinit var map: GoogleMap
    private val logId by lazy { intent.getLongExtra(EXTRA_LOG_ID, -1L) }

    private val db by lazy { AppDatabase.getInstance(this) }
    private val trackRepo by lazy { TrackPointRepository(db.trackPointDao()) }
    private val photoRepo by lazy { PhotoEntryRepository(db.photoEntryDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_detail)

        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = false
        map.setOnMarkerClickListener(this)
        drawRoute()
        mapPhotos()

        // 共有ボタン初期化
        findViewById<FloatingActionButton>(R.id.fabShare).setOnClickListener {
            shareLog()
        }
    }

    /** ルートを描画 */
    private fun drawRoute() {
        lifecycleScope.launchWhenStarted {
            trackRepo.getPointsByLog(logId).collectLatest { points ->
                if (points.isNotEmpty()) {
                    val poly = PolylineOptions().apply {
                        addAll(points.map { LatLng(it.lat, it.lng) })
                        width(6f)
                        color(getColor(R.color.purple_500))
                    }
                    map.addPolyline(poly)
                    // カメラを始点に
                    val start = points.first()
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(start.lat, start.lng), 12f
                        )
                    )
                }
            }
        }
    }

    /** 地図スナップ + 写真URIリストをまとめて Intent 共有 */
    private fun shareLog() {
        map.snapshot { bmp ->
            val mapUri = saveBitmapToCache(bmp, "share_map_${logId}.png")
            lifecycleScope.launch {
                // 写真 URI のリスト取得（Flow.first() 等は省略）
                val rawUris: List<Uri> = photoRepo.getPhotosByLog(logId)
                    .first()
                    .map { it.uri }
                    .toMutableList().apply {
                        mapUri?.let { add(0, it) }
                    }

                // file:// を content:// に変換
                val shareUris = rawUris.map { uri ->
                    if (uri.scheme == "file") {
                        // file://→FileProvider content:// に
                        FileProvider.getUriForFile(
                            this@LogDetailActivity,
                            "${packageName}.fileprovider",
                            File(uri.path!!)
                        )
                    } else {
                        uri
                    }
                }

                // SNS共有用 Intent
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList(shareUris)
                    )
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "ツーリングログ：$logId\n距離: ${"%.1f".format(
                            JourneyLogRepository(db.journeyLogDao())
                                .getById(logId).first()?.distanceKm ?: 0.0
                        )} km"
                    )
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "ログを共有"))
            }
        }
    }

    /** Bitmap を cacheDir にファイル化して FileProvider 経由で URI を返す */
    private fun saveBitmapToCache(bmp: Bitmap?, filename: String): Uri? {
        if (bmp == null) return null
        val file = File(cacheDir, filename)
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
    }

    /** 写真をマーカーとしてマッピング */
    private fun mapPhotos() {
        lifecycleScope.launchWhenStarted {
            photoRepo.getPhotosByLog(logId).collectLatest { photos ->
                photos.forEach { photo ->
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(photo.lat, photo.lng))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    marker?.tag = photo
                }
            }
        }
    }

    /** マーカータップ時に写真プレビューを表示 */
    override fun onMarkerClick(marker: Marker): Boolean {
        val entry = marker.tag as? PhotoEntry ?: return true
        // 簡易プレビュー：ダイアログで表示
        val dlg = PhotoPreviewDialogFragment.newInstance(entry.uri.toString())
        dlg.show(supportFragmentManager, "photo_preview")
        return true
    }
}