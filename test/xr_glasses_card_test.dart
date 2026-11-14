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

  testWidgets('wide hides the advanced scale', (tester) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: XrGlassesCardBody(
      state: _detected(preset: 'wide'),
      onProfile: _unusedProfile,
      onRecenter: _unused,
    ))));
    expect(find.text('Advanced'), findsNothing);
    expect(find.text('Wide'), findsOneWidget);
  });

  testWidgets('body lock and smooth follow hide wide and ultrawide', (tester) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: XrGlassesCardBody(
      state: _detected(mode: 'smooth-follow'),
      onProfile: _unusedProfile,
      onRecenter: _unused,
    ))));
    await _openMenu(tester, find.byType(DropdownButton<String>).at(1));
    expect(find.text('Wide'), findsNothing);
    expect(find.text('Ultrawide'), findsNothing);
    expect(find.text('Single screen'), findsWidgets);
  });

  testWidgets('leaving world lock on wide saves single', (tester) async {
    String? mode;
    String? preset;
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: XrGlassesCardBody(
      state: _detected(preset: 'wide'),
      onProfile: (nextMode, nextPreset, _) {
        mode = nextMode;
        preset = nextPreset;
      },
      onRecenter: _unused,
    ))));
    await _openMenu(tester, find.byType(DropdownButton<String>).first);
    await tester.tap(find.text('Smooth follow').last);
    await tester.pumpAndSettle();
    expect(mode, 'smooth-follow');
    expect(preset, 'single');
  });

  testWidgets('single shows the collapsed advanced scale steps', (tester) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: XrGlassesCardBody(
      state: _detected(),
      onProfile: _unusedProfile,
      onRecenter: _unused,
    ))));
    expect(find.text('Advanced'), findsOneWidget);
    await tester.tap(find.text('Advanced'));
    await tester.pumpAndSettle();
    await _openMenu(tester, find.byType(DropdownButton<double>));
    expect(find.text('1080p'), findsWidgets);
    expect(find.text('1440p'), findsOneWidget);
    expect(find.text('2160p'), findsOneWidget);
  });

  testWidgets('1440p sends the four-thirds scale', (tester) async {
    String? mode;
    String? preset;
    double? scale;
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: XrGlassesCardBody(
      state: _detected(mode: 'body-lock'),
      onProfile: (nextMode, nextPreset, nextScale) {
        mode = nextMode;
        preset = nextPreset;
        scale = nextScale;
      },
      onRecenter: _unused,
    ))));
    await tester.tap(find.text('Advanced'));
    await tester.pumpAndSettle();
    await _openMenu(tester, find.byType(DropdownButton<double>));
    await tester.tap(find.text('1440p').last);
    await tester.pumpAndSettle();
    expect(mode, 'body-lock');
    expect(preset, 'single');
    expect(scale, closeTo(4 / 3, 0.001));
  });
}

void _unused() {}

void _unusedProfile(String mode, String preset, double singleScale) {}

Map<String, dynamic> _detected({
  String mode = 'world-lock',
  String preset = 'single',
  double singleScale = 1,
}) => {
  'detected': true,
  'id': 'xreal',
  'displayName': 'XREAL',
  'mode': mode,
  'preset': preset,
  'singleScale': singleScale,
  'usbPermission': 'not_required',
};

Future<void> _openMenu(WidgetTester tester, Finder button) async {
  await tester.tap(button);
  await tester.pumpAndSettle();
}
