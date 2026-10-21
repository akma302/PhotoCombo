package com.combo.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.combo.Const
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.SwitchMode
import com.combo.core.model.FramingMode
import com.combo.core.model.TransitionType

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

@Entity(
    tableName = "combo_items",
    foreignKeys = [ForeignKey(
        entity = ComboEntity::class,
        parentColumns = ["id"],
        childColumns = ["comboId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("comboId")]
)
data class ComboItemEntity(
    @PrimaryKey val id: String,
    val comboId: String,
    val zIndex: Int,
    val centerX: Float, val centerY: Float,
    val width: Float, val height: Float,
    val rotation: Float = 0f,
    val switchMode: String,
    val autoIntervalMs: Long = Const.DEFAULT_AUTO_INTERVAL_MS,
    val manualTransition: String,
    val autoTransition: String,
    /** Имя [com.combo.core.model.FramingMode]; хранится строкой, как и остальные enum'ы. */
    val framingMode: String,
) {
    val switchModeEnum: SwitchMode get() = SwitchMode.valueOf(switchMode)
    val manualTransitionEnum: TransitionType get() = TransitionType.valueOf(manualTransition)
    val autoTransitionEnum: TransitionType get() = TransitionType.valueOf(autoTransition)
    val framingModeEnum: FramingMode get() = FramingMode.valueOf(framingMode)
}

@Entity(
    tableName = "combo_item_contents",
    foreignKeys = [
        ForeignKey(
            entity = ComboItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [Index("itemId"), Index("photoId")]
)
data class ComboItemContentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val photoId: String,
    val orderIndex: Int,
    val contentScale: Float,
    val panX: Float, val panY: Float,
)

data class ItemWithContents(
    @Embedded val item: ComboItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId") val contents: List<ComboItemContentEntity>
)

data class ComboWithItems(
    @Embedded val combo: ComboEntity,
    @Relation(
        entity = ComboItemEntity::class,
        parentColumn = "id",
        entityColumn = "comboId"
    ) val items: List<ItemWithContents>
)
