import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/components/warning_banner.dart';
import 'package:jisho_lens/providers/db_status_provider.dart';
import 'package:jisho_lens/providers/page_navigation_provider.dart';

class CustomNavigationBar extends ConsumerWidget {
  const CustomNavigationBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final showDbBanner = ref.watch(dbStatus);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // show a warning when there's no database file
        showDbBanner == DbStatus.empty ? const WarningBanner() : Container(),
        NavigationBar(
          selectedIndex: ref.watch(activePage),
          onDestinationSelected: (index) {
            ref.read(pageController).animateToPage(
                  index,
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.easeOut,
                );
          },
          destinations: const [
            NavigationDestination(
              selectedIcon: Icon(Icons.home_sharp),
              icon: Icon(Icons.home_outlined),
              label: "Home",
            ),
            NavigationDestination(
              selectedIcon: Icon(Icons.search_rounded),
              icon: Icon(Icons.search_rounded),
              label: "Search",
            ),
            NavigationDestination(
              selectedIcon: Icon(Icons.settings),
              icon: Icon(Icons.settings_outlined),
              label: "Settings",
            ),
            NavigationDestination(
              selectedIcon: Icon(Icons.info_rounded),
              icon: Icon(Icons.info_outlined),
              label: "About",
            ),
          ],
        ),
      ],
    );
  }
}
