import 'package:flutter/material.dart';

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
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    height: 1.8,
                  ),
            ),
          )
          .toList(),
    );
  }
}
