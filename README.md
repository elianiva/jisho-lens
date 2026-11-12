<div align="center">
  <picture>
     <source width="400" media="(prefers-color-scheme: dark)" srcset="https://i.ibb.co/P1SXZxr/readme-logo-dark.png">
     <img width="400" alt="logo" src="https://i.ibb.co/3pjfzd1/readme-logo-light.png">
  </picture>
</div>

Jisho Lens is an Android app that scans Japanese words from a picture and looks them up in local dictionaries. Scan a page, tap a word, and read the entry. Nothing leaves the device.

## Status

The native app lives in `app/` and uses Kotlin, Jetpack Compose, and a Yomitan-compatible SQLite database. It replaces the 1.x app, whose source and old database are no longer part of this tree.

- Preview package: `com.elianiva.preview`
- Stable package: `com.elianiva`
- Minimum Android version: 8.0 (API 26)

The native app does not read the 1.x database. Import a Yomitan `.zip` dictionary instead.

## Features

- Scan from the camera or the photo picker.
- Read the screen you are looking at live: turn on Live reading once, tap the Screen lens tile in any app, and Japanese words highlight in place. Tap a word for its entry. The results sheet slides up and the page stays visible. A vertical swipe scrolls the app through its accessibility scroll action.
- Tap a recognized line, then a dictionary word, to open its entry.
- Segment with Kuromoji and IPADIC, so a conjugated phrase, a compound, a katakana loanword, or a keigo phrase resolves to the entry a reader wants. A tap offers the overlapping words at that character, not only the longest one.
- Search Japanese words, kanji, kana, and English glosses.
- Deinflect conjugated input, so 食べさせられた finds 食べる.
- Import several Yomitan dictionaries, enable them, reorder them, and remove them.
- Show tags, glosses, cross references, images, frequency data, and the pitch accent contour from the dictionary's `pitch` metadata.
- Rank scanned words and their entries by the imported frequency data, so the common word comes first.
- Mark words as known. The lens shows a filled check on an entry you already know.
- Send a mined word to AnkiDroid with its reading, gloss, the sentence it came from, and its pitch accent.
- Pick from a Yomitan dictionary catalogue grouped into terms, kanji, frequency, pitch, and names or specialised entries, with the size, licence, and terms of each.
- Download the starter set, or any entry whose publisher allows it, in one tap; the rest link to the publisher page for a manual import.
- Material 3 Expressive design with Google Sans Flex. Light, dark, and system themes, with dynamic color on Android 12 and later and a blue fallback.
- Share an image to the app, or select text in another app and use the Look up action.
- No storage permission. The app uses the system file picker and a foreground service for imports.
- The network is used only for a dictionary download that you start. Scanning, OCR, and lookup stay on the device.
- Live reading reads text and window bounds from the active app on the device. The OCR fallback keeps one bitmap in memory and recycles it. Nothing is written to disk or sent. The lens closes when you close it, when the screen turns off, or after ten idle minutes.

## Limitations

- The native app is a preview. It has no signed stable release yet.
- Segmentation loads the IPADIC model once per process (about 36 MB of heap) and adds about 13 MB to the APK.
- Vertical manga text and stylized fonts are harder for ML Kit. Crop and zoom the image when recognition is poor.
- Live reading uses Android's `AccessibilityService`. It only sees text an app exposes in its accessibility tree. Manga, images, and custom-drawn text use an in-memory screenshot and ML Kit on Android 11 and later, and a protected window refuses the screenshot. While the lens is open it takes touches. Swipe to scroll the app, or close the lens to interact with the page.
- Google Play requires a Permissions Declaration for the `AccessibilityService` API. The app declares `isAccessibilityTool=false` because it is a language-learning aid, not a screen reader for people with disabilities, and shows the disclosure before the system grant. A Play release needs that declaration.
- The Anki action uses AnkiDroid's `ACTION_SEND` contract, not its LGPL-3.0 library, so the note is opened in AnkiDroid for you to confirm instead of being written silently. Without AnkiDroid the note goes to the system share sheet.
- History, bookmarks, and a kanji screen are not part of this version.
- The app ships no dictionary and mirrors none. Every catalogue entry points at its publisher, records its licence and terms, and only offers a one-tap download where those terms allow the app to fetch the file. JPDB and BCCWJ must be downloaded and imported by hand.

### Live reading and Google Play

Live reading uses `AccessibilityService`, not MediaProjection. There is no screen-share consent dialog per lookup and no capture notification. The trade-off is a Play policy declaration. The service is not an accessibility tool for people with disabilities, so a Play release must complete the AccessibilityService declaration and the app shows an in-app disclosure before sending the user to Accessibility settings.

The disclosure is in `app/src/main/kotlin/com/elianiva/jisholens/ui/screens/LensSetupScreen.kt`. The service configuration is `app/src/main/res/xml/accessibility_service_config.xml`. The service reads the active window only while the lens is open, skips password fields, and keeps all text on the device.

## Tech stack

- Kotlin 2.4 and Jetpack Compose with Material 3 Expressive (`material3` 1.5.0-rc01)
- ViewModel and StateFlow for screen state
- Navigation Compose with type-safe routes
- DataStore for settings
- `androidx.sqlite` bundled SQLite behind the `:dictionary` interface
- FTS4 with `unicode61` for search
- AccessibilityService with a `TYPE_ACCESSIBILITY_OVERLAY` Compose window, and ML Kit Japanese text recognition for images
- Kuromoji 0.9.0 with the IPADIC model for segmentation (Apache-2.0)
- Gradle 9.9.1 and AGP 9.5.1

## Build

You need Android SDK platform 37 and JDK 17 or newer. Gradle provisions a Java 17
toolchain when your JDK differs, so a newer JDK also works.

```sh
./gradlew :app:assemblePreviewDebug
./gradlew :dictionary:test :app:testPreviewDebugUnitTest
```

The build keeps Gradle's configuration cache and build cache on, so later builds
reuse the task graph and task outputs. Tests always run, because Roborazzi writes
the screenshots outside the test task's outputs.

Install the preview on a connected device:

```sh
./gradlew :app:installPreviewDebug
```

Build a minified, unsigned release APK:

```sh
./gradlew :app:assemblePreviewRelease
```

The preview flavor uses `com.elianiva.preview`. The stable flavor uses `com.elianiva`.

### Previews and screenshot tests

Every screen has a stateless `*Content` composable and a `@Preview` in `app/src/debug/.../previews`. The screenshot test renders each preview on the JVM with Robolectric and Roborazzi, so adding a preview adds a screenshot. Record them with:

```sh
./gradlew :app:testPreviewDebugUnitTest -Proborazzi.test.record=true
```

The images land in `app/build/screenshots`: `previews/` for each preview and `app/` for the running app, including a tablet-width window with the navigation rail.

### CI and releases

Every push to `master` and every pull request runs the checks in `.github/workflows/ci.yml`. Each check is a job of its own, so lint, the app, dictionary, and CLI tests, the screenshot tests, the APK builds, and the workflow lint all run at the same time. Branch protection can require the single `All checks` job.

Pushing a tag such as `v1.2.3` runs `.github/workflows/release.yml`. It waits for the CI run on the tagged commit, builds the signed stable release with the tag as `versionName`, checks that the APK reports that version, and publishes `jisho-lens-1.2.3.apk`, `jisho-lens-1.2.3.aab`, and `SHA256SUMS.txt` as a GitHub release. The release notes are the `CHANGELOG.md` section for the version, or GitHub's generated notes when there is none. The version code is `major * 10000 + minor * 100 + patch`, so `v1.2.3` has code `10203`.

Signing reads four repository secrets, and the release stops before building if one is missing:

- `ANDROID_KEYSTORE_BASE64`: the release keystore, base64-encoded.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: the key alias inside the keystore.
- `ANDROID_KEY_PASSWORD`: the key password.

A local build without them produces an unsigned release, which is what `:app:assembleStableRelease` has always done.

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

The 1.x release and its `jmdict.db` file are not compatible with the native app.

## Development

The Android app is a standard Gradle module. Open the repository in Android Studio, or use the commands above. The dictionary tests run on the JVM and need no device.

The `:dictionary` module is plain Kotlin/JVM. It has no Android imports. The Android app supplies a `SqliteDatabase` adapter over `androidx.sqlite`.

## License

[MIT License](LICENSE). The deconjugation rules and their Kotlin port are licensed under the Apache License, Version 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and the full license text.
