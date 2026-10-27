package com.combo.di

import androidx.lifecycle.SavedStateHandle
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.viewer.PhotoViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * ViewModel'и экранов. Живут в commonMain без платформенных зависимостей: Koin
 * сам управляет их жизненным циклом через `koinViewModel()`, а область видимости
 * задаёт владелец из стека навигации.
 *
 * `SavedStateHandle` собирается вызывающим (экран навигации кладёт в него ключ перехода)
 * и передаётся параметром фабрики в обёртке [EntryArgs]: параметр типа `SavedStateHandle`
 * Koin трактует особо — не читает его из `parametersOf`, а создаёт из `CreationExtras`,
 * которых на desktop нет.
 *
 * У [PhotoViewerViewModel] аргументов перехода нет (photoId — параметр экрана), поэтому
 * handle создаётся пустым прямо здесь, чтобы не плодить особый случай у вызова.
 */
val viewModelModule = module {
    viewModel { LibraryViewModel(photoRepo = get(), picker = get(), settingsRepository = get()) }

    viewModel { ComboListViewModel(comboRepo = get(), photoRepo = get(), bitmapSource = get(), settingsRepository = get()) }

    viewModel { PhotoViewerViewModel(SavedStateHandle(), photoRepo = get(), bitmapSource = get()) }

    viewModel { (args: EntryArgs) ->
        ComboBuilderViewModel(
            savedStateHandle = args.handle,
            comboRepo = get(),
            photoRepo = get(),
            bitmapSource = get(),
            ids = get(),
            clock = get(),
            ioDispatcher = get(),
        )
    }
}

/**
 * Аргументы перехода для фабрик ViewModel — обёртка вокруг `SavedStateHandle`.
 *
 * Без обёртки не обойтись: параметр типа `SavedStateHandle` Koin не берёт из
 * `parametersOf`, а строит из `CreationExtras`, где обязателен `SavedStateRegistryOwner`
 * (на desktop его нет) — создание ViewModel падало бы. В обёртке handle доезжает до
 * конструктора как обычный параметр фабрики.
 */
class EntryArgs(val handle: SavedStateHandle)
