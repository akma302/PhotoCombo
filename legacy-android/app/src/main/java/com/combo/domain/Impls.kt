package com.combo.domain

import android.net.Uri
import androidx.room.withTransaction
import com.combo.data.ComboDao
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val photoDao: PhotoDao,
    private val comboDao: ComboDao,
    private val storage: PhotoStorage,
    private val importer: PhotoImporter,
) : PhotoRepository {

    override fun all(): Flow<List<PhotoEntity>> = photoDao.all()

    override suspend fun get(id: String): PhotoEntity? = photoDao.get(id)

    override suspend fun delete(id: String): DeleteResult = withContext(Dispatchers.IO) {
        val photo = photoDao.get(id)
        if (photo == null) return@withContext DeleteResult.DELETED
        if (comboDao.isPhotoUsed(id)) return@withContext DeleteResult.BLOCKED_IF_USED_IN_COMBO
        photoDao.delete(photo)
        storage.photoFile(photo.fileName).delete()
        storage.thumbFile(id).delete()
        DeleteResult.DELETED
    }

    override fun thumbFile(photoId: String): File? =
        storage.thumbFile(photoId).takeIf { it.exists() }

    /** Сам конвейер импорта (хеш, копирование, EXIF, миниатюра, avgColor) живёт в [PhotoImporter]. */
    override suspend fun importPhotos(uris: List<Uri>): ImportResult = importer.importPhotos(uris)
}

@Singleton
class ComboRepositoryImpl @Inject constructor(
    private val db: com.combo.data.AppDb,
    private val comboDao: ComboDao,
) : ComboRepository {

    override fun all(): Flow<List<ComboWithItems>> =
        comboDao.all().map { list -> list.map { sorted(it) } }

    override suspend fun get(id: String): ComboWithItems? = comboDao.get(id)?.let { sorted(it) }

    override suspend fun saveCombo(
        combo: ComboEntity,
        items: List<ComboItemEntity>,
        contents: List<ComboItemContentEntity>,
    ) = db.withTransaction {
        comboDao.upsert(combo)
        comboDao.deleteItems(combo.id)
        comboDao.insertItems(items)
        comboDao.insertContents(contents)
    }

    override suspend fun delete(id: String) = comboDao.delete(id)

    /** @Relation не сортирует — contents по orderIndex и items по zIndex сортирует репозиторий. */
    private fun sorted(c: ComboWithItems) = c.copy(
        items = c.items.sortedBy { it.item.zIndex }.map { i ->
            i.copy(contents = i.contents.sortedBy { it.orderIndex })
        }
    )
}
