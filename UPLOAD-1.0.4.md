# FreeWallet 1.0.4 — инструкция по обновлению с телефона

## Обновление (не удаляйте 1.0.3!)

1. Сохраните резервную копию через **Ещё → Экспорт** (не публикуйте этот JSON).
2. Распакуйте `Karman-1.0.4-changed-files.zip`.
3. Загрузите файлы в **тот же репозиторий**, заменяя совпадающие пути; новые файлы создайте по пути из архива. Не кладите сам ZIP в корень вместо исходников.
4. Убедитесь, что `.github/workflows/android.yml`, `app/build.gradle.kts` и `app/src/main/AndroidManifest.xml` тоже обновлены.
5. Дождитесь зелёной сборки **GitHub Actions**.
6. В **Artifacts** скачайте `Karman-1.0.4-RELEASE-signed`, распакуйте `app-release.apk` и установите поверх 1.0.3.
7. **Не устанавливайте DEBUG-test-only**: у него другой ключ подписи.

## Изменённые файлы

- `.github/workflows/android.yml` — номер версии в проверках и именах артефактов.
- `app/build.gradle.kts` — `versionCode = 5`, `versionName = "1.0.4"`.
- `app/src/main/AndroidManifest.xml` — сервис доступа к уведомлениям.
- `app/src/main/java/com/mamoro/karman/KarmanUi.kt` — исправление глобального контраста текста и обновление экрана при покупке.
- `app/src/main/java/com/mamoro/karman/PremiumHomeUi.kt` — цвет текста внутри карточек и быстрых кнопок.
- `app/src/main/java/com/mamoro/karman/SettingsUi.kt` — карточка настроек импорта.
- `app/src/main/java/com/mamoro/karman/Models.kt` — настройки банковского импорта.
- `app/src/main/java/com/mamoro/karman/KarmanViewModel.kt` — обновление состояния после импорта.
- `app/src/main/java/com/mamoro/karman/LedgerDb.kt` — сохранение покупок и защита от дублей.
- `app/src/main/java/com/mamoro/karman/BankPushParser.kt` — **новый** парсер.
- `app/src/main/java/com/mamoro/karman/BankImportRules.kt` — **новый** алгоритм идентификации уведомлений.
- `app/src/main/java/com/mamoro/karman/BankNotificationListener.kt` — **новый** Android-сервис.
- `app/src/main/java/com/mamoro/karman/BankImportSettingsUi.kt` — **новый** интерфейс настроек.
- `app/src/test/java/com/mamoro/karman/BankPushParserTest.kt` — **новый** тест парсинга.
- `app/src/test/java/com/mamoro/karman/LedgerDbTest.kt` — тест импорта с использованием Robolectric.
- `docs/BANK-NOTIFICATIONS-1.0.4.md`, `README.md`, `CHANGELOG.md` — документация.

**Не загружайте** `.p12`, `.jks`, архив приватной подписи, пароли, локальную базу и резервные копии с личными данными. GitHub Secrets для подписи остаются прежними.

## После установки

Во вкладке **Ещё**: выберите рублёвый счёт, откройте системные настройки уведомлений и дайте доступ «Карману», затем включите автоматический учёт. Новые покупки должны появляться в **Истории** и отражаться в **До дохода**. Для проверки используйте новые реальные сообщения от Т-Банка, поскольку старые уведомления назад не обрабатываются.
