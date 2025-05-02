package jp.tukutano.tomagps.repository

import android.net.Uri
import jp.tukutano.tomagps.db.PhotoEntryDao
import jp.tukutano.tomagps.db.PhotoEntryEntity
import jp.tukutano.tomagps.service.PhotoEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PhotoEntryRepository(private val dao: PhotoEntryDao) {
    fun getPhotosByLog(logId: Long): Flow<List<PhotoEntry>> =
        dao.getByLogId(logId).map { list ->
            list.map { PhotoEntry(
                uri = android.net.Uri.parse(it.uri),
                lat = it.lat,
                lng = it.lng,
                time = it.timestamp
            ) }
        }

    fun savePhotos(logId: Long, photos: List<PhotoEntry>) {
        val entities = photos.map {
            PhotoEntryEntity(
                logId = logId,
                uri = it.uri.toString(),
                lat = it.lat,
                lng = it.lng,
                timestamp = it.time
            )
        }
        dao.insertAll(entities)
    }

    fun deleteByUri(uri: Uri) {
        dao.deleteByUri(uri.toString())
    }
}