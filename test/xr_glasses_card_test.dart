import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/main.dart';

void main() {
  testWidgets('xr card stays hidden when no glasses are detected', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: XrGlassesCardBody(
          state: {'detected': false},
          onProfile: _unusedProfile,
          onRecenter: _unused,
        ),
      ),
    );
    expect(find.text('XR glasses'), findsNothing);
  });

  testWidgets('xr card shows the title for a detected pair', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: XrGlassesCardBody(
            state: {
              'detected': true,
              'id': 'xreal',
              'displayName': 'XREAL',
              'mode': 'world-lock',
              'preset': 'single',
              'usbPermission': 'not_required',
            },
            onProfile: _unusedProfile,
            onRecenter: _unused,
          ),
        ),
      ),
    );
    expect(find.text('XR glasses'), findsOneWidget);
  });
}

void _unused() {}

void _unusedProfile(String mode, String preset) {}
