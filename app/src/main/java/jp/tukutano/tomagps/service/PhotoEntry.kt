package jp.tukutano.tomagps.service

import android.net.Uri

data class PhotoEntry(
    val uri: Uri,
    val lat: Double,
    val lng: Double,
    val time: Long
)