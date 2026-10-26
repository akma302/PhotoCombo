package com.combo.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode

@Entity(tableName = "photos", indices = [Index("sha256", unique = true)])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val originalName: String?,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val sha256: String,
    val avgColor: Int,
    val importedAt: Long,
)

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey val id: String,
    val name: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val background: BackgroundSpec,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Рамка комбо. В рамке ровно одно фото: его id и положение внутри рамки хранятся здесь же
 * (раньше фото лежали отдельной таблицей `combo_item_contents`, см. миграцию 1→2).
 *
 * `photoId` ссылается на `photos` с `RESTRICT`: фото, занятое рамкой, удалить нельзя.
 */
@Entity(
    tableName = "combo_items",
    foreignKeys = [
        ForeignKey(
            entity = ComboEntity::class,
            parentColumns = ["id"],
            childColumns = ["comboId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [Index("comboId"), Index("photoId")]
)
data class ComboItemEntity(
    @PrimaryKey val id: String,
    val comboId: String,
    val zIndex: Int,
    val centerX: Float, val centerY: Float,
    val width: Float, val height: Float,
    val rotation: Float = 0f,
    /** Имя [com.combo.core.model.FramingMode]; хранится строкой, как и остальные enum'ы. */
    val framingMode: String,
    val photoId: String,
    val contentScale: Float,
    val panX: Float, val panY: Float,
) {
    val framingModeEnum: FramingMode get() = FramingMode.valueOf(framingMode)
}

data class ComboWithItems(
    @Embedded val combo: ComboEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "comboId"
    ) val items: List<ComboItemEntity>
)
