<div align="center">
  <picture>
     <source width="400" media="(prefers-color-scheme: dark)" srcset="https://i.ibb.co/P1SXZxr/readme-logo-dark.png">
     <img width="400" alt="logo" src="https://i.ibb.co/3pjfzd1/readme-logo-light.png">
  </picture>
</div>

Jisho Lens is an Android app that scans Japanese words from a picture and looks them up in local dictionaries. Scan a page, tap a word, and read the entry. Nothing leaves the device.

## Status

The native app lives in `app/` and uses Kotlin, Jetpack Compose, and a Yomitan-compatible SQLite database. The Flutter app in `jisho_lens/` is the released 1.x version. Keep it installed while the native app is verified, or uninstall it when you switch.

- Preview package: `com.elianiva.preview`
- Stable package: `com.elianiva`
- Minimum Android version: 8.0 (API 26)

The native app does not read the old Flutter database. Import a Yomitan `.zip` dictionary instead.

## Features

- Scan from the camera or the photo picker.
- Tap a recognized line, then a dictionary word, to open its entry.
- Search Japanese words, kanji, kana, and English glosses.
- Deinflect conjugated input, so 食べさせられた finds 食べる.
- Import several Yomitan dictionaries, enable them, reorder them, and remove them.
- Show tags, glosses, cross references, images, and frequency data from the dictionary.
- Light, dark, and system themes, with dynamic color on Android 12 and later.
- Share an image to the app, or select text in another app and use the Look up action.
- No storage permission. The app uses the system file picker and a foreground service for imports.

## Limitations

- The native app is a preview. It has no signed stable release yet.
- Vertical manga text and stylized fonts are harder for ML Kit. Crop and zoom the image when recognition is poor.
- Screen scanning from a Quick Settings tile and the accessibility overlay are not implemented.
- Anki export, history, bookmarks, and a kanji screen are not part of this version.
- The app ships no dictionary. Jitendex and JMdict are available from their publishers and are licensed under CC BY-SA 4.0.

## Tech stack

- Kotlin 2.4 and Jetpack Compose with Material 3
- ViewModel and StateFlow for screen state
- Navigation Compose with type-safe routes
- DataStore for settings
- `androidx.sqlite` bundled SQLite behind the `:dictionary` interface
- FTS4 with `unicode61` for search
- ML Kit Japanese text recognition
- Gradle 8.14.4 and AGP 8.13.2

## Build

You need JDK 17 and Android SDK platform 36.

```sh
./gradlew :app:assemblePreviewDebug
./gradlew :dictionary:test :app:testPreviewDebugUnitTest
```

Install the preview on a connected device:

```sh
./gradlew :app:installPreviewDebug
```

Build a minified, unsigned release APK:

```sh
./gradlew :app:assemblePreviewRelease
```

The preview flavor uses `com.elianiva.preview`. The stable flavor uses `com.elianiva`.

### Screenshot tests

The Compose screenshot tests render on the JVM with Robolectric and Roborazzi. Record them with:

```sh
./gradlew :app:testPreviewDebugUnitTest \
  -Proborazzi.test.record=true \
  --tests com.elianiva.jisholens.ScreenshotTest
```

The images land in `app/build/screenshots`.

## Dictionary tooling

`cli` imports Yomitan dictionary archives into one SQLite file. It accepts every bank a Yomitan dictionary can hold: terms, kanji, term metadata (frequency and pitch), kanji metadata, tags, media, and `styles.css`.

Build it with `./gradlew :cli:installDist`. The launcher lands at `cli/build/install/jisho-dict/bin/jisho-dict`.

```sh
jisho-dict import JMdict_english.zip --db jisho_lens.db
jisho-dict list --db jisho_lens.db
jisho-dict lookup 食べる --db jisho_lens.db
jisho-dict lookup 食べさせられた --db jisho_lens.db
jisho-dict search eat --db jisho_lens.db
jisho-dict kanji 食 --db jisho_lens.db
jisho-dict remove JMdict --db jisho_lens.db
```

`import` accepts several archives at once and an `--alias` name. `--json` prints machine-readable output and `--vacuum` compacts the file after importing. Importing a dictionary whose title already exists replaces it and keeps its priority, alias, and enabled state.

The database mirrors Yomitan's object stores: `dictionaries`, `terms`, `term_meta`, `kanji`, `kanji_meta`, `tags`, and `media`. Glossaries keep their original JSON, structured content included, in 4 MiB zlib-compressed blocks. Each term stores the block, offset, and length. FTS4 indexes expressions, readings, glosses, and kanji meanings. Japanese text is indexed one character per token, so partial input matches without an ICU tokenizer.

## Usage

1. Install the preview or stable build.
2. Download a Yomitan dictionary archive such as Jitendex or JMdict.
3. Open Settings, then Dictionaries, then the add button.
4. Pick the `.zip` file. The import runs in a foreground service and can be cancelled.
5. Scan a picture or search for a word.

The old Flutter release and its `jmdict.db` file are not compatible with the native app.

## Development

The Android app is a standard Gradle module. Open the repository in Android Studio, or use the commands above. The dictionary tests run on the JVM and need no device.

The `:dictionary` module is plain Kotlin/JVM. It has no Android imports. The Android app supplies a `SqliteDatabase` adapter over `androidx.sqlite`.

## License

[MIT License](LICENSE). The deconjugation rules and their Kotlin port are licensed under the Apache License, Version 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and the full license text.
