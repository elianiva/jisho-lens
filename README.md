**WARNING: NOT READY YET. IF YOU WANT TO TRY THE APP YOU NEED TO BUILD IT YOURSELF USING THE INSTRUCTIONS.**

<div align="center">
  <picture>
     <source media="(prefers-color-scheme: dark)" srcset="https://i.ibb.co/99dBTBk/readme-logo-dark.png">
     <img alt="logo" src="https://i.ibb.co/Tv3WPxN/readme-logo-light.png">
  </picture>
</div>

Jisho Lens is an app that allows you to scan Japanese words from a picture and search for its meaning from a dictionary. In a nutshell, think of it as a Google Lens but instead of searching from Google, it searches from a local dictionary.

## Motivation

> It's a bit long, bare with me. Or just ignore it if you're not interested lol

This app was made mainly because I got frustrated whenever I see a word on my phone and I don't know what it means. I mean, just Google it, right? No. You see, Japanese has multiple writing system, one of them is kanji. In order for me to be able to Google it, I need to know how to write it. The thing with Kanji, unlike latin alphabet, is that you can't just see a letter and just be able to write it down using a keyboard.

Now, imagine this scenario. You're a non native English speaker, you came a cross the word _"depression"_, you don't know what it means, easy, just Google it up innit, just write _"depression"_ on Google and you're done. Now how about Japanese? Imagine you're not a native Japanese speaker, you came across a word "鬱" (which, in small size almost looks like a block of square, at least to me). How do you supposed to know if it's read as うつ? You don't. That's why I made this app to try to solve this problem.

I use this awesome browser extension called [Yomichan](https://github.com/FooSoft/yomichan) which basically works like a magic wand. You just hover your cursor over an unknown word and it will pull up a dictionary entry. Unfortunately it only works on browser. This is also another reason why I made this app, I want something like this to be available on mobile.

I found this cool project the other day called [Jidoujisho](https://github.com/lrorpilla/jidoujisho) which is an app that enables immersion learning. It's a really cool project and I like it. Why don't I just use it? Well, it's more fun to make it yourself ¯\\\_(ツ)\_/¯. Jisho Lens is basically a fraction of that project and I prefer having simpler app that does one thing that suits my need. I recommend you to at least check it out, it may work better for you because it has tons of other features ;)

## Features

Here are some features of this app:

- [x] Dark mode / Light mode
- [x] Scans a picture for words
- [x] Search for words from a dictionary using a keyword with Full Text Search support
- [x] Scans a picture by sharing it to this app ([see here for details](#usage))
- [ ] Add dictionary entry to Anki (planned)
- [ ] Scan words from current screen using quick settings tile (planned)
- [x] JMdict dataset
- [ ] KANJIDIC2 dataset (planned)
- [ ] Tatoeba dataset (planned)
- [ ] Online version? You will no longer need to download the entire dataset but requires internet connection and I have to host the server :p

## Limitations

There are some limitations of this app:

- It only works on Android (specifically Android ) since I don't have Apple devices to test it on.
- You need to take the image manually and remove it afterwards to scan the current screen for now, but once I implemented the quick settings tile it should be possible to let Jisho Lens handle the image deletion when you're done automatically.
- The app size might grow a bit in the future when more datasets are added. For now it's roughly ~26MB for the app size and ~100MB for the JMdict database. I'm planning to find a way to modularise the database so that e.g if you just want JMdict, you can just download it by itself without having to download the rest of the data.
- Query speed might be a bit slow since I'm not an SQL guru that can optimise the query/database to be faster than light. I'm hoping the situation will get better as the app gets more developed.
- The image might get false positive of recognised texts (icons got recognised as texts) because Google ML Kit is not perfect, but it should be good enough most of the time.

## Tech Stack

This project is split into two parts: the android app and a small tool to generate the database from a dictionary.

Here are some of the framework/library used for the android app.

- [Flutter](https://flutter.dev) - The main framework
- [Riverpod](https://riverpod.dev) - State management
- [Google ML Kit](https://developers.google.com/ml-kit) - Used as the text recognition engine
- [Hive](https://docs.hivedb.dev/#/) - A persistent key-value store
- [Sqflite](https://pub.dev/packages/sqflite) - SQLite database driver for Flutter

Please refer to [DbGenerator/README.md](https://github.com/elianiva/jisho-lens/blob/master/DbGenerator/README.md) for more details.

## Usage

You must first import a dictionary to use this app. I made the dictionary separated so that I can update it more frequently without having to reinstall the app everytime. You can grab one from [the release page](#TODO). If you don't import the dictionary, you won't be able to use any of the features and will get a warning like this:

<img src="https://i.ibb.co/jT0fYnK/warning.png" width="600" />

After you import the dictionary, everything should be self explanatory, but in case it wasn't clear enough, here are some videos demonstrating how to use this app (It's hosted on streamable because I don't want to attach it in the readme, it's definitely not me being too lazy to attach them here, no).

- [Searching for words using keyword](https://streamable.com/k0q2yr)
- [Scan a word](https://streamable.com/y32jxq)
- [Quick share shortcut](https://streamable.com/2c1vf9) - This is how I use it most of the time

## Screenshots

<details>
<summary>Home Page</summary>

| Dark mode                                                                         | Light mode                                                                         |
| --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| <kbd> <img src="https://i.ibb.co/M7RY15P/homepage-dark.png" width="240" /> </kbd> | <kbd> <img src="https://i.ibb.co/FVQqX2y/homepage-light.png" width="240" /> </kbd> |

</details>

<details>
<summary>Search Page</summary>

| Dark mode                                                                       | Light mode                                                                       |
| ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| <kbd> <img src="https://i.ibb.co/tJswpBK/search-dark.png" width="240" /> </kbd> | <kbd> <img src="https://i.ibb.co/0VNXv38/search-light.png" width="240" /> </kbd> |

</details>

<details>
<summary>Settings page</summary>

| Dark mode                                                                         | Light mode                                                                         |
| --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| <kbd> <img src="https://i.ibb.co/hXv8P9P/settings-dark.png" width="240" /> </kbd> | <kbd> <img src="https://i.ibb.co/j5rD9rk/settings-light.png" width="240" /> </kbd> |

</details>

<details>
<summary>Scan page</summary>

| Dark mode                                                                       | Light mode                                                                       |
| ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| <kbd> <img src="https://i.ibb.co/9G8BBVp/scan-dark.png" width="240" /> </kbd>   | <kbd> <img src="https://i.ibb.co/bgg5yD5/scan-light.png" width="240" /> </kbd>   |
| <kbd> <img src="https://i.ibb.co/gw38JRk/scan-2-dark.png" width="240" /> </kbd> | <kbd> <img src="https://i.ibb.co/ZB80nFM/scan-2-light.png" width="240" /> </kbd> |

</details>

## Development

It's just a standard Flutter project, you can develop it just like any other Flutter app using [Android Studio](https://developer.android.com/studio) or [Visual Studio Code](https://code.visualstudio.com/). If this is the first time you're using Flutter, you can follow [the official instructions](https://docs.flutter.dev/get-started/install) to set it up.
Although, if you want to develop any features related to the dictionary, you need to generate the database first by following the instruction in [DbGenerator/README.md](./DbGenerator/README.md).

## License

[MIT License](LICENSE)
