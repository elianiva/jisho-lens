# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

## Unreleased

### Features

* add the native Kotlin and Jetpack Compose app under `app/`
* import Yomitan dictionaries, with tags, media, frequency data, and deinflected lookup
* add search, scan, dictionary management, sharing, and process-text lookup
* redesign the app on Material 3 Expressive: spring motion, shape morphing, emphasized type, flexible app bars, segmented lists and a loading indicator that waits 200 ms
* open on Look up, with Scan and Settings beside it; the navigation bar becomes a rail from 840 dp
* add recent searches, search as you type, and a wallpaper-colors switch
* use `com.elianiva` as the stable application ID and `com.elianiva.preview` for previews
* read the screen from a Quick Settings tile: one MediaProjection frame, ML Kit OCR, every word on the frozen page tappable, and `scanAt` offers the shorter candidates the greedy scan drops
* draw the pitch accent contour from a dictionary's `pitch` metadata, with the pattern named
* rank scan candidates and their entries by imported frequency data
* mark words as known, dim them in reader mode, and show how much of the page is known
* mine a word into an Anki note through AnkiDroid's `ACTION_SEND` contract, with the sentence, the pitch, and a share-sheet fallback
* download Jitendex or JMdict on first run, with size and licence shown, and import it in one tap

### Bug Fixes

* restore the original Jisho Lens launcher icon as an adaptive icon, with a monochrome layer for themed icons

### Notes

* Material 3 is pinned to 1.5.0-alpha18, the last 1.5 build that works with Compose 1.11, compileSdk 36 and AGP 8.13. Later builds need compileSdk 37 and AGP 9.1
* the screen lens keeps one frame in memory and never writes it to disk; closing the reader stops the capture session, and the window is `FLAG_SECURE`
* Jitendex and JMdict in Yomitan format ship no `term_meta` banks, so the pitch contour and the frequency ranking show up with dictionaries that provide them
* the Anki action uses AnkiDroid's documented `ACTION_SEND` contract, because its Instant-Add library is LGPL-3.0 and this app is MIT
* the 1.x app and its `jmdict.db` database are not compatible with the native app

## 1.1.0 (2023-10-08)


### Features

* add FTS index ([7054644](https://github.com/elianiva/jisho-lens/commit/7054644fe52895b5eec1e844b2a5474ebfe1e5d4))
* add initial version of db generator ([92c86a5](https://github.com/elianiva/jisho-lens/commit/92c86a55d9b4cbd5c70b2fcf23d8a0a62465bb1c))
* add reading order ([a9f3353](https://github.com/elianiva/jisho-lens/commit/a9f335397364b75bb11162f3a2622b9dc4db17b0))
* better sorting rule ([dcf6469](https://github.com/elianiva/jisho-lens/commit/dcf6469eba047037a2bacd45fa182952f4f08091))
* change blendmode for better accesibility ([a1939aa](https://github.com/elianiva/jisho-lens/commit/a1939aafadd016de3030ed97bc44e9d7cab37191))
* disallow fuzzy search with a single kana ([eb95071](https://github.com/elianiva/jisho-lens/commit/eb95071fb3df4ad52438385ecef6abc27a4c714a))
* don't expand pos entity ([e1d2d5a](https://github.com/elianiva/jisho-lens/commit/e1d2d5ab175c4525fe17e67eabc5e82ad82c8f97))
* initial release ([4126f60](https://github.com/elianiva/jisho-lens/commit/4126f608fc93c8de757c8df5d1dc33fd4cb8921f))
* lemmatise the words ([54522fc](https://github.com/elianiva/jisho-lens/commit/54522fc89bb15f5a752a60956c54a83e580dd195))
* use custom sqlite with icu support ([f9cc21b](https://github.com/elianiva/jisho-lens/commit/f9cc21bebf13175390ae88f9b0f6ebada1a8f95d))
* use fts for reading and kanji ([cfc78ea](https://github.com/elianiva/jisho-lens/commit/cfc78ea9573a28afe9cd1745eb0bd332023c9f03))


### Bug Fixes

* also apply fuzzy filter on lens page ([22b4177](https://github.com/elianiva/jisho-lens/commit/22b4177b56e7fc629beebbc79e28f555b690f16e))
* ask for permission on android 13 ([0c5edf8](https://github.com/elianiva/jisho-lens/commit/0c5edf802903f86e10727109275d9a3807a515d2))
* incorrect join causing incomplete results ([041b688](https://github.com/elianiva/jisho-lens/commit/041b6882b0fc6a17a7021d365c7ea6a9663bc386))
* incorrect sorting behaviour ([8e91e36](https://github.com/elianiva/jisho-lens/commit/8e91e365d1e78044e64b011fa4a317d934768019))
* incorrect warning condition ([efa646d](https://github.com/elianiva/jisho-lens/commit/efa646dac8d5f834e7a33e6355b30908d90e776f))
* remove fetch duration after resetting ([9e64e23](https://github.com/elianiva/jisho-lens/commit/9e64e23d6ae72648a72656683d478f22c2c6a41e))
* set max chars from entities to 0 ([0a6d5c6](https://github.com/elianiva/jisho-lens/commit/0a6d5c69599cdd0569edccc774799960c6ab573f))
* wrong regex ([8b87a85](https://github.com/elianiva/jisho-lens/commit/8b87a85a7455ee7f5fbf2e8d07c86d673f02f77b))
