import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'page_navigation_provider.g.dart';

final activePage = StateProvider<int>((_) => 0);

@riverpod
PageController pageController(PageControllerRef ref) => PageController();
