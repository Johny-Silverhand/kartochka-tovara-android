# Карточка товара — Android

Локальное Android-приложение: карточки товара (название, описание, до **4 фото** с листанием).  
Данные в **SQLite** на устройстве + файлы фото в `files/photos`. Без облака.

**Разработано в Victimok Labs.**

## Возможности

- Список / создать / сохранить / удалить
- До 4 фото, стрелки + свайп
- Автомасштаб под телефон и планшет (`viewport`, `clamp`, адаптивная сетка)
- Производительность: hardware WebView, `offscreenPreRaster`, R8 minify в release, индекс по `updated_at`

## Сборка APK

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/android-sdk
cd kartochka-tovara-android
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk
# или debug:
./gradlew :app:assembleDebug
```

Готовый файл для раздачи: `dist/KartochkaTovara.apk`.

## БД

SQLite: `kartochka.db` во внутреннем хранилище приложения.  
Фото: `filesDir/photos/`.

applicationId: `labs.victimok.kartochka` · version **1.0.0**
