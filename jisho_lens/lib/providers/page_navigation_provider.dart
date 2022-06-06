import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final activePage = StateProvider<int>((_) => 0);
final pageController = Provider<PageController>((_) => PageController());
