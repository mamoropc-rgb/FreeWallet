# Загрузка FreeWallet 1.0.6 через телефон

1. Перед обновлением приложения открой **Ещё → Резервная копия → Экспорт**, сохрани резервную копию вне приложения.
2. Открой репозиторий GitHub `mamoropc-rgb/FreeWallet` на ветке `main`.
3. Из архива `FreeWallet-1.0.6-changed-files.zip` загрузи файлы с сохранением точной структуры папок. В существующих путях выбери замену файлов.
4. Убедись, что новые Kotlin-файлы находятся именно в `app/src/main/java/com/mamoro/karman/` и JUnit-тест в `app/src/test/java/com/mamoro/karman/`. Не создавай вторую папку `app` внутри `app`.
5. `app/build.gradle.kts` должен содержать `versionName = "1.0.6"` и `versionCode = 7`.
6. Проверь Actions: тесты, lint и Debug; затем шаги проверки сертификата, Release и проверки APK-подписи.
7. Из **Artifacts** скачать исключительно `Karman-1.0.6-RELEASE-signed` (внутри `app-release.apk`). `DEBUG-test-only` не устанавливать поверх текущего релиза.
8. Установить APK без удаления приложения. После запуска проверить: история и аналитика, переключение месяца и счета, график/пустой месяц, прежние счета и инвестиции, ночная и светлая темы.

## Новые файлы
- `app/src/main/java/com/mamoro/karman/HistoryAnalytics.kt`
- `app/src/main/java/com/mamoro/karman/HistoryAnalyticsUi.kt`
- `app/src/test/java/com/mamoro/karman/HistoryAnalyticsTest.kt`
- `docs/HISTORY-ANALYTICS-1.0.6.md`

## Заменить существующие
- `app/src/main/java/com/mamoro/karman/KarmanUi.kt`
- `app/build.gradle.kts`
- `.github/workflows/android.yml`
- `CHANGELOG.md`
- `README.md`
- `SIGNING-SETUP.md`

## Что нельзя загружать
Не загружать `.p12`, `.jks`, архивы с приватным ключом, пароли и файлы секретов. Секреты уже настроены в Actions и остаются неизменными. Пакет и постоянный сертификат не меняются.

## Важно
Это локально подготовленный код. Полный Android APK 1.0.6 **не собран** в этой среде. Если Actions завершится ошибкой, присылай первый красный шаг и строки вокруг ошибки. Не очищай данные приложения.
