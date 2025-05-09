package jp.tukutano.tomagps.ui.home

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
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
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.databinding.FragmentTrackBinding
import jp.tukutano.tomagps.service.PhotoEntry
import jp.tukutano.tomagps.service.TrackPoint
import jp.tukutano.tomagps.ui.capture.PhotoCaptureActivity
import jp.tukutano.tomagps.ui.detail.PhotoPreviewDialogFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class TrackFragment : Fragment(R.layout.fragment_track),
    OnMapReadyCallback {

    private var _binding: FragmentTrackBinding? = null
    private val binding get() = _binding!!
    private val resumeLogId: Long? by lazy {
        arguments?.getLong("resumeLogId").takeIf { it != 0L }
    }
    private val vm: TrackViewModel by viewModels {
        TrackViewModelFactory(requireActivity().application as Application, resumeLogId)
    }

    private lateinit var map: GoogleMap
    private lateinit var fusedClient: FusedLocationProviderClient

    private var tracking = false
    private var polyline: Polyline? = null
    private var lastDrawnSize = 0

    // 写真ピンを保持: URI → Marker
    private val photoMarkers = mutableMapOf<Uri, Marker>()

    // Launchers
    private val photoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("photo_uri")?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                val lat = result.data?.getDoubleExtra("lat", Double.NaN) ?: return@let
                val lng = result.data?.getDoubleExtra("lng", Double.NaN) ?: return@let
                addPhotoEntry(uri, lat, lng)
            }
        }
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pickPhoto(it) } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTrackBinding.bind(view)
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())
        setupMap()
        setupFabListeners()
        observeViewModel()

        // ① FragmentResultListener を登録
        childFragmentManager.setFragmentResultListener(
            PhotoPreviewDialogFragment.REQUEST_KEY_PHOTO_DELETED,
            viewLifecycleOwner
        ) { _, bundle ->
            val uri = bundle.getParcelable<Uri>(PhotoPreviewDialogFragment.KEY_URI)
            if (uri != null) {
                handlePhotoDeleted(uri)
            }
        }
    }

    /** PhotoPreviewDialogFragment から飛んできた削除イベント */
    private fun handlePhotoDeleted(uri: Uri) {
        // 地図上のマーカーだけを削除
        // 2) マーカーを地図から消して、コレクションからも外す
        photoMarkers[uri]?.remove()
        photoMarkers.remove(uri)
        // ViewModel 側のデータからも削除
        vm.removePhoto(uri)
        // 3) 残りのマーカーを再同期
        drawPhotoPins(vm.photos.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Map 初期化 */
    private fun setupMap() {
        (childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment)
            .getMapAsync(this)
    }

    /** FAB リスナー */
    private fun setupFabListeners() = binding.apply {
        fabStartStop.setOnClickListener { toggleTracking() }
        fabPhoto.setOnClickListener { if (tracking) imagePicker.launch("image/*") }
        fabCamera.setOnClickListener { if (tracking) capturePhoto() }
        fabMyLocation.setOnClickListener { moveToMyLocation() }
    }

    /** ViewModel フロー監視 */
    private fun observeViewModel() {
        with(binding) {
            lifecycleScope.launchWhenStarted {
                vm.distanceKm.collect { tvDistance.text = "%.1f km".format(it) }
            }
            lifecycleScope.launchWhenStarted {
                vm.elapsed.collect { tvTime.text = toHms(it) }
            }
        }
    }

    /** Map 準備完了 */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) map.isMyLocationEnabled = true

        // Polyline 初期化
        polyline = map.addPolyline(
            PolylineOptions()
                .width(6f)
                .color(ContextCompat.getColor(requireContext(), R.color.purple_500))
        )

        // マーカークリック: PhotoEntry プレビュー
        map.setOnMarkerClickListener { marker ->
            (marker.tag as? PhotoEntry)?.let { entry ->
                PhotoPreviewDialogFragment.newInstance(entry.uri.toString())
                    .setMarker(marker)          // ← ここでマーカーを渡す
                    .show(childFragmentManager, "photo_preview")
                return@setOnMarkerClickListener true
            }
            false
        }

        // ルート更新
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.path.collect { drawPath(it) }
        }
        // 写真ピン追加
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.photos.collect { drawPhotoPins(it) }
        }
    }

    /** トラッキング切り替え */
    private fun toggleTracking() = binding.fabStartStop.apply {
        if (!tracking) {
            vm.startTracking()
            setImageResource(R.drawable.ic_stop)
        } else {
            showSaveDialog()
            setImageResource(R.drawable.ic_play)
        }
        tracking = !tracking
    }

    /** 保存ダイアログ */
    private fun showSaveDialog() {
        val edit = EditText(requireContext()).apply { hint = "ログタイトルを入力" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("タイトルを入力")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val title = edit.text.toString().ifBlank { "無題" }
                map.snapshot { bmp ->
                    val uri = saveSnapshot(bmp)
                    vm.stopAndSaveLog(title, uri)
                    Toast.makeText(requireContext(), "ログを保存しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("破棄", null)
            .show()
    }

    /** 写真撮影 */
    private fun capturePhoto() = photoLauncher.launch(
        Intent(requireContext(), PhotoCaptureActivity::class.java)
    )

    /** ギャラリー写真選択 */
    private fun pickPhoto(uri: Uri) {
        vm.path.value.lastOrNull()?.let { p ->
            addPhotoEntry(uri, p.lat, p.lng, p.time)
        } ?: Toast.makeText(requireContext(), "位置情報がありません", Toast.LENGTH_SHORT).show()
    }

    /** PhotoEntry 追加＋マーカー作成 */
    private fun addPhotoEntry(uri: Uri, lat: Double, lng: Double, time: Long = System.currentTimeMillis()) {
        val entry = PhotoEntry(uri, lat, lng, time)
        vm.addPhoto(entry)
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        marker?.tag = entry
        if (marker != null) photoMarkers[uri] = marker
    }

    /** 現在地移動 */
    private fun moveToMyLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(it.latitude, it.longitude), 15f
                ))
            } ?: Toast.makeText(requireContext(), "現在地が取得できません", Toast.LENGTH_SHORT).show()
        }
    }

    /** 路線描画（増分のみ） */
    private fun drawPath(points: List<TrackPoint>) {
        for (i in lastDrawnSize until points.size) {
            val p = points[i]
            map.addCircle(
                CircleOptions()
                    .center(LatLng(p.lat, p.lng))
                    .radius(2.0)
                    .fillColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
            )
        }
        lastDrawnSize = points.size
        polyline?.points = points.map { LatLng(it.lat, it.lng) }
    }

    /** 写真ピン描画 */
    private fun drawPhotoPins(list: List<PhotoEntry>) {
        // 1) VMに存在しないURIのマーカーを削除
        val currentUris = list.map { it.uri }.toSet()
        photoMarkers.keys
            .filter { it !in currentUris }
            .forEach { uri ->
                photoMarkers.remove(uri)
                photoMarkers[uri]?.remove()
            }

        // 2) VMに存在するがマップに未登録のマーカーだけを追加
        list.forEach { entry ->
            if (!photoMarkers.containsKey(entry.uri)) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(entry.lat, entry.lng))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .title("Photo")
                )
                marker?.tag = entry
                if (marker != null) {
                    photoMarkers[entry.uri] = marker
                }
            }
        }
    }

    /** Bitmap → content:// URI */
    private fun saveSnapshot(bmp: Bitmap?): Uri? {
        bmp ?: return null
        val file = File(requireContext().cacheDir, "map_\${SystemClock.uptimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }

    private fun toHms(sec: Long) = "%02d:%02d:%02d".format(
        sec / 3600,
        (sec % 3600) / 60,
        sec % 60
    )
}
