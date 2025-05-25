import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const CameraApp());
}

class CameraApp extends StatefulWidget {
  const CameraApp({Key? key}) : super(key: key);

  @override
  State<CameraApp> createState() => _CameraAppState();
}

class _CameraAppState extends State<CameraApp> {
  static const platform = MethodChannel('gcam_clone/camera');

  String _lastPhotoPath = '';
  String? _filterConfigXml;

  String _flashMode = 'auto';
  String _cameraFacing = 'back';
  int _timerSec = 0;
  double _zoom = 1.0;

  Future<void> _takePhoto() async {
    if (_filterConfigXml == null) {
      _showMessage("Please import filter config XML first.");
      return;
    }
    try {
      final String result = await platform.invokeMethod('takePhoto', {
        'filterXml': _filterConfigXml,
        'flashMode': _flashMode,
        'cameraFacing': _cameraFacing,
        'timer': _timerSec,
        'zoom': _zoom,
      });
      setState(() {
        _lastPhotoPath = result;
      });
    } on PlatformException catch (e) {
      _showMessage("Failed to take photo: '${e.message}'.");
    }
  }

  Future<void> _importFilterConfig() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['xml'],
    );

    if (result != null && result.files.single.path != null) {
      final file = File(result.files.single.path!);
      final content = await file.readAsString();
      setState(() {
        _filterConfigXml = content;
      });
      _showMessage("Filter config imported successfully!");
    } else {
      _showMessage("No file selected.");
    }
  }

  void _showMessage(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  void _toggleFlash() {
    setState(() {
      if (_flashMode == 'auto') {
        _flashMode = 'on';
      } else if (_flashMode == 'on') {
        _flashMode = 'off';
      } else {
        _flashMode = 'auto';
      }
    });
  }

  void _switchCamera() {
    setState(() {
      _cameraFacing = (_cameraFacing == 'back') ? 'front' : 'back';
    });
  }

  void _changeTimer(int seconds) {
    setState(() {
      _timerSec = seconds;
    });
  }

  void _changeZoom(double zoom) {
    setState(() {
      _zoom = zoom;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark(),
      home: Scaffold(
        body: Stack(
          children: [
            // Native preview from AndroidView
            const SizedBox.expand(
              child: AndroidView(viewType: 'gcam_clone/preview'),
            ),
            // Controls overlay
            Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          icon: Icon(_flashMode == 'auto'
                              ? Icons.flash_auto
                              : _flashMode == 'on'
                                  ? Icons.flash_on
                                  : Icons.flash_off),
                          color: Colors.white,
                          onPressed: _toggleFlash,
                        ),
                        const SizedBox(width: 40),
                        FloatingActionButton(
                          onPressed: _takePhoto,
                          child: const Icon(Icons.camera),
                        ),
                        const SizedBox(width: 40),
                        IconButton(
                          icon: const Icon(Icons.switch_camera),
                          color: Colors.white,
                          onPressed: _switchCamera,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    // Timer selector
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [0, 3, 5, 10].map((sec) {
                        final selected = _timerSec == sec;
                        return Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: ChoiceChip(
                            label: Text('$sec s'),
                            selected: selected,
                            onSelected: (_) => _changeTimer(sec),
                          ),
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 12),
                    // Zoom slider
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 32),
                      child: Slider(
                        min: 1.0,
                        max: 4.0,
                        divisions: 30,
                        value: _zoom,
                        label: '${_zoom.toStringAsFixed(1)}x',
                        onChanged: _changeZoom,
                      ),
                    ),
                    // Import filter config button
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.upload_file),
                        label: const Text("Import Filter Config (XML)"),
                        onPressed: _importFilterConfig,
                      ),
                    ),
                    // Show if filter config is loaded
                    if (_filterConfigXml != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Text(
                          "Filter config loaded",
                          style: const TextStyle(color: Colors.greenAccent),
                        ),
                      ),
                    // Thumbnail preview
                    if (_lastPhotoPath.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Image.file(
                          File(_lastPhotoPath),
                          width: 80,
                          height: 80,
                          fit: BoxFit.cover,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
