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
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
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

    // １度だけ作るPolyline
    private var polyline: Polyline? = null
    // 追加済みのデータ数を覚えておく
    private var lastDrawnSize = 0

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

        // ③ fabCamera のクリックでギャラリー起動
        view.findViewById<FloatingActionButton>(R.id.fabPhoto)
            .setOnClickListener {
                if (tracking) {
                    // 画像タイプを指定してギャラリーを開く
                    imagePicker.launch("image/*")
                } else {
                    Toast.makeText(
                        requireContext(),
                        "まず計測を開始してください",
                        Toast.LENGTH_SHORT
                    ).show()
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
    // ViewModel の path.collect { drawPath(it) } から呼ばれる
    private fun drawPath(points: List<TrackPoint>) {
        if (!::map.isInitialized || points.isEmpty()) return

        // 増分だけ点を追加
        for (i in lastDrawnSize until points.size) {
            val p = points[i]
            map.addCircle(
                CircleOptions()
                    .center(LatLng(p.lat, p.lng))
                    .radius(2.0)
                    .strokeWidth(0f)
                    .fillColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
            )
        }
        lastDrawnSize = points.size

        // ポリライン全体を更新
        polyline?.points = points.map { LatLng(it.lat, it.lng) }

        // カメラを追従
        val last = points.last()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(last.lat, last.lng),
            map.cameraPosition.zoom
        ))
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
        // 1) 最初に一度だけ Polyline を作成
        polyline = map.addPolyline(
            PolylineOptions()
                .width(6f)
                .color(ContextCompat.getColor(requireContext(), R.color.purple_500))
        )

        // 2) Polyline が用意できてから Flow を購読
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.path.collect { points ->
                drawPath(points)
            }
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
    // Fragmentが再び前面に来たときにも再描画
    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) {
            drawPath(vm.path.value)
        }
    }


    // ① GetContent で画像選択用ランチャーを登録
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 最後の地点を取得して PhotoEntry を作成
            val lastPoint = vm.path.value.lastOrNull()
            if (lastPoint != null) {
                val entry = PhotoEntry(
                    lat   = lastPoint.lat,
                    lng   = lastPoint.lng,
                    time  = lastPoint.time,
                    uri   = it
                )
                vm.addPhoto(entry)
                Toast.makeText(requireContext(),
                    "写真を追加：(${entry.lat}, ${entry.lng})",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(requireContext(),
                    "位置情報がありません",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
