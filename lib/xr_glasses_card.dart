part of 'main.dart';

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
  }

  Future<void> save(String mode, String preset) async {
    final id = state['id'] as String? ?? '';
    if (id.isEmpty || busy) return;
    setState(() => busy = true);
    try {
      final next = await widget.bridge.setXrGlassesProfile(
        id: id,
        mode: mode,
        preset: preset,
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
  final void Function(String mode, String preset) onProfile;
  final VoidCallback onRecenter;
  final VoidCallback? onRequestUsb;

  @override
  Widget build(BuildContext context) {
    if (state['detected'] != true) return const SizedBox.shrink();
    final mode = state['mode'] as String? ?? 'body-lock';
    final preset = state['preset'] as String? ?? 'single';
    final name = state['displayName'] as String? ?? '';
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
                value: mode,
                underline: const SizedBox.shrink(),
                onChanged: (value) {
                  if (value != null && value != mode) onProfile(value, preset);
                },
                items: [
                  DropdownMenuItem(value: 'body-lock', child: Text(t('uiXrGlassesBodyLock'))),
                  DropdownMenuItem(value: 'smooth-follow', child: Text(t('uiXrGlassesSmoothFollow'))),
                  DropdownMenuItem(value: 'world-lock', child: Text(t('uiXrGlassesWorldLock'))),
                ],
              ),
            ),
            ListTile(
              title: Text(t('uiXrGlassesGeometry')),
              trailing: DropdownButton<String>(
                value: preset,
                underline: const SizedBox.shrink(),
                onChanged: (value) {
                  if (value != null && value != preset) onProfile(mode, value);
                },
                items: [
                  DropdownMenuItem(value: 'single', child: Text(t('uiXrGlassesPresetSingle'))),
                  DropdownMenuItem(value: 'ultrawide', child: Text(t('uiXrGlassesPresetUltrawide'))),
                  DropdownMenuItem(value: 'triple', child: Text(t('uiXrGlassesPresetTriple'))),
                ],
              ),
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
