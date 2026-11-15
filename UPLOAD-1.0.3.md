# Karman 1.0.3 — что загрузить с телефона

Обновление **поверх установленной подписанной 1.0.2**. Перед установкой экспортируйте резервную копию из «Ещё» — это важно для финансовых данных. **Не удаляйте приложение** и **не очищайте его данные** из-за вылета на вкладке «Счета».

**Репозиторий**: `mamoropc-rgb/FreeWallet`  · **ветка**: `main`

Скачайте `Karman-1.0.3-changed-files.zip`, распакуйте и загрузите файлы с сохранением путей. ZIP целиком в корень репозитория загружать не надо.

## Обязательные файлы для компиляции (новые пути отмечены NEW)

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/main/java/com/mamoro/karman/AccountRules.kt` **(NEW)**
- `app/src/main/java/com/mamoro/karman/AccountsUi.kt`
- `app/src/main/java/com/mamoro/karman/FxRates.kt` **(NEW)**
- `app/src/main/java/com/mamoro/karman/KarmanUi.kt`
- `app/src/main/java/com/mamoro/karman/KarmanViewModel.kt`
- `app/src/main/java/com/mamoro/karman/LedgerDb.kt`
- `app/src/main/java/com/mamoro/karman/LedgerMath.kt`
- `app/src/main/java/com/mamoro/karman/PremiumFxPolicy.kt` **(NEW)**
- `app/src/main/java/com/mamoro/karman/PremiumHomeUi.kt`
- `app/src/main/java/com/mamoro/karman/SettingsUi.kt`
- `app/src/main/java/com/mamoro/karman/UntilIncomeMath.kt`
- `app/src/main/java/com/mamoro/karman/UntilIncomeUi.kt`
- `app/src/test/java/com/mamoro/karman/AccountRulesTest.kt` **(NEW)**
- `app/src/test/java/com/mamoro/karman/FxRatesTest.kt` **(NEW)**
- `app/src/test/java/com/mamoro/karman/LedgerDbTest.kt`
- `app/src/test/java/com/mamoro/karman/PremiumFxPolicyTest.kt` **(NEW)**

## Документация и описание изменений

- `CHANGELOG.md`
- `README.md`
- `SIGNING-SETUP.md`
- `VERIFY.md`
- `WORK-DEPLOY.md`
- `docs/PREMIUM-GLASS-1.0.3.md` **(NEW)**
- `UPLOAD-1.0.3.md`

## Порядок и проверка

1. Сохранить подготовленные файлы в GitHub. Не загружать `Karman-PRIVATE-signing.zip`, пароли и `.p12`.
2. Проверить запуск Actions: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, затем `apksigner verify`.
3. В разделе `Artifacts` брать **только `Karman-1.0.3-RELEASE-signed`**. Debug APK не подходит для обновления предыдущего подписанного релиза.
4. Установить APK поверх версии 1.0.2 без удаления приложения.
5. Проверить: после создания/старой облигации вкладка «Счета» больше не вылетает; счёт можно переименовать; пустой второй счёт можно удалить; счёт с инвестициями удалить нельзя; курсы задаются через «Ещё»; меняется только курс новых операций, старые записи остаются прежними.
6. Проверить переливание в MAX/BALANCED, FAST без анимации, обе темы, а также исправленное положение кнопки «+».

**Подпись:** applicationId `com.mamoro.karman`, versionCode 4, versionName 1.0.3; используется прежний ключ из GitHub Secrets `ANDROID_*`. Схема базы не изменяется.

**Статус:** проект подготовлен и проверена компиляция чистой Kotlin-логики локально. Полный Android Gradle CI и работоспособность APK на устройстве требуют загрузки файлов и успешного GitHub Actions.
