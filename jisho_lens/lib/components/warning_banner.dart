import 'package:flutter/material.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';

class WarningBanner extends StatelessWidget {
  const WarningBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: context.theme.colorScheme.error,
      child: const Padding(
        padding: EdgeInsets.all(8.0),
        child: Text(
          "You don't have any dictionary. Please import the dictionary database.",
          style: TextStyle(fontSize: 12, color: Colors.white),
        ),
      ),
    );
  }
}
