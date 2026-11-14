part of 'main.dart';

const _scaleSteps = <double>[1, 4 / 3, 2];

double _nearestScale(double value) {
  var best = _scaleSteps.first;
  for (final step in _scaleSteps) {
    if ((step - value).abs() < (best - value).abs()) best = step;
  }
  return best;
}

String _modeKey(String mode) => switch (mode) {
  'smooth-follow' => 'uiXrGlassesSmoothFollow',
  'world-lock' => 'uiXrGlassesWorldLock',
  _ => 'uiXrGlassesBodyLock',
};

class XrGlassesSettingsCard extends StatefulWidget {
  const XrGlassesSettingsCard({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  State<XrGlassesSettingsCard> createState() => _XrGlassesSettingsCardState();
}

class _XrGlassesSettingsCardState extends State<XrGlassesSettingsCard> {
  Map<String, dynamic> state = const {};
  Timer? monitor;
  var busy = false;

  @override
  void initState() {
    super.initState();
    load();
    monitor = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!busy) load();
    });
  }

  @override
  void dispose() {
    monitor?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final next = await widget.bridge.xrGlassesState();
    if (!mounted) return;
    setState(() => state = next);
    final loadedPreset = next['preset'] as String? ?? 'single';
    final loadedMode = next['mode'] as String? ?? 'body-lock';
    if (loadedPreset != 'single' && loadedMode != 'world-lock') {
      final loadedScale = _nearestScale((next['singleScale'] as num?)?.toDouble() ?? 1);
      await save('world-lock', loadedPreset, loadedScale);
    }
  }

  Future<void> save(String mode, String preset, double singleScale) async {
    final id = state['id'] as String? ?? '';
    if (id.isEmpty || busy) return;
    final sendMode = preset != 'single' && mode != 'world-lock' ? 'world-lock' : mode;
    final sendPreset = sendMode == 'world-lock' ? preset : 'single';
    setState(() => busy = true);
    try {
      final next = await widget.bridge.setXrGlassesProfile(
        id: id,
        mode: sendMode,
        preset: sendPreset,
        singleScale: singleScale,
      );
      if (!mounted) return;
      setState(() => state = next);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => XrGlassesCardBody(
    state: state,
    onProfile: save,
    onRecenter: () => widget.bridge.xrGlassesRecenter(),
    onRequestUsb: () => widget.bridge.xrGlassesRequestUsbPermission(),
  );
}

class XrGlassesCardBody extends StatelessWidget {
  const XrGlassesCardBody({
    required this.state,
    required this.onProfile,
    required this.onRecenter,
    this.onRequestUsb,
    super.key,
  });

  final Map<String, dynamic> state;
  final void Function(String mode, String preset, double singleScale) onProfile;
  final VoidCallback onRecenter;
  final VoidCallback? onRequestUsb;

  @override
  Widget build(BuildContext context) {
    if (state['detected'] != true) return const SizedBox.shrink();
    final mode = state['mode'] as String? ?? 'body-lock';
    final preset = state['preset'] as String? ?? 'single';
    final name = state['displayName'] as String? ?? '';
    final scale = _nearestScale((state['singleScale'] as num?)?.toDouble() ?? 1);
    const modes = ['body-lock', 'smooth-follow', 'world-lock'];
    final shownMode = modes.contains(mode) ? mode : 'world-lock';
    final presets = shownMode == 'world-lock'
        ? const ['single', 'wide', 'ultrawide']
        : const ['single'];
    final shownPreset = presets.contains(preset) ? preset : 'single';
    String t(String key) => localText(context, key);
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Card(
        margin: EdgeInsets.zero,
        child: Column(
          children: [
            ListTile(
              leading: const Icon(Icons.view_in_ar_outlined),
              title: Text(t('uiXrGlassesTitle')),
              subtitle: Text(name),
            ),
            ListTile(
              title: Text(t('uiXrGlassesStabilization')),
              trailing: DropdownButton<String>(
                value: shownMode,
                underline: const SizedBox.shrink(),
                onChanged: (value) {
                  if (value == null || value == shownMode) return;
                  final nextPreset = value == 'world-lock' ? shownPreset : 'single';
                  onProfile(value, nextPreset, scale);
                },
                items: [
                  for (final item in modes)
                    DropdownMenuItem(value: item, child: Text(t(_modeKey(item)))),
                ],
              ),
            ),
            ListTile(
              title: Text(t('uiXrGlassesGeometry')),
              trailing: DropdownButton<String>(
                value: shownPreset,
                underline: const SizedBox.shrink(),
                onChanged: (value) {
                  if (value == null || value == shownPreset) return;
                  final nextMode = value == 'single' ? shownMode : 'world-lock';
                  onProfile(nextMode, value, scale);
                },
                items: [
                  for (final item in presets)
                    DropdownMenuItem(
                      value: item,
                      child: Text(t(switch (item) {
                        'wide' => 'uiXrGlassesPresetWide',
                        'ultrawide' => 'uiXrGlassesPresetUltrawide',
                        _ => 'uiXrGlassesPresetSingle',
                      })),
                    ),
                ],
              ),
            ),
            if (shownPreset == 'single')
              ExpansionTile(
                title: Text(t('uiXrGlassesAdvanced')),
                children: [
                  ListTile(
                    title: Text(t('uiXrGlassesScale')),
                    trailing: DropdownButton<double>(
                      value: scale,
                      underline: const SizedBox.shrink(),
                      onChanged: (value) {
                        if (value != null && value != scale) onProfile(mode, preset, value);
                      },
                      items: [
                        DropdownMenuItem(value: 1.0, child: Text(t('uiXrGlassesScale1080'))),
                        DropdownMenuItem(value: 4 / 3, child: Text(t('uiXrGlassesScale1440'))),
                        DropdownMenuItem(value: 2.0, child: Text(t('uiXrGlassesScale2160'))),
                      ],
                    ),
                  ),
                ],
              ),
            if (state['usbPermission'] == 'needed')
              ListTile(
                leading: const Icon(Icons.usb_outlined),
                title: Text(t('uiXrGlassesUsbNeeded')),
                onTap: onRequestUsb,
              ),
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: FilledButton.tonal(
                  onPressed: onRecenter,
                  child: Text(t('uiXrGlassesRecenter')),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
