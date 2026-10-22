package com.combo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.combo.ui.common.designsystem.components.PcSegmentedControl
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.theme.PcTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.combo.di.EntryArgs
import com.combo.feature.builder.ComboBuilderScreen
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.library.LibraryScreen
import com.combo.feature.viewer.PhotoViewerScreen
import com.combo.ui.common.BackDispatcher
import com.combo.ui.common.BackKeyHost
import com.combo.ui.common.LocalBackDispatcher
import com.combo.ui.common.RegisterBackHandler
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Навигация приложения на Navigation 3: стек ключей [ScreenKey] и один `NavDisplay`.
 *
 * Стек — обычный `mutableStateListOf`: ключи не сериализуются и не восстанавливаются после
 * перезапуска (на desktop его не бывает), зато аргументы перехода типизированы и не
 * требуют `@Serializable`-регистрации.
 *
 * ВЛАДЕЛЬЦЫ VIEWMODEL. У корневых вкладок владелец — [rememberRootViewModelStoreOwner]:
 * выбор фото и настройки списка комбо обязаны переживать переход в просмотр и назад.
 * Каждая запись поверх вкладки получает свой владелец ([rememberEntryViewModelStoreOwner]) —
 * иначе `koinViewModel` вернул бы один и тот же экземпляр всем записям одного класса, и
 * билдер второго комбо открылся бы поверх состояния первого.
 *
 * ЗАЧЕМ ДВОЙНОЙ СЛОЙ «НАЗАД». Верхние обработчики (диалог выхода, режим выбора) кладут
 * себя через [RegisterBackHandler] и получают Esc первыми. Если никто не взял событие,
 * срабатывает нижний обработчик этого файла: шаг назад по стеку. Поэтому Esc ведёт себя
 * как системная кнопка «назад» — включая выход из просмотра к списку.
 *
 * `onBack` в `NavDisplay` не используется: у нас есть только Esc/мышь, и те идут через
 * [BackDispatcher]; кнопки «назад» на экранах вызывают `onBack` явно.
 */
@Composable
fun AppNav(backDispatcher: BackDispatcher) {
    val backStack = remember { mutableStateListOf<NavKey>(ScreenKey.Library) }
    val current = backStack.lastOrNull()
    val rootOwner = rememberRootViewModelStoreOwner()

    // Нижний обработчик: стек уходит на шаг назад. Держится всё время жизни AppNav,
    // поэтому регистрация «поверх» него всегда перекрывает его.
    DisposableEffect(backDispatcher, backStack) {
        val handler: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
        backDispatcher.push(handler)
        onDispose { backDispatcher.remove(handler) }
    }

    CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
        // Хост «назад» держит фокус и ловит Esc/боковую кнопку мыши; UI-тесты поднимают
        // экраны через такой же хост (см. BackKeyHost).
        BackKeyHost(backDispatcher) {
            PcSurface(Modifier.fillMaxSize(), color = PcTheme.colors.app) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.fillMaxSize(),
                            entryProvider = entryProvider {
                                entry<ScreenKey.Library> {
                                    // Владелец вкладки — корневой: уход в просмотр и назад не должен
                                    // сбрасывать режим выбора и выбранные фото.
                                    CompositionLocalProvider(LocalViewModelStoreOwner provides rootOwner) {
                                        LibraryScreen(
                                            onOpenViewer = { backStack.add(ScreenKey.Viewer(it)) },
                                            onCreateCombo = { backStack.add(ScreenKey.Builder(photos = it)) },
                                        )
                                    }
                                }
                                entry<ScreenKey.Combos> {
                                    CompositionLocalProvider(LocalViewModelStoreOwner provides rootOwner) {
                                        ComboListScreen(
                                            onOpenBuilder = { backStack.add(ScreenKey.Builder(comboId = it)) },
                                            onCreateCombo = { backStack.add(ScreenKey.Builder()) },
                                        )
                                    }
                                }
                                entry<ScreenKey.Viewer> { key ->
                                    EntryScope {
                                        PhotoViewerScreen(
                                            photoId = key.photoId,
                                            onBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                }
                                entry<ScreenKey.Builder> { key ->
                                    EntryScope {
                                        val vm: ComboBuilderViewModel = koinViewModel {
                                            parametersOf(EntryArgs(builderHandle(key)))
                                        }
                                        ComboBuilderScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            vm = vm,
                                        )
                                    }
                                }
                            },
                        )
                    }
                    if (current is ScreenKey.Library || current is ScreenKey.Combos) {
                        PcDivider()
                        PcSurface(Modifier.fillMaxWidth()) {
                            PcSegmentedControl(
                                options = listOf(PcOption(false, "Photos", PcIcons.Photo), PcOption(true, "Combos", PcIcons.Frame)),
                                value = current is ScreenKey.Combos,
                                onValueChange = { backStack.switchTab(if (it) ScreenKey.Combos else ScreenKey.Library) },
                                modifier = Modifier.fillMaxWidth().padding(PcTheme.spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Пер-запись владелец ViewModel: данные перехода передаются в `SavedStateHandle` так же,
 * как их передавал маршрут Nav2, поэтому конструкторы ViewModel не меняются от смены
 * навигационной библиотеки.
 */
private fun builderHandle(key: ScreenKey.Builder): SavedStateHandle {
    val args = buildMap {
        key.comboId?.let { put("comboId", it) }
        if (key.photos.isNotEmpty()) put("photos", key.photos.joinToString(","))
    }
    return SavedStateHandle(args)
}

/** Запись поверх вкладки живёт со своим ViewModel-хранилищем и уносит его при уходе. */
@Composable
private fun EntryScope(content: @Composable () -> Unit) {
    val owner = rememberEntryViewModelStoreOwner()
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

/**
 * Переключение корневой вкладки: поднимает уже открытую вкладку наверх или кладёт её
 * поверх, как `navigateTop` у Nav2 (popUpTo старта + singleTop).
 */
private fun MutableList<NavKey>.switchTab(tab: NavKey) {
    val index = indexOf(tab)
    if (index >= 0) {
        while (size > index + 1) removeAt(size - 1)
    } else {
        add(tab)
    }
}
