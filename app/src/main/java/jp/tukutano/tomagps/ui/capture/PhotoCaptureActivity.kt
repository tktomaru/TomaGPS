// PhotoCaptureActivity.kt
package jp.tukutano.tomagps.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.gms.location.LocationServices
import jp.tukutano.tomagps.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var fabCapture: FloatingActionButton
    private var imageCapture: ImageCapture? = null

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.CAMERA] == true
            if (granted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        previewView = findViewById(R.id.previewView)
        fabCapture  = findViewById(R.id.fabCapture)

        fabCapture.setOnClickListener { takePhoto() }

        // カメラ・位置情報権限をまとめてリクエスト
        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return

        // 保存ファイルを準備
        val dir = externalMediaDirs.firstOrNull()?.let {
            File(it, "TomaGPS").apply { mkdirs() }
        } ?: filesDir
        val file = File(
            dir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        ic.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 位置情報を添付して結果返却
                    attachLocationAndFinish(file)
                }
                override fun onError(exc: ImageCaptureException) {
                    exc.printStackTrace()
                }
            }
        )
    }

    private fun attachLocationAndFinish(file: File) {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        // 最終位置取得
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fused.lastLocation.addOnSuccessListener { loc ->
                val result = Intent().apply {
                    putExtra("photo_uri", Uri.fromFile(file).toString())
                    loc?.let {
                        putExtra("lat", it.latitude)
                        putExtra("lng", it.longitude)
                    }
                }
                setResult(RESULT_OK, result)
                finish()
            }.addOnFailureListener {
                // 位置取得失敗でもファイル URI は返す
                setResult(RESULT_OK, Intent().apply {
                    putExtra("photo_uri", Uri.fromFile(file).toString())
                })
                finish()
            }
        } else {
            // 権限なければ URI のみ
            setResult(RESULT_OK, Intent().apply {
                putExtra("photo_uri", Uri.fromFile(file).toString())
            })
            finish()
        }
    }
}
