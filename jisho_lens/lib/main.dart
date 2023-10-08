import 'dart:async';
import 'dart:io';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:jisho_lens/components/navigation_bar.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';
import 'package:jisho_lens/screens/about_page.dart';
import 'package:jisho_lens/screens/home_page.dart';
import 'package:jisho_lens/screens/lens_page.dart';
import 'package:jisho_lens/screens/search_page.dart';
import 'package:jisho_lens/screens/settings_page.dart';
import 'package:jisho_lens/providers/db_status_provider.dart';
import 'package:jisho_lens/providers/ocr_provider.dart';
import 'package:jisho_lens/providers/page_navigation_provider.dart';
import 'package:jisho_lens/providers/theme_provider.dart';
import 'package:jisho_lens/services/sqlite_client.dart';
import 'package:share_handler/share_handler.dart';

Future<void> main() async {
  GoogleFonts.config.allowRuntimeFetching = false;

  // needed for sqlite and maybe other native stuff that I'm not aware of
  WidgetsFlutterBinding.ensureInitialized();

  // open the hive box so we don't have to deal with async stuff later
  await Hive.initFlutter();
  await Hive.openBox<String>(kThemeBox);
  await Hive.openBox<dynamic>(kSettingsBox);

  runApp(const ProviderScope(child: MyApp()));
}

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ThemeMode themeMode = ThemeMode.system;
    PreferredTheme currentTheme = ref.watch(preferredThemeNotifierProvider);

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

    return DynamicColorBuilder(
      builder: (lightColorscheme, darkColorscheme) {
        return MaterialApp(
          navigatorKey: navigatorKey,
          title: 'Jisho Lens',
          themeMode: themeMode,
          theme: ThemeData(
            colorScheme: lightColorscheme ?? ColorScheme.fromSwatch(primarySwatch: Colors.blue),
            useMaterial3: true,
          ),
          darkTheme: ThemeData(
            colorScheme:
                darkColorscheme ?? ColorScheme.fromSwatch(primarySwatch: Colors.blue, brightness: Brightness.dark),
            useMaterial3: true,
          ),
          home: const RootPage(),
        );
      },
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
  late final _pageController = PageController();
  SharedMedia? media;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final dbPath = await SqliteClient.instance.path;
      final dbFileExists = await File(dbPath).exists();
      ref.read(dbStatus.notifier).state = DbStatus.fromBoolean(dbFileExists);
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
      ref.read(selectedImagePath.notifier).state = media?.attachments?.first?.path;
      navigatorKey.currentState?.push(MaterialPageRoute(builder: (_) => const LensPage()));
    }

    handler.sharedMediaStream.listen((SharedMedia media) {
      if (!mounted) return;
      ref.read(selectedImagePath.notifier).state = media.attachments?.first?.path;
      navigatorKey.currentState?.push(MaterialPageRoute(builder: (_) => const LensPage()));
    });
  }

  @override
  Widget build(BuildContext context) {
    final currentTheme = ref.watch(preferredThemeNotifierProvider);
    final systemTheme = MediaQuery.of(context).platformBrightness;
    String logoPath = "";

    switch (currentTheme) {
      case PreferredTheme.dark:
        logoPath = "assets/logo_light.png";
        break;
      case PreferredTheme.light:
        logoPath = "assets/logo_dark.png";
        break;
      case PreferredTheme.system:
      default:
        logoPath = systemTheme == Brightness.dark ? "assets/logo_light.png" : "assets/logo_dark.png";
    }

    return Scaffold(
      backgroundColor: context.theme.colorScheme.background,
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
            ref.read(activePage.notifier).state = index;
          },
          children: widget.pages,
        ),
      ),
      bottomNavigationBar: CustomNavigationBar(pageController: _pageController),
    );
  }
}
