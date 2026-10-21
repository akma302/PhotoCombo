package com.combo.ui

import com.combo.domain.PhotoRepository
import com.combo.ui.common.BackDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.koin.core.KoinApplication

/**
 * Общая обвязка desktop UI-тестов: главный диспетчер, изолированный Koin и «назад».
 *
 * Главный диспетчер — Unconfined: ViewModel'и и фейковые репозитории выполняются сразу,
 * и тесту не приходится ждать реальных тиков. [ioDispatcher] тоже виртуальный и делит
 * планировщик с главным: сохранение в редакторе проходит до конца на прогоне виртуального
 * времени, а гейты (`saveGate`) позволяют поймать промежуточное состояние `saving`.
 *
 * Koin-приложения теста закрываются после каждого теста: контексты изолированные,
 * состояние между тестами не разделяется.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class UiTestBase {

    protected val mainDispatcher = UnconfinedTestDispatcher()

    protected val ioDispatcher = UnconfinedTestDispatcher(mainDispatcher.scheduler)

    protected val backDispatcher = BackDispatcher()

    private val koinApps = mutableListOf<KoinApplication>()

    /** Koin экрана: `ThumbImage` берёт из него репозиторий, декодер и IO-диспетчер. */
    protected fun trackKoin(photoRepo: PhotoRepository): KoinApplication =
        uiTestKoin(photoRepo).also { koinApps += it }

    /**
     * Koin произвольной конфигурации (например, с `viewModelModule`): закрывается после
     * теста так же, как экранный.
     */
    protected fun trackKoin(app: KoinApplication): KoinApplication = app.also { koinApps += it }

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDownUiTestBase() {
        koinApps.forEach { it.close() }
        koinApps.clear()
        Dispatchers.resetMain()
    }
}
