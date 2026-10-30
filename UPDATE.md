# Autoaktualizacja

Repo jest **publiczne** — apka czyta `update.json` bez tokena:

`https://raw.githubusercontent.com/NiewidzialnySI/telefon/main/update.json`

## Jak wypuścić nową wersję

1. W `app/build.gradle.kts` podnieś `versionCode` i `versionName`.
2. Zbuduj APK: `./gradlew :app:assembleRelease` (lub debug).
3. Utwórz [GitHub Release](https://github.com/NiewidzialnySI/telefon/releases) z tagiem np. `v1.1` i dołącz plik `.apk`.
4. Zaktualizuj `update.json` w `main`:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "apkUrl": "https://github.com/NiewidzialnySI/telefon/releases/download/v1.1/app-release.apk",
  "changelog": "Co nowego…"
}
```

5. Commit + push `update.json`.

Telefon przy następnym sprawdzeniu zobaczy wyższy `versionCode`. W ustawieniach: **Pobierz APK** → **Zainstaluj** → chooser (systemowy instalator, Shizuku itd.).

## Uwaga

Instalacja idzie przez `Intent` + FileProvider — wybierasz aplikację instalującą. Cicha instalacja bez kliknięcia wymaga Device Owner / root / Shizuku.

