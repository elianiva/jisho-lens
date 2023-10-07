import 'package:flutter/cupertino.dart';

extension SizedBoxShortcut on num {
  Widget get verticalBox => SizedBox(height: toDouble());

  Widget get horizontalBox => SizedBox(width: toDouble());
}
