---
title: Виджет «Неделя» — история разработки
tags: [android, kotlin, glance, claude-code, week-widget]
repo: https://github.com/lemantik/week-widget
created: 2026-09-25
updated: 2026-09-26
status: работает на телефоне
---

# Виджет «Неделя» — история разработки

Android-виджет на Kotlin + Jetpack Glance: 7 дней вперёд от сегодня, 7 колонок,
ближние дни шире и крупнее, события из системного календаря (`CalendarContract`).

- Репозиторий: https://github.com/lemantik/week-widget
- Релизы (APK): https://github.com/lemantik/week-widget/releases
- Контекст для Claude: `CLAUDE.md` в корне репозитория

## Сессия 1 — 2026-09-25…26 (Claude Code в облаке)

**Запрос:** дополнить исходники до полного Gradle-проекта; GitHub Actions — сборка release APK
на каждый push в `main` и публикация в Release; подпись постоянным ключом из GitHub Secrets.

**Что было на входе:** `week-widget.zip` с `app/src/main` (код виджета уже написан ранее)
и дубль `WeekWidget.kt` в корне.

**Что сделано:**
1. Распакован `app/src/main`, архив и дубль удалены.
2. Gradle Kotlin DSL + version catalog + wrapper 8.14.3; AGP 8.7.3, Kotlin 2.0.21, Compose BOM,
   glance-appwidget 1.1.1, work-runtime-ktx 2.10.0, minSdk 26. Добавлены тема и адаптивная иконка
   (7 столбиков убывающей ширины).
3. Workflow `release.yml`: сборка на любой ветке, релиз `v1.0.<run_number>` только из `main`,
   без секретов `main` падает (чтобы не выпустить APK с debug-подписью).
4. Сгенерирован keystore (RSA 4096, 10000 дней, alias `weekwidget`), секреты добавлены в репозиторий.
5. PR [lemantik/week-widget#1](https://github.com/lemantik/week-widget/pull/1) смержен →
   релиз [v1.0.3](https://github.com/lemantik/week-widget/releases/tag/v1.0.3).
   Подпись APK проверена: сертификат `CN=Week Widget`, SHA-256 `B3:45:CD:…:0F:34`.
6. APK установлен на телефон — виджет работает.

**Проблемы по дороге:**
- В облачной сессии заблокирован `dl.google.com` (Android SDK, Google Maven) — собрать локально
  нельзя, компиляцию проверяли через CI на ветке.
- Первая сборка упала: `No value passed for parameter 'intent'` —
  reified `actionStartActivity<MainActivity>()` живёт в `androidx.glance.action`, не хватало импорта.
- Предупреждение о Node 20 в Actions → обновлены checkout@v7, setup-java@v6, setup-gradle@v6,
  upload-artifact@v7.
- Версии начались с 1.0.3, т.к. два запуска workflow ушли на проверку ветки.

**Важно помнить:**
- `week-widget-release.jks` + пароль хранятся у меня (менеджер паролей). Потеря ключа =
  новые версии не встанут поверх установленной.
- Внешний вид — константы в начале `WeekWidget.kt`.

## Как продолжить в новой сессии

Открыть Claude Code с репозиторием `lemantik/week-widget` и написать, например:

> Продолжаем работу над виджетом «Неделя». Прочитай CLAUDE.md и docs/HISTORY.md.
> Задача: …

После изменений: push в `main` → через ~3 минуты новый APK в Releases, ставится поверх старого.
Не забыть дописать сюда новую сессию.
