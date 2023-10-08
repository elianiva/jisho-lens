import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';
import 'package:jisho_lens/providers/db_status_provider.dart';
import 'package:jisho_lens/providers/search_settings_provider.dart';
import 'package:jisho_lens/providers/theme_provider.dart';
import 'package:jisho_lens/repository/jmdict_repository.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:settings_ui/settings_ui.dart';

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({super.key});

  @override
  SettingsPageState createState() => SettingsPageState();
}

class SettingsPageState extends ConsumerState<SettingsPage> {
  final _jmdictRepository = JMDictRepository();

  @override
  Widget build(BuildContext context) {
    final theme = ref.watch(preferredThemeNotifierProvider);

    return SettingsList(
      darkTheme: SettingsThemeData(
        settingsListBackground: context.theme.colorScheme.background,
        titleTextColor: context.theme.colorScheme.primary,
      ),
      lightTheme: SettingsThemeData(
        settingsListBackground: context.theme.colorScheme.background,
        titleTextColor: context.theme.colorScheme.primary,
      ),
      sections: [
        SettingsSection(
          title: const Text("LOOK AND FEEL"),
          tiles: <SettingsTile>[
            SettingsTile(
              onPressed: (_) => _selectTheme(),
              leading: Icon(
                Icons.format_paint_rounded,
                color: context.theme.textTheme.titleMedium?.color,
              ),
              title: Text(
                "Theme",
                style: context.theme.textTheme.bodyMedium,
              ),
              value: Text(
                theme.describe,
                style: context.theme.textTheme.bodySmall,
              ),
            ),
          ],
        ),
        SettingsSection(
          title: const Text("DATA SOURCES"),
          tiles: <SettingsTile>[
            SettingsTile(
              onPressed: (_) => _importDatabase(),
              leading: Icon(
                Icons.my_library_books,
                color: context.theme.textTheme.titleMedium?.color,
              ),
              title: Text(
                "Import Dictionary",
                style: context.theme.textTheme.bodyMedium,
              ),
              value: Text(
                "Import a dictionary from your local storage",
                style: context.theme.textTheme.bodySmall,
              ),
            ),
            SettingsTile(
              onPressed: (_) => _removeDatabase(),
              leading: Icon(
                Icons.highlight_remove_sharp,
                color: context.theme.textTheme.titleMedium?.color,
              ),
              title: Text(
                "Remove Dictionary",
                style: context.theme.textTheme.bodyMedium,
              ),
              value: Text(
                "Remove a dictionary from the app database",
                style: context.theme.textTheme.bodySmall,
              ),
            ),
          ],
        ),
        SettingsSection(title: const Text("RESULT ACCURACY"), tiles: <SettingsTile>[
          SettingsTile.switchTile(
            activeSwitchColor: context.theme.colorScheme.primary,
            initialValue: ref.watch(searchSettingsNotifierProvider).useFuzzySearch,
            onToggle: (bool value) {
              ref.read(searchSettingsNotifierProvider.notifier).setFuzzySearchTo(value);
            },
            title: Text(
              "Full Text Search for kana",
              style: context.theme.textTheme.bodyMedium,
            ),
            description: Text(
              "This is useful when you want to search for a word but you only know certain parts of the kana."
              "Use it only when you need it because it will slow down the searching process.",
              style: context.theme.textTheme.bodySmall,
            ),
          )
        ])
      ],
    );
  }

  void _selectTheme() {
    final themeNotifier = ref.watch(preferredThemeNotifierProvider.notifier);
    final theme = ref.read(preferredThemeNotifierProvider);

    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("Choose a theme"),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              RadioListTile(
                title: const Text("Follows System"),
                value: PreferredTheme.system,
                activeColor: context.theme.colorScheme.primary,
                groupValue: theme,
                onChanged: (theme) {
                  themeNotifier.setPreferredTheme(PreferredTheme.system);
                  Navigator.of(context).pop();
                },
              ),
              RadioListTile(
                title: const Text("Dark"),
                value: PreferredTheme.dark,
                activeColor: context.theme.colorScheme.primary,
                groupValue: theme,
                onChanged: (theme) {
                  themeNotifier.setPreferredTheme(PreferredTheme.dark);
                  Navigator.of(context).pop();
                },
              ),
              RadioListTile(
                title: const Text("Light"),
                value: PreferredTheme.light,
                activeColor: context.theme.colorScheme.primary,
                groupValue: theme,
                onChanged: (theme) {
                  themeNotifier.setPreferredTheme(PreferredTheme.light);
                  Navigator.of(context).pop();
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _importDatabase() async {
    final status = await Permission.manageExternalStorage.request();
    if (status.isDenied && context.mounted) {
      await showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: const Text("Permission denied"),
              content: const Text("Please allow the app to access your storage."),
              actions: [
                TextButton(
                  child: const Text("OK"),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            );
          });
      return;
    }

    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.any,
      dialogTitle: "Pick a dictionary database",
    );

    if (result == null) {
      if (context.mounted) {
        showDialog(
            context: context,
            builder: (context) {
              return AlertDialog(
                title: const Text("No file selected"),
                content: const Text("Please select a dictionary database."),
                actions: [
                  TextButton(
                    child: const Text("OK"),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              );
            });
      }
      return;
    }

    if (result.files.first.extension != "db" && mounted) {
      await showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text("Invalid file"),
            content: const Text("Please select a dictionary database with the correct extension (.db)"),
            actions: [
              TextButton(
                child: const Text("OK"),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          );
        },
      );
      return;
    }

    if (mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) {
          return const AlertDialog(
            title: Text("Importing..."),
            content: LinearProgressIndicator(),
          );
        },
      );
    }

    try {
      await _jmdictRepository.importDbFile(result.files.first.path);
      // remove the warning banner on success
      ref.read(dbStatus.notifier).state = DbStatus.ready;

      if (!mounted) return;
      // remove the loading dialog
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
          "The database file has been imported successfully.",
          style: context.theme.textTheme.titleMedium,
        ),
        behavior: SnackBarBehavior.floating,
        backgroundColor: context.theme.dialogBackgroundColor,
      ));
    } catch (ex) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: const Text(
          "Failed to import the database.",
          style: TextStyle(color: Colors.white),
        ),
        behavior: SnackBarBehavior.floating,
        backgroundColor: context.theme.colorScheme.error,
      ));
    }
  }

  Future<void> _removeDatabase() {
    return showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("Remove the dictionary database?"),
          content: const Text(
            "This will remove the dictionary database from the app.",
          ),
          actions: [
            TextButton(
              child: const Text("Cancel"),
              onPressed: () => Navigator.of(context).pop(),
            ),
            TextButton(
              child: const Text("Remove"),
              onPressed: () async {
                try {
                  await _jmdictRepository.removeDbFile();
                  // show the warning banner when it's been deleted
                  ref.read(dbStatus.notifier).state = DbStatus.empty;

                  if (!mounted) return;
                  Navigator.of(context).pop();
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text(
                      "Database has been removed successfully.",
                      style: context.theme.textTheme.titleMedium,
                    ),
                    behavior: SnackBarBehavior.floating,
                    backgroundColor: context.theme.dialogBackgroundColor,
                  ));
                } catch (ex) {
                  String reason = "";
                  if (ex is FileSystemException) {
                    reason = ex.message;
                  }

                  if (!mounted) return;
                  Navigator.of(context).pop();
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text(
                      "Failed to remove the database. $reason",
                      style: const TextStyle(color: Colors.white),
                    ),
                    behavior: SnackBarBehavior.floating,
                    backgroundColor: context.theme.colorScheme.error,
                  ));
                }
              },
            ),
          ],
        );
      },
    );
  }
}
