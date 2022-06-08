import 'package:flutter/material.dart';

class SearchWarningDialog extends StatelessWidget {
  const SearchWarningDialog({super.key});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text("Error"),
      content: const Text(
        "Searching a single kana with fuzzy search enabled isn't allowed because this might cause the app to crash.\n\n"
        "Please increase your search keyword or disabled fuzzy search.",
      ),
      actions: [
        TextButton(
          child: const Text("OK"),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
    );
  }
}
