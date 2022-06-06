import 'package:flutter/material.dart';

class WarningBanner extends StatelessWidget {
  const WarningBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Theme.of(context).errorColor,
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
