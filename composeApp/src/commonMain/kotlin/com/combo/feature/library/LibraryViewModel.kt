package com.combo.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.Const
import com.combo.core.image.ImagePicker
import com.combo.data.PhotoEntity
import com.combo.domain.DeleteResult
import com.combo.domain.ImportResult
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val photoRepo: PhotoRepository,
    private val picker: ImagePicker,
) : ViewModel() {

    val photos: StateFlow<List<PhotoEntity>> = photoRepo.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Выбранные id В ПОРЯДКЕ ВЫБОРА: `ComboBuilder` получает список и раскладывает по
     * рамкам именно в этом порядке, а `Set` его терял.
     */
    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected: StateFlow<List<String>> = _selected.asStateFlow()

    /**
     * Режим выбора включается долгим нажатием или пунктом «Select» и НЕ выключается сам,
     * когда выбор пуст: пользователь снимает галочки и думает дальше, а не начинает
     * случайно открывать фото вместо касаний. Выход — «отмена» в заголовке или «назад».
     */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selected.value = emptyList()
    }

    /** Переключение выбора имеет смысл только в режиме выбора. */
    fun toggle(id: String) {
        if (!_selectionMode.value) return
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        exitSelectionMode()
    }

    fun deleteSelected() {
        val ids = _selected.value
        _selected.value = emptyList()
        viewModelScope.launch {
            for (id in ids) {
                when (photoRepo.delete(id)) {
                    DeleteResult.BLOCKED_IF_USED_IN_COMBO ->
                        _messages.send("Photo is used in a combo and cannot be deleted")
                    else -> {}
                }
            }
        }
    }

    /**
     * Импорт: диалог выбора файлов показывает [ImagePicker], ViewModel лишь импортирует
     * результат. Пустой выбор — отмена диалога: сообщения об этом не шлём, как и раньше,
     * когда пустой результат системного пикера просто не доходил до импорта.
     */
    fun import() {
        viewModelScope.launch {
            val sources = picker.pickImages()
            if (sources.isEmpty()) return@launch
            val result = photoRepo.importPhotos(sources)
            _messages.send(importMessage(result))
        }
    }

    /**
     * Собирает список фото для билдера. Больше [Const.MAX_ITEMS] рамок комбо всё равно не
     * вместит, поэтому лишние отбрасываются С ЯВНЫМ сообщением: молчаливый обрез выглядел
     * бы как «фото потерялись».
     */
    suspend fun photosForNewCombo(): List<String> {
        val ids = _selected.value
        if (ids.size > Const.MAX_ITEMS) {
            _messages.send("Only the first ${Const.MAX_ITEMS} photos will be used")
        }
        exitSelectionMode()
        return ids.take(Const.MAX_ITEMS)
    }

    private fun importMessage(r: ImportResult): String = buildString {
        append("Imported: ${r.added}")
        if (r.duplicates > 0) append(", already added: ${r.duplicates}")
        if (r.failed > 0) append(", failed: ${r.failed}")
    }
}
