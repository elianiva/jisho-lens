This folder contains a C# project to parse JMdict and Furigana entries and insert them to an sqlite3 database used by jisho_lens.

### Development

There are some prerequisites to run this project:

- [Dotnet SDK](https://dotnet.microsoft.com/download/dotnet-sdk)
- The [JMdict](http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz) and [Furigana](https://github.com/Doublevil/JmdictFurigana/releases) file and put them inside the `./Data` directory.
- A custom compiled sqlite3 with ICU statically linked. You can download it from [here](https://sqlite.org/download.html) and extract it to a directory called `./Data/sqlite`.

To compile the sqlite3 with ICU support, make sure you have the following packages installed:

- `automake`
- `libtool`
- `autoconf`
- `libicu`

After that, you can run the following command to compile sqlite3 with ICU support:

```sh
dotnet msbuild -target:BuildSqlite
```

Under the hood it executes the following command:

```sh
CFLAGS="-O3 -DSQLITE_ENABLE_ICU" CPPFLAGS=`icu-config --cppflags` LDFLAGS="-Wl,-Bstatic "`icu-config --ldflags`" -Wl,-Bdynamic" ./configure
make clean
make
```

If you got an error that looks like this:

```
Makefile.am:3: error: Libtool library used but 'LIBTOOL' is undefined
Makefile.am:3:   The usual way to define 'LIBTOOL' is to add 'LT_INIT'
Makefile.am:3:   to 'configure.ac' and run 'aclocal' and 'autoconf' again.
Makefile.am:3:   If 'LT_INIT' is in 'configure.ac', make sure
Makefile.am:3:   its definition is in aclocal's search path.
```

You can fix it by going into the sqlite directory and then run the following commands:

```sh
aclocal
autoreconf -fvi
```

That should fix the issue.

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
