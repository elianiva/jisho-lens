import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:jisho_lens/components/navigation_bar.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:jisho_lens/models/lens_history.dart';
import 'package:jisho_lens/screens/about.dart';
import 'package:jisho_lens/screens/home.dart';
import 'package:jisho_lens/screens/lens.dart';
import 'package:jisho_lens/screens/search.dart';
import 'package:jisho_lens/screens/settings.dart';
import 'package:jisho_lens/providers/db_status_provider.dart';
import 'package:jisho_lens/providers/ocr_provider.dart';
import 'package:jisho_lens/providers/page_navigation_provider.dart';
import 'package:jisho_lens/providers/theme_provider.dart';
import 'package:jisho_lens/services/sqlite_client.dart';
import 'package:jisho_lens/themes/dark_theme.dart';
import 'package:jisho_lens/themes/light_theme.dart';
import 'package:share_handler/share_handler.dart';

Future<void> main() async {
  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('google_fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['google_fonts'], license);
  });

  // needed for sqlite and maybe other native stuff that I'm not aware of
  WidgetsFlutterBinding.ensureInitialized();

  // open the hive box so we don't have to deal with async stuff later
  await Hive.initFlutter();

  Hive.registerAdapter(HistoryAdapter());

  await Hive.openBox<String>(kThemeBox);
  await Hive.openBox(kSettingsBox);
  await Hive.openBox(kLensHistoryBox);

  runApp(const ProviderScope(child: MyApp()));
}

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ThemeMode themeMode = ThemeMode.system;
    PreferredTheme currentTheme = ref.watch(PreferredThemeNotifier.provider);

    switch (currentTheme) {
      case PreferredTheme.light:
        themeMode = ThemeMode.light;
        break;
      case PreferredTheme.dark:
        themeMode = ThemeMode.dark;
        break;
      case PreferredTheme.system:
      default:
        themeMode = ThemeMode.system;
    }

    return MaterialApp(
      navigatorKey: navigatorKey,
      title: 'Jisho Lens',
      themeMode: themeMode,
      darkTheme: darkTheme,
      theme: lightTheme,
      home: const RootPage(),
    );
  }
}

class RootPage extends ConsumerStatefulWidget {
  final List<Widget> pages = const [
    HomePage(),
    SearchPage(),
    SettingsPage(),
    AboutPage(),
  ];

  const RootPage({super.key});

  @override
  ConsumerState<RootPage> createState() => _RootPageState();
}

class _RootPageState extends ConsumerState<RootPage> {
  late final _pageController = ref.read(pageController);
  SharedMedia? media;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final dbPath = await SqliteClient.instance.path;
      final dbFileExists = await File(dbPath).exists();
      if (!dbFileExists) {
        // show the banner when the database doesn't exist
        ref.read(dbStatus.notifier).state = DbStatus.empty;
      }
    });

    initPlatformState();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> initPlatformState() async {
    final handler = ShareHandlerPlatform.instance;
    media = await handler.getInitialSharedMedia();

    if (media != null) {
      if (!mounted) return;
      ref.read(selectedImagePath.state).state = media?.attachments?.first?.path;
      navigatorKey.currentState
          ?.push(MaterialPageRoute(builder: (_) => const LensPage()));
    }

    handler.sharedMediaStream.listen((SharedMedia media) {
      if (!mounted) return;
      ref.read(selectedImagePath.state).state = media.attachments?.first?.path;
      navigatorKey.currentState
          ?.push(MaterialPageRoute(builder: (_) => const LensPage()));
    });
  }

  @override
  Widget build(BuildContext context) {
    final currentTheme = ref.watch(PreferredThemeNotifier.provider);
    final systemTheme = MediaQuery.of(context).platformBrightness;
    String logoPath = "";

    switch (currentTheme) {
      case PreferredTheme.dark:
        logoPath = "logo_light.png";
        break;
      case PreferredTheme.light:
        logoPath = "logo_dark.png";
        break;
      case PreferredTheme.system:
      default:
        logoPath =
            systemTheme == Brightness.dark ? "logo_light.png" : "logo_dark.png";
    }

    return Scaffold(
      backgroundColor: Theme.of(context).backgroundColor,
      appBar: AppBar(
        title: Image(
          image: AssetImage(logoPath),
          height: 28,
        ),
      ),
      body: SizedBox.expand(
        child: PageView(
          controller: _pageController,
          onPageChanged: (index) {
            ref.read(activePage.state).state = index;
          },
          children: widget.pages,
        ),
      ),
      bottomNavigationBar: const CustomNavigationBar(),
    );
  }
}
