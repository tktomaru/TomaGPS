package jp.tukutano.tomagps.ui.detail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.AppDatabase
import jp.tukutano.tomagps.repository.TrackPointRepository
import jp.tukutano.tomagps.repository.PhotoEntryRepository
import jp.tukutano.tomagps.service.PhotoEntry
import kotlinx.coroutines.flow.collectLatest

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