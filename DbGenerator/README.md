This folder contains a C# project to parse JMdict and Furigana entries and insert them to an sqlite3 database used by jisho_lens.

### Development

There are some prerequisites to run this project:

- [Dotnet SDK](https://dotnet.microsoft.com/download/dotnet-sdk)
- The [JMdict](http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz) and [Furigana](https://github.com/Doublevil/JmdictFurigana/releases/download/2.3.0%2B2023-03-25/JmdictFurigana.json) file and put them inside the `./Data` directory.
- A custom compiled sqlite3 with ICU statically linked. You can download it from [here](https://sqlite.org/download.html) and extract it to a directory called `./Data/sqlite`.

To compile the sqlite3 with ICU support, you can use the following command:

```sh
dotnet msbuild -target:BuildSqlite
```

<!--
```sh
CFLAGS="-O3 -DSQLITE_ENABLE_ICU" CPPFLAGS=`icu-config --cppflags` LDFLAGS="-Wl,-Bstatic "`icu-config --ldflags`" -Wl,-Bdynamic" ./configure
``` -->

### Usage

Before running the project, you need to make sure these files exist:

- `./Data/sqlite/sqlite3` - The custom compiled sqlite3
- `./Data/JMdict_e.gz` - The JMdict entries
- `./Data/JMdictFurigana.json` - The Furigana needed for the JMdict entries

After you've done that, you can run the project with the following command:

```sh
dotnet run
```

The output should be inside the `./Data` directory called `jmdict.db`.
