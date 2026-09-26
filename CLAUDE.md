# CLAUDE.md

Android-виджет «Неделя»: 7 дней вперёд от сегодня, 7 колонок, ближние дни шире и крупнее,
события из `CalendarContract` (системный календарь, куда синхронизируется Google Calendar).
Kotlin + Jetpack Glance. Общение с владельцем — на русском.

## Структура

- `app/src/main/java/com/anton/weekwidget/`
  - `WeekWidget.kt` — UI виджета; **настройки внешнего вида в начале файла**
    (`COLUMN_WEIGHTS` — ширины колонок, `tierFor` — шрифты/время/строки по дням)
  - `CalendarRepository.kt` — запрос `Instances` на 7 дней, многодневные и all-day события
  - `WidgetUpdater.kt` — кладёт неделю (JSON через `WeekCodec` из `Model.kt`) в Glance-state и перерисовывает
  - `Scheduling.kt` — WorkManager: обновление в полночь, по изменению календаря (content URI trigger), раз в час
  - `Receivers.kt` — жизненный цикл виджета, смена часового пояса/времени/локали
  - `MainActivity.kt` — Compose-экран запроса `READ_CALENDAR`
- `gradle/libs.versions.toml` — все версии зависимостей
- `.github/workflows/release.yml` — CI

## Сборка

- Gradle wrapper 8.14.3, AGP 8.7.3, Kotlin 2.0.21, JDK 17, compileSdk/targetSdk 35, minSdk 26.
- Локально: `./gradlew assembleDebug` (нужен Android SDK).
- **В облачных сессиях Claude Code `dl.google.com` / Google Maven заблокированы сетевой политикой** —
  локальная сборка невозможна. Проверять компиляцию через CI: push в любую ветку запускает сборку,
  результат — в GitHub Actions (артефакт с APK).

## CI и релизы

- Push в любую ветку → `assembleRelease` + APK в артефактах запуска.
- Push в `main` → дополнительно GitHub Release `v1.0.<run_number>` с APK.
  `versionCode` = `github.run_number`, поэтому каждая сборка ставится поверх предыдущей.
- Изменения только в `*.md` релиз не запускают (`paths-ignore`).
- Подпись: секреты `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`weekwidget`),
  `SIGNING_KEY_PASSWORD`. Без секретов на `main` сборка падает намеренно; на других ветках
  подписывается debug-ключом (только как проверка — такой APK не ставить на телефон).
- SHA-256 сертификата релизного ключа:
  `B3:45:CD:64:83:BD:73:C2:9F:D1:F2:C1:AE:F0:0C:40:B4:BA:9D:36:97:C0:2C:C6:51:B8:CB:F5:4C:98:0F:34`.
  Keystore в репозитории нет и не должно быть (`*.jks` в `.gitignore`); копия — у владельца.

## Ограничения и грабли

- Glance: не больше 10 дочерних элементов в `Column` → максимум 7 событий на день (`MAX_EVENTS`).
- `actionStartActivity<T>()` (reified) импортируется из `androidx.glance.action`,
  а версия с `Intent` — из `androidx.glance.appwidget.action`. Нужны оба импорта.
- `isMinifyEnabled = false` — R8 не включали, не проверяли с Glance/WorkManager.

## История

См. `docs/HISTORY.md`.
