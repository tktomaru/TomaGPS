package jp.tukutano.tomagps.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.service.PhotoEntry
import jp.tukutano.tomagps.service.TrackPoint
import jp.tukutano.tomagps.ui.capture.PhotoCaptureActivity
import kotlinx.coroutines.launch
import java.io.File

class TrackFragment : Fragment(R.layout.fragment_track), OnMapReadyCallback {

    private val vm: TrackViewModel by viewModels()
    private lateinit var map: GoogleMap
    private var tracking = false
    private lateinit var fusedClient: FusedLocationProviderClient

    private fun onStopTracking() {
        // サービス停止は ViewModel 側でも行われるので省略可。ここではタイトル入力と保存呼び出し
        val edit = EditText(requireContext()).apply {
            hint = "ログタイトルを入力"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("タイトルを入力")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val title = edit.text.toString().takeIf { it.isNotBlank() } ?: "無題"
                // Map のスナップショットをサムネとして保存
                map.snapshot { bmp ->
                    val thumbUri = saveBmp(requireContext(), bmp)
                    // ここで必ず title, thumbnail を渡す
                    vm.stopAndSavePoints(title, thumbUri)
                    Toast.makeText(
                        requireContext(),
                        "ログを保存しました", Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("破棄", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Map
        (childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment)
            .getMapAsync(this)

        val fabStartStop = view.findViewById<FloatingActionButton>(R.id.fabStartStop)
        val fabCamera = view.findViewById<FloatingActionButton>(R.id.fabCamera)
        val fabMyLocation = view.findViewById<FloatingActionButton>(R.id.fabMyLocation)
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())

        fabStartStop.setOnClickListener {
            if (!tracking) {
                vm.start()
                tracking = true
                fabStartStop.setImageResource(R.drawable.ic_stop)
            } else {
                // 停止時に DB へ永続化
                onStopTracking()
                tracking = false
                fabStartStop.setImageResource(R.drawable.ic_play)
            }
        }

        fabCamera.setOnClickListener {
            if (tracking) {
                photoLauncher.launch(
                    Intent(requireContext(), PhotoCaptureActivity::class.java)
                )
            } else {
                Toast.makeText(requireContext(), "まず計測を開始してください", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        fabMyLocation.setOnClickListener {
            // 1) 権限確認
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                // 必要ならリクエスト
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
                return@setOnClickListener
            }
            // 2) 直近の現在地を取得
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && ::map.isInitialized) {
                    val now = LatLng(loc.latitude, loc.longitude)
                    // マーカーがいらなければ省略
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(now, 15f))
                } else {
                    Toast.makeText(requireContext(), "現在地が取得できません", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        /* Flow 監視 */
        lifecycleScope.launchWhenStarted {
            vm.path.collect { drawPath(it) }
        }
        lifecycleScope.launchWhenStarted {
            vm.distanceKm.collect { dist ->
                view.findViewById<TextView>(R.id.tvDistance).text =
                    String.format("%.1f km", dist)
            }
        }
        lifecycleScope.launchWhenStarted {
            vm.elapsed.collect { sec ->
                view.findViewById<TextView>(R.id.tvTime).text = toHms(sec)
                val speed = if (sec > 0) vm.distanceKm.value / (sec / 3600.0) else 0.0
                view.findViewById<TextView>(R.id.tvSpeed).text =
                    String.format("%.1f km/h", speed)
            }
        }
    }

    private fun saveBmp(context: Context, bmp: Bitmap?): Uri? {
        if (bmp == null) return null
        // キャッシュディレクトリ内にファイルを作成
        val file = File(context.cacheDir, "map_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // FileProvider を使って content:// URI を取得
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /* 地図描画 */
    private fun drawPath(points: List<TrackPoint>) {
        if (!::map.isInitialized || points.isEmpty()) return
        map.clear()
        val poly = PolylineOptions().apply {
            points.forEach { add(LatLng(it.lat, it.lng)) }
            width(6f)
            color(ContextCompat.getColor(requireContext(), R.color.purple_500))
        }
        map.addPolyline(poly)
        // カメラフォロー
        val last = points.last()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lng), 15f))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = false
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }
    }

    private fun toHms(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }


    // PhotoCaptureActivity の結果を受け取るランチャー
    private val photoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { data ->
                    val uriStr = data.getStringExtra("photo_uri") ?: return@let
                    val lat = data.getDoubleExtra("lat", Double.NaN)
                    val lng = data.getDoubleExtra("lng", Double.NaN)
                    val uri = Uri.parse(uriStr)
                    // タイムスタンプは現在時刻を使う
                    val entry = PhotoEntry(uri, lat, lng, System.currentTimeMillis())
                    // ViewModel へ追加
                    vm.addPhoto(entry)
                    // 地図上にもマーカーを表示
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                }
            }
        }

}
