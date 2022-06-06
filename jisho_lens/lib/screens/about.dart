import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/providers/theme_provider.dart';
import 'package:url_launcher/url_launcher.dart';

class AboutPage extends ConsumerWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentTheme = ref.watch(PreferredThemeNotifier.provider);
    final systemTheme = MediaQuery.of(context).platformBrightness;
    String logoPath = "";

    switch (currentTheme) {
      case PreferredTheme.dark:
        logoPath = "assets/icons/github_light.png";
        break;
      case PreferredTheme.light:
        logoPath = "assets/icons/github_dark.png";
        break;
      case PreferredTheme.system:
      default:
        logoPath = systemTheme == Brightness.dark
            ? "assets/icons/github_light.png"
            : "assets/icons/github_dark.png";
    }

    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 16),
          child: RichText(
            text: TextSpan(
              style: Theme.of(context).textTheme.bodyMedium,
              children: [
                const TextSpan(
                  text: "        Jisho Lense",
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
                const TextSpan(
                  text:
                      " is a Japanese Kanji dictionary app that can scan texts from a picture using ",
                ),
                buildClickableURL(
                  context,
                  "https://developers.google.com/ml-kit",
                  "Google ML Kit",
                ),
                const TextSpan(
                  text: " and then fetch the results from ",
                ),
                buildClickableURL(
                  context,
                  "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project",
                  "JMDict dataset.",
                ),
                const TextSpan(
                  text:
                      " You can also look up the kanji manually from the search page."
                      " At the moment this app only supports Japanese-English translation.",
                ),
              ],
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: RichText(
            text: TextSpan(
              style: Theme.of(context).textTheme.bodyMedium,
              children: const [
                TextSpan(
                  text: "        This app is Open Source and hosted on Github."
                      " Feel free to make an issue if you have any suggestions or difficulties."
                      " Any form of contributions is appreciated!",
                ),
              ],
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            children: <Widget>[
              TextButton(
                onPressed: () => _launchUrl(
                  context,
                  "https://github.com/elianiva/jisho-lens/issues",
                ),
                style: ButtonStyle(
                  backgroundColor: MaterialStateProperty.all(
                    Theme.of(context).colorScheme.primary.withOpacity(0.075),
                  ),
                  padding: MaterialStateProperty.all(
                    const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.bug_report,
                      color: Theme.of(context).colorScheme.onBackground,
                      size: 28,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      "Report an issue",
                      style: TextStyle(
                          color: Theme.of(context).colorScheme.onBackground),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              TextButton(
                onPressed: () => _launchUrl(
                  context,
                  "https://github.com/elianiva/jisho-lens",
                ),
                style: ButtonStyle(
                  backgroundColor: MaterialStateProperty.all(
                    Theme.of(context).colorScheme.primary.withOpacity(0.075),
                  ),
                  padding: MaterialStateProperty.all(
                    const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Image(image: AssetImage(logoPath), height: 28),
                    const SizedBox(width: 8),
                    Text(
                      "See on Github",
                      style: TextStyle(
                          color: Theme.of(context).colorScheme.onBackground),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  TextSpan buildClickableURL(BuildContext context, String url, String text) {
    return TextSpan(
      text: text,
      style: const TextStyle(
        color: Colors.blue,
        decoration: TextDecoration.underline,
        decorationStyle: TextDecorationStyle.dotted,
      ),
      recognizer: TapGestureRecognizer()
        ..onTap = () => _launchUrl(context, url),
    );
  }

  void _launchUrl(BuildContext context, String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else {
      showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text("Error"),
            content: const Text("Failed to open the URL."),
            insetPadding: const EdgeInsets.all(24),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("OK"),
              ),
            ],
          );
        },
      );
    }
  }
}
