# Реализация UI-компонентов

Спецификация: [DESIGN_SYSTEM.md v0.3](DESIGN_SYSTEM.md).
Общая ветка задачи: `feature_0005_uiComponents`, итоговый PR направлен в `main`.
Изолированные рабочие копии используются для параллельной реализации; интеграция
компонентов производится merge-коммитами с сохранением отдельных веток.

## Состав и ветки

| Часть | Ветка |
|---|---|
| Архитектурные ограничения и синтетические проверки | `feature_0006_designSystemArchitecture` |
| Токены и PcTheme | `feature_0007_pcTheme` |
| PcIcons: 20 оригинальных ImageVector | `feature_0008_pcIcons` |
| PcText | `feature_0009_pcText` |
| PcSurface | `feature_0010_pcSurface` |
| PcDivider | `feature_0011_pcDivider` |
| PcIcon | `feature_0012_pcIcon` |
| Общий Layout, поверхности взаимодействия и исходный индикатор фокуса | `feature_0013_pcControlFoundation` |
| PcActionGroup и PcOption | `feature_0014_pcActionGroup` |
| PcCheckbox | `feature_0015_pcCheckbox` |
| PcTooltip и позиционирование popup | `feature_0016_pcTooltip` |
| PcButton | `feature_0017_pcButton` |
| PcSegmentedControl | `feature_0018_pcSegmentedControl` |
| PcDialog и desktop backend | `feature_0019_pcDialog` |
| PcTextField | `feature_0020_pcTextField` |
| PcIconButton | `feature_0021_pcIconButton` |
| PcSelect | `feature_0022_pcSelect` |
| Галерея, системный шрифт и визуальная матрица | `feature_0023_componentGallery` |
| Появление popup и собственные тени | `feature_0029_popupAppearance` |
| Палитра «Графит · тон», тональный фокус и мягкое нижнее свечение | `feature_0031_graphiteFocusGlow` |
| Спецификация v0.3 и визуальный лист «Графит · тон» | `feature_0032_graphiteFocusSpec` |
| Регрессии нижней полосы, свечения и способов ввода | `feature_0033_graphiteFocusTests` |

Отдельные регрессии, выявленные проверкой компонентов, сохраняются в ветках
`bug_0024_segmentFocusOutline`, `bug_0025_busyButtonGeometry`,
`bug_0026_dialogFallbackFocus`, `bug_0027_selectActiveValue`,
`bug_0028_wrappedActionBounds` и также интегрируются в общую ветку.

## Использование

Токены/тема/примитивы/иконки/компоненты располагаются в подпакетах
`com.combo.ui.common.designsystem`. Общий код использует `PcTheme`, desktop-хост —
`com.combo.ui.common.PcDesktopTheme`. Файлы системных шрифтов не входят в репозиторий.

```kotlin
PcDesktopTheme {
    PcSurface {
        PcButton(
            text = "Save",
            onClick = onSave,
            variant = PcButtonVariant.Primary,
            icon = PcIcons.Check,
        )
    }
}
```

Ввод и семантика остаются в Foundation (`clickable`, `toggleable`, `selectable`,
`BasicTextField`). Внешний вид, геометрия и feedback задаются собственными
Layout/Canvas/Modifiers. Направленные иконки зеркалируются в RTL. Выборы и текст
передаются снаружи, компоненты не создают бизнес-состояние.

## «Графит · тон» (v0.3)

Утверждён светлый нейтральный accent `#DCE1E6`, onAccent `#1C2528`, hover `#35383E`,
pressed `#41454C` и focusFill `#3A3C41`. Остальные роли Dark остаются прежними.
Контракт `PcTheme.focus` объединяет высоту внутреннего градиента 10 dp с alpha 0.14
и нижнего мягкого свечения 4 dp с максимальной alpha 0.35. Полоса толщиной 2 dp
обрезается shape; внешнего прямоугольника вокруг контрола нет. `focusOffset` 2 dp
означает свободное пространство, gutter `PcActionGroup` 4 dp сохраняется для свечения.

Кнопки, checkbox, сегменты и select показывают индикатор только при клавиатурном
вводе. Поле показывает также фокус редактирования мышью. Primary сохраняет свою
заливку, использует полосу onAccent и светлое свечение accent; ошибочное поле сохраняет
danger-границу, текст ошибки и danger-индикатор фокуса. Геометрия, hit test,
callbacks и runtime-векторы не меняются.

Изменения реализации и документации интегрируются отдельными ветками из таблицы.
Проверка v0.3 от 2026-10-22 на Windows: полный `desktopTest` — **411 тестов, 0 failures,
0 errors, 1 skipped** (не задан опциональный каталог личных фотографий). Обновлены
84 PNG галереи по 12 сочетаниям ширины/density/fontScale; фактический шрифт — Segoe UI.
Просмотрены фокус основной кнопки, поле ввода на узком экране при density 1.5 и
fontScale 1.25, диалог. Пиксельные тесты проверяют градиент и нижнее свечение при
density 1.0/1.5, fontScale 1.0/1.25 и LTR/RTL, непрозрачную полосу, отсутствие
внешней рамки сверху/сбоку, прежние bounds и исключение свечения из hit area.
Реальными событиями проверены mouse → Tab, mouse → Enter, mouse → стрелка сегмента,
редактирование поля с ошибкой, busy/disabled. Переключение DPI Windows и ручной
обход отдельного окна не выполнялись.

## Воспроизводимая галерея

Запуск: `./gradlew.bat :composeApp:runDesignSystemGallery`.
Это отдельное окно без DI, базы и личной библиотеки; оно показывает настоящие
компоненты, допускает ввод текста, выбор режима/формата, открытие меню и диалога.
Обычный запуск PhotoCombo продолжает открывать продуктовые экраны.

Полная проверка: `./gradlew.bat :composeApp:desktopTest --offline --console=plain`.
Только компоненты: `./gradlew.bat :composeApp:desktopTest --tests '*designsystem*' --offline`.

`ComponentGalleryTest` создаёт PNG в `composeApp/build/ui-previews/design-system/`
для ширин 420/800/1200 dp, density 1.0/1.5 и fontScale 1.0/1.25:

- `top` и `bottom` — части прокручиваемой галереи;
- `focus`, `hover`, `pressed` — реальные состояния кнопки от focus/pointer input;
- `menu`, `dialog` — поверхности открытых платформенных слоёв;
- `font.txt` — фактически выбранный системный шрифт.

Галерея проверяет ввод текста с пробелами, переключение флажка, прямой выбор значения,
Escape и изменение ширины/плотности/шрифта. Компонентные тесты отдельно проверяют
Tab, RTL, роли/имена, disabled/busy, отмену нажатия, индикатор фокуса, динамические options,
открытое меню при resize и callback ровно один раз. Векторные тесты проверяют реальные
границы paths со stroke и кэширование экземпляров.

Поле `Required name` — редактируемый пример валидации: пустое или состоящее только
из пробелов значение показывает `Enter a name`; ввод имени снимает ошибку.

Density в тесте моделирует масштаб клиентской сцены; тест не переключает настройки
Windows и не доказывает поведение её системной строки заголовка. Проверка через
скриншоты галереи и автоматические действия не является ручной проверкой всех
контролов в работающем приложении.

Проверка v0.2 от 2026-10-22 на Windows: полный `desktopTest` — **403 теста, 0 failures,
0 errors, 1 skipped** (опциональная проверка импорта личного каталога фотографий).
Созданы 84 PNG по 12 комбинациям размеров/density/fontScale. Фактический шрифт —
Segoe UI. Просмотрены снимки широкой и узкой галереи, состояний контролов и диалога;
снимки помогли выявить и закрыть перенос короткой подписи сегмента. Ручной обход
галереи мышью в отдельном окне и переключение системного DPI не выполнялись.

## Граница этапа

Поставлен набор компонентов и его галерея. Builder, выбор фотографий, библиотека и
продуктовые диалоги пока используют существующий UI. Перенос builder по разделу 7
спецификации и его снимки на тестовых фотографиях выполняются отдельным PR;
компонентная галерея не заменяет эту приёмку. Остальные экраны мигрируют после него.
