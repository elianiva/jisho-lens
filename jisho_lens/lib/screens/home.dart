import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:jisho_lens/providers/db_status_provider.dart';
import 'package:jisho_lens/screens/lens.dart';
import 'package:jisho_lens/providers/ocr_provider.dart';

class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  HomePageState createState() => HomePageState();
}

class HomePageState extends ConsumerState<HomePage> {
  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          height: MediaQuery.of(context).size.width * 0.5,
          child: GridView.count(
            primary: false,
            padding: const EdgeInsets.all(20),
            crossAxisCount: 2,
            crossAxisSpacing: 20,
            mainAxisSpacing: 20,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              TextButton(
                style: ButtonStyle(
                  backgroundColor: MaterialStateProperty.all(
                    Theme.of(context).colorScheme.primary.withOpacity(0.075),
                  ),
                  shape: MaterialStateProperty.all(
                    RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16)),
                  ),
                ),
                onPressed: () => _scanImage(context, ImageSource.gallery),
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      Icon(
                        Icons.image_outlined,
                        color: Theme.of(context).textTheme.labelLarge?.color,
                        size: 32.0,
                        semanticLabel: "Scan from gallery",
                      ),
                      const SizedBox(height: 18),
                      Text(
                        "Scan from gallery",
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                ),
              ),
              TextButton(
                style: ButtonStyle(
                  backgroundColor: MaterialStateProperty.all(
                    Theme.of(context).colorScheme.primary.withOpacity(0.075),
                  ),
                  shape: MaterialStateProperty.all(
                    RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16)),
                  ),
                ),
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      Icon(
                        Icons.camera_outlined,
                        color: Theme.of(context).textTheme.labelLarge?.color,
                        size: 32.0,
                        semanticLabel: "Take a picture",
                      ),
                      const SizedBox(height: 18),
                      Text(
                        "Take a picture",
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                ),
                onPressed: () => _scanImage(context, ImageSource.camera),
              )
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _scanImage(BuildContext context, ImageSource source) async {
    if (ref.read(dbStatus) == DbStatus.empty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
          "You can't use this feature until you've imported the dictionary database.",
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Colors.white,
              ),
        ),
        behavior: SnackBarBehavior.floating,
        backgroundColor: Theme.of(context).errorColor,
      ));
      return;
    }

    final image = await ImagePicker().pickImage(source: source);
    if (image == null) return;

    ref.read(selectedImagePath.state).state = image.path;

    if (mounted) {
      await Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => const LensPage(),
      ));
    }
  }
}
