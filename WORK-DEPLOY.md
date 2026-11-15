# Развёртывание в ChatGPT Work через виртуальный браузер

1. Откройте ChatGPT Work и передайте ZIP **Karman-1.0.3-PremiumGlass-full-project.zip**. Попросите создать отдельную ветку `feature/freewallet-103-stability` в `mamoropc-rgb/FreeWallet`. Сначала создавайте ветку/коммиты, не перезаписывайте `main` без проверки.
2. Замените содержимое проекта полным извлечённым деревом ZIP с сохранением `.github/workflows/android.yml`. `Karman-PRIVATE-signing.zip` **не загружать** и не прикладывать к PR.
3. Проверьте `applicationId=com.mamoro.karman`, `versionCode=4`, `versionName=1.0.3` и отличия кода. Отдельно фиксируйте (Git commit) исправление ключей Compose, управление счетами, валютные курсы и Premium Glass; после каждого запускайте проверки. Не переписывайте историю операций.
4. Дайте GitHub Actions выполнить `testDebugUnitTest`, `lintDebug`, `assembleDebug`. Проверьте, что был создан **Debug APK**.
5. Через GitHub Settings → Secrets and variables → Actions убедитесь, что уже заданные четыре секрета `ANDROID_*` используются без изменения. Не печатайте их значения в логах, не загружайте исходный сертификат в репозиторий.
6. Запустите workflow `Karman Android — verified build and signed update` в ветке `main` (после проверки и принятия PR). Дождитесь зелёного статуса, артефакта `Karman-1.0.3-RELEASE-signed`, успешных `apksigner verify` и сертификата SHA-256.
7. Перед установкой на телефон экспортируйте резервную копию данных. Если ранее был установлен Debug APK, подписи Debug/Release не совпадают и обновление поверх Debug не установится.
8. Не утверждайте «готово к установке» до появления реально подписанного Release APK.

**Примечание:** текущий GitHub-коннектор в чате способен читать код, но создание ветки получило HTTP 403. Поэтому загрузка и GitHub Actions должны выполняться через Work с доступом на запись или после повторного подключения GitHub.
