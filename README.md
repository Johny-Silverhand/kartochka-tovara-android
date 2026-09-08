# Фото карта — Android

Локальное приложение: карты с названием, описанием и до **4 фото**.  
Источники фото: **камера**, **галерея**, **файловый менеджер**. Данные в **SQLite** на устройстве. **Полностью офлайн** — интернет не нужен.

**Разработано в Victimok Labs.**

## Сборка

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/android-sdk
./gradlew :app:assembleRelease
```

APK: `dist/KartochkaTovara.apk` (applicationId `labs.victimok.kartochka`, v1.0.1).
