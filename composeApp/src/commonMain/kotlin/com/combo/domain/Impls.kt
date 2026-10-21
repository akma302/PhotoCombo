package com.combo.domain

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.combo.core.image.ImageSource
import com.combo.data.AppDb
import com.combo.data.ComboDao
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/** Репозиторий фото: владеет сущностями, конвейер импорта делегирует [PhotoImporter]. */
class PhotoRepositoryImpl(
    private val photoDao: PhotoDao,
    private val comboDao: ComboDao,
    private val storage: PhotoStorage,
    private val files: FileSystem,
    private val importer: PhotoImporter,
    private val ioDispatcher: CoroutineDispatcher,
) : PhotoRepository {

    override fun all(): Flow<List<PhotoEntity>> = photoDao.all()

    override suspend fun get(id: String): PhotoEntity? = photoDao.get(id)

    override suspend fun delete(id: String): DeleteResult = withContext(ioDispatcher) {
        val photo = photoDao.get(id)
        if (photo == null) return@withContext DeleteResult.DELETED
        if (comboDao.isPhotoUsed(id)) return@withContext DeleteResult.BLOCKED_IF_USED_IN_COMBO
        photoDao.delete(photo)
        storage.deletePhotoFiles(photo.fileName, id)
        DeleteResult.DELETED
    }

    override fun thumbPath(photoId: String): Path? =
        storage.thumbPath(photoId).takeIf { files.exists(it) }

    override suspend fun importPhotos(sources: List<ImageSource>): ImportResult =
        importer.importPhotos(sources)
}

class ComboRepositoryImpl(
    private val db: AppDb,
    private val comboDao: ComboDao,
) : ComboRepository {

    override fun all(): Flow<List<ComboWithItems>> =
        comboDao.all().map { list -> list.map { sorted(it) } }

    override suspend fun get(id: String): ComboWithItems? = comboDao.get(id)?.let { sorted(it) }

    override suspend fun saveCombo(combo: ComboEntity, items: List<ComboItemEntity>) {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                comboDao.upsert(combo)
                comboDao.deleteItems(combo.id)
                comboDao.insertItems(items)
            }
        }
    }

    override suspend fun delete(id: String) = comboDao.delete(id)

    /** @Relation не сортирует — items по zIndex сортирует репозиторий. */
    private fun sorted(c: ComboWithItems) = c.copy(items = c.items.sortedBy { it.zIndex })
}
