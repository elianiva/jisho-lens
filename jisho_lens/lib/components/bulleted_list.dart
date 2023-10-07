import 'package:flutter/material.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';

class BulletedList extends StatelessWidget {
  const BulletedList({super.key, required this.items});

  final List<String> items;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: items
          .map(
            (e) => Text(
              "\u2022 $e",
              style: context.theme.textTheme.bodySmall?.copyWith(
                height: 1.8,
              ),
            ),
          )
          .toList(),
    );
  }
}
